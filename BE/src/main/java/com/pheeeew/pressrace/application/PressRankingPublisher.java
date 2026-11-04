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

/** 조회·게시는 직렬화하고, 구독 상태 잠금은 DB 조회 동안 유지하지 않는다. 송신은 잠금 밖이다. */
@RequiredArgsConstructor
@Component
public class PressRankingPublisher {

    private final PressRankingReader reader;
    private final Map<String, Subscription> subscriptions = new HashMap<>();
    private final Object refreshLock = new Object();
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
        subscriptions.put(sessionId, new Subscription(subscription.deviceId, view,
                subscription.snapshots, subscription.stopped));
        return true;
    }

    public synchronized void unsubscribe(String sessionId) {
        subscriptions.remove(sessionId);
    }

    public void refresh() {
        synchronized (refreshLock) {
            Map<String, Subscription> observed;
            synchronized (this) {
                if (subscriptions.isEmpty()) {
                    return;
                }
                observed = Map.copyOf(subscriptions);
            }
            PressRanking ranking;
            try {
                ranking = reader.read();
            } catch (PressRaceException exception) {
                synchronized (this) {
                    observed.forEach((sessionId, subscription) -> {
                        if (subscriptions.remove(sessionId, subscription)) {
                            subscription.stopped.accept(exception.code());
                        }
                    });
                }
                return;
            }
            synchronized (this) {
                long nextVersion = Math.incrementExact(version);
                version = nextVersion;
                observed.forEach((sessionId, subscription) -> {
                    // 진입·재초기화·화면 전환 이전에 시작한 관찰은 새 기준으로 재사용하지 않는다.
                    if (subscriptions.get(sessionId) != subscription) {
                        return;
                    }
                    PressRanking.Screen screen = ranking.forDevice(subscription.deviceId, subscription.view);
                    boolean baseline = subscription.baseline || subscription.date == null || !subscription.date.equals(screen.date());
                    subscription.snapshots.accept(Snapshot.of(ranking, subscription.deviceId, subscription.view,
                            Long.toString(nextVersion), baseline));
                    subscription.baseline = false;
                    subscription.date = screen.date();
                });
            }
        }
    }

    /** 전체 순위는 공유 참조이며 JSON으로 직렬화하지 않는다. 화면과 추월 숫자만 송신한다. */
    public record Snapshot(PressRanking ranking, UUID deviceId, PressView view, String snapshotVersion,
                           boolean baseline, PressRanking.Overtakes overtakes) {
        public static Snapshot of(PressRanking ranking, UUID deviceId, PressView view, String snapshotVersion, boolean baseline) {
            return of(ranking, deviceId, view, snapshotVersion, baseline, PressRanking.Overtakes.of(0, 0));
        }

        public PressRanking.Screen screen() {
            return ranking.forDevice(deviceId, view);
        }

        public Instant generatedAt() {
            return ranking.generatedAt();
        }

        public Snapshot forDelivery(Snapshot previous, boolean reset) {
            boolean newBaseline = reset || previous == null || !deviceId.equals(previous.deviceId)
                    || view != previous.view || !screen().date().equals(previous.screen().date());
            PressRanking.Overtakes changes = newBaseline ? PressRanking.Overtakes.of(0, 0)
                    : ranking.overtakesSince(previous.ranking, deviceId);
            return of(ranking, deviceId, view, snapshotVersion, newBaseline, changes);
        }

        private static Snapshot of(PressRanking ranking, UUID deviceId, PressView view, String snapshotVersion,
                boolean baseline, PressRanking.Overtakes overtakes) {
            return new Snapshot(ranking, deviceId, view, snapshotVersion, baseline, overtakes);
        }
    }

    private static final class Subscription {
        private final UUID deviceId;
        private final Consumer<Snapshot> snapshots;
        private final Consumer<String> stopped;
        private final PressView view;
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
