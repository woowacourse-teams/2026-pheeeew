package com.pheeeew.pressrace.application;

import com.pheeeew.common.logging.ScheduledTaskErrorHandler;
import com.pheeeew.pressrace.domain.PressRanking;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import com.pheeeew.pressrace.domain.PressView;
import com.pheeeew.pressrace.exception.PressRaceException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.support.DelegatingErrorHandlingRunnable;
import org.springframework.stereotype.Component;

/** 조회 시작부터 대기 게시까지 단일 흐름. 송신·DB 쓰기는 이 잠금에서 실행하지 않는다. */
@RequiredArgsConstructor
@Component
public class PressRankingPublisher {

    private final PressRankingReader reader;
    private final Map<String, Subscription> subscriptions = new HashMap<>();
    private long version;
    private final ScheduledExecutorService updates = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("press-ranking-updates").daemon().factory());

    @PostConstruct
    void startUpdates() {
        // 정리 작업이 사용하는 기본 Spring 스케줄러와 분리한다. 예외 후에도 다음 전체 조회를 계속한다.
        updates.scheduleWithFixedDelay(new DelegatingErrorHandlingRunnable(this::refresh,
                new ScheduledTaskErrorHandler()), 0, 250, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stopUpdates() {
        updates.shutdownNow();
    }

    public synchronized void subscribe(String sessionId, UUID deviceId, PressView view,
            Consumer<Snapshot> snapshots, Consumer<String> stopped) {
        subscriptions.put(sessionId, new Subscription(deviceId, view, snapshots, stopped));
        // session.ready 이전 캐시를 재사용하지 않고 다음 전체 조회에서 첫 기준 상태를 제공한다.
    }

    public synchronized boolean changeView(String sessionId, PressView view, Runnable invalidatePending) {
        Subscription subscription = subscriptions.get(sessionId);
        if (subscription == null) {
            return false;
        }
        invalidatePending.run();
        subscription.view = view;
        subscription.baseline = true;
        return true;
    }

    public synchronized void unsubscribe(String sessionId) {
        subscriptions.remove(sessionId);
    }

    public synchronized void refresh() {
        if (subscriptions.isEmpty()) {
            return;
        }
        PressRanking ranking;
        try {
            ranking = reader.read();
        } catch (PressRaceException exception) {
            subscriptions.values().forEach(subscription -> subscription.stopped.accept(exception.code()));
            subscriptions.clear();
            return;
        }
        long nextVersion = Math.incrementExact(version);
        version = nextVersion;
        for (Subscription subscription : subscriptions.values()) {
            PressRanking.Screen screen = ranking.forDevice(subscription.deviceId, subscription.view);
            boolean baseline = subscription.baseline || subscription.date == null || !subscription.date.equals(screen.date());
            subscription.snapshots.accept(Snapshot.of(screen, Long.toString(nextVersion), ranking.generatedAt(), baseline));
            subscription.baseline = false;
            subscription.date = screen.date();
        }
    }

    public record Snapshot(PressRanking.Screen screen, String snapshotVersion, Instant generatedAt, boolean baseline) {
        public static Snapshot of(PressRanking.Screen screen, String snapshotVersion, Instant generatedAt, boolean baseline) {
            return new Snapshot(screen, snapshotVersion, generatedAt, baseline);
        }
    }

    private static final class Subscription {
        private final UUID deviceId;
        private final Consumer<Snapshot> snapshots;
        private final Consumer<String> stopped;
        private PressView view;
        private boolean baseline = true;
        private java.time.LocalDate date;

        private Subscription(UUID deviceId, PressView view, Consumer<Snapshot> snapshots, Consumer<String> stopped) {
            this.deviceId = deviceId;
            this.view = view;
            this.snapshots = snapshots;
            this.stopped = stopped;
        }
    }
}
