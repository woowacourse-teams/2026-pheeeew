package com.pheeeew.pressrace.v2.application;

import static com.pheeeew.pressrace.v2.exception.PressStreamErrorCode.PRESS_STREAM_SUBSCRIPTION_LIMIT_EXCEEDED;

import com.pheeeew.common.logging.ScheduledTaskErrorHandler;
import com.pheeeew.pressrace.v2.domain.PressScreen;
import com.pheeeew.pressrace.v2.domain.PressSnapshot;
import com.pheeeew.pressrace.v2.domain.PressView;
import com.pheeeew.pressrace.v2.exception.PressStreamException;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.support.DelegatingErrorHandlingRunnable;
import org.springframework.stereotype.Service;

/**
 * 구독자가 있는 동안 주기마다 DB 에서 공통 순위를 한 번 만들고, 구독마다 화면을 계산해 바뀐 경우만 보낸다.
 * 전송은 이 발행기 스레드에서 구독마다 차례로 한다. 수신이 늦은 연결은 같은 주기의 다른 전송을 늦출 수 있다.
 * 같은 기기의 동시 구독 상한은 이 JVM 안에서만 센다.
 * 열린 구독은 진행 중인 비동기 요청이라 웹 서버의 graceful 종료가 기다린다. 그보다 먼저 멈추도록 기본 phase 의
 * SmartLifecycle 로 두고, 멈출 때 모든 구독을 닫는다. 앱은 다시 구독해 최신 상태를 받는다.
 */
@Slf4j
@Service
public class PressStreamPublisher implements SmartLifecycle {

    static final int MAX_SUBSCRIPTIONS_PER_DEVICE = 2;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PressSnapshotService pressSnapshotService;
    private final Clock clock;
    private final Duration publishInterval;
    private final Duration heartbeatInterval;
    private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> deviceSubscriptions = new ConcurrentHashMap<>();
    private final Object generation = new Object();
    private ScheduledExecutorService scheduler;
    private volatile Published latest;
    private volatile boolean running;
    private volatile boolean stopped;
    private Instant nextHeartbeatAt = Instant.MIN;

    @Autowired
    public PressStreamPublisher(PressSnapshotService pressSnapshotService, Clock clock,
            @Value("${pheeeew.press-race.stream.publish-interval-millis:1000}") long publishIntervalMillis,
            @Value("${pheeeew.press-race.stream.heartbeat-interval-millis:15000}") long heartbeatIntervalMillis) {
        this(pressSnapshotService, clock, Duration.ofMillis(publishIntervalMillis),
                Duration.ofMillis(heartbeatIntervalMillis));
    }

    PressStreamPublisher(PressSnapshotService pressSnapshotService, Clock clock, Duration publishInterval,
            Duration heartbeatInterval) {
        this.pressSnapshotService = pressSnapshotService;
        this.clock = clock;
        this.publishInterval = publishInterval;
        this.heartbeatInterval = heartbeatInterval;
    }

    /** 멈춘 뒤 다시 시작할 수 있도록 시작할 때마다 실행기를 새로 만든다. */
    @Override
    public synchronized void start() {
        long period = publishInterval.toMillis();
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("press-stream-publisher").daemon().factory());
        scheduler.scheduleWithFixedDelay(new DelegatingErrorHandlingRunnable(this::publish,
                new ScheduledTaskErrorHandler()), period, period, TimeUnit.MILLISECONDS);
        stopped = false;
        running = true;
    }

    @Override
    public synchronized void stop() {
        stopped = true;
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        subscriptions.forEach(Subscription::close);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 현재 상태를 먼저 보낸 뒤 구독을 등록한다. 상한을 넘거나 최초 상태를 만들지 못하면 예외를 던지고 등록하지 않는다.
     */
    public PressStreamSubscription subscribe(UUID devicePublicId, PressView view, Instant expiresAt,
            PressStreamListener listener) {
        acquire(devicePublicId);
        Subscription subscription = new Subscription(devicePublicId, view, expiresAt, listener);
        PressScreen screen;
        try {
            screen = findInitialSnapshot().findScreen(devicePublicId, view);
        } catch (RuntimeException exception) {
            subscription.cancel();
            throw exception;
        }

        try {
            subscription.send(screen, false);
        } catch (IOException | RuntimeException exception) {
            subscription.close();
            return subscription;
        }

        subscriptions.add(subscription);
        if (subscription.isClosed()) {
            subscriptions.remove(subscription);
        }
        if (stopped) {
            subscription.close();
        }
        return subscription;
    }

    void publish() {
        if (subscriptions.isEmpty()) {
            return;
        }

        Instant now = clock.instant();
        PressSnapshot snapshot = generateOrNull();
        boolean heartbeat = !now.isBefore(nextHeartbeatAt);
        if (heartbeat) {
            nextHeartbeatAt = now.plus(heartbeatInterval);
        }

        for (Subscription subscription : subscriptions) {
            if (!now.isBefore(subscription.expiresAt)) {
                subscription.close();
                continue;
            }
            PressScreen screen = snapshot == null ? null
                    : snapshot.findScreen(subscription.devicePublicId, subscription.view);
            try {
                subscription.send(screen, heartbeat);
            } catch (IOException | RuntimeException exception) {
                subscription.close();
            }
        }
    }

    int subscriptionCount(UUID devicePublicId) {
        return deviceSubscriptions.getOrDefault(devicePublicId, 0);
    }

    private void acquire(UUID devicePublicId) {
        boolean[] rejected = {false};
        deviceSubscriptions.compute(devicePublicId, (device, count) -> {
            int current = count == null ? 0 : count;
            if (current >= MAX_SUBSCRIPTIONS_PER_DEVICE) {
                rejected[0] = true;
                return count;
            }
            return current + 1;
        });
        if (rejected[0]) {
            throw new PressStreamException(PRESS_STREAM_SUBSCRIPTION_LIMIT_EXCEEDED);
        }
    }

    private void release(UUID devicePublicId) {
        deviceSubscriptions.computeIfPresent(devicePublicId, (device, count) -> count <= 1 ? null : count - 1);
    }

    private PressSnapshot findInitialSnapshot() {
        Published current = latest;
        if (isFresh(current)) {
            return current.snapshot;
        }

        synchronized (generation) {
            current = latest;
            if (isFresh(current)) {
                return current.snapshot;
            }
            return generate();
        }
    }

    private boolean isFresh(Published published) {
        if (published == null) {
            return false;
        }
        Instant now = clock.instant();
        return !now.isAfter(published.startedAt.plus(publishInterval))
                && published.snapshot.date().equals(LocalDate.ofInstant(now, KST));
    }

    private PressSnapshot generateOrNull() {
        try {
            synchronized (generation) {
                return generate();
            }
        } catch (RuntimeException exception) {
            log.warn("실시간 프레스 공통 순위를 만들지 못해 이번 주기를 건너뜁니다.", exception);
            return null;
        }
    }

    private PressSnapshot generate() {
        Instant startedAt = clock.instant();
        PressSnapshot snapshot = pressSnapshotService.findTodaySnapshot();
        latest = new Published(snapshot, startedAt);
        return snapshot;
    }

    private record Published(PressSnapshot snapshot, Instant startedAt) {
    }

    private final class Subscription implements PressStreamSubscription {

        private final UUID devicePublicId;
        private final PressView view;
        private final Instant expiresAt;
        private final PressStreamListener listener;
        private final AtomicBoolean closed = new AtomicBoolean();
        private PressScreen lastSent;

        private Subscription(UUID devicePublicId, PressView view, Instant expiresAt, PressStreamListener listener) {
            this.devicePublicId = devicePublicId;
            this.view = view;
            this.expiresAt = expiresAt;
            this.listener = listener;
        }

        @Override
        public void cancel() {
            markClosed();
        }

        private void close() {
            if (markClosed()) {
                listener.close();
            }
        }

        private boolean markClosed() {
            if (!closed.compareAndSet(false, true)) {
                return false;
            }
            subscriptions.remove(this);
            release(devicePublicId);
            return true;
        }

        private boolean isClosed() {
            return closed.get();
        }

        private synchronized void send(PressScreen screen, boolean heartbeat) throws IOException {
            if (closed.get()) {
                return;
            }
            boolean older = screen != null && lastSent != null && screen.generatedAt().isBefore(lastSent.generatedAt());
            if (screen != null && !older && !screen.hasSameStateAs(lastSent)) {
                listener.sendSnapshot(screen);
                lastSent = screen;
            }
            if (heartbeat) {
                listener.sendHeartbeat();
            }
        }
    }
}
