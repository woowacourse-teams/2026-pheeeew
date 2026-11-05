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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
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
 * 전송 방식은 {@link PressStreamSendMode} 설정으로 고른다.
 * <ul>
 *   <li>SEQUENTIAL: 이 발행기 스레드에서 구독마다 차례로 보낸다. 수신이 늦은 연결은 같은 주기의 다른 전송을 늦출 수 있다.</li>
 *   <li>ISOLATED: 발행기는 구독별 대기를 최신 화면 하나와 heartbeat 표시 하나로 바꾸기만 하고, 전송은 상한이 있는 전송 실행기에서
 *   구독마다 한 번에 하나씩 한다. 실행기가 가득 차면 대기를 남겨 두고 다음 주기에 다시 넣는다. 한 번의 전송이 제한 시간 이상
 *   걸리면 구독을 닫는다.</li>
 * </ul>
 * 같은 기기의 동시 구독 상한은 이 JVM 안에서만 센다.
 * 열린 구독은 진행 중인 비동기 요청이라 웹 서버의 graceful 종료가 기다린다. 그보다 먼저 멈추도록 기본 phase 의
 * SmartLifecycle 로 두고, 멈출 때 모든 구독을 닫는다. 앱은 다시 구독해 최신 상태를 받는다.
 * SseEmitter 는 전송과 종료가 같은 잠금을 쓴다. 그래서 전송 중인 구독을 닫을 때는 구독 수 반납과 대기 정리만 하고, 연결은
 * 전송 스레드가 IO 에서 돌아온 뒤 닫는다. 막힌 IO 가 언제 끝나는지는 보장하지 않는다.
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
    private final PressStreamSendMode sendMode;
    private final int sendThreads;
    private final int sendQueueCapacity;
    private final Duration sendTimeout;
    private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> deviceSubscriptions = new ConcurrentHashMap<>();
    private final Object generation = new Object();
    private ScheduledExecutorService scheduler;
    private volatile ThreadPoolExecutor sender;
    private volatile Published latest;
    private volatile boolean running;
    private volatile boolean stopped;
    private Instant nextHeartbeatAt = Instant.MIN;

    @Autowired
    public PressStreamPublisher(PressSnapshotService pressSnapshotService, Clock clock,
            @Value("${pheeeew.press-race.stream.publish-interval-millis:1000}") long publishIntervalMillis,
            @Value("${pheeeew.press-race.stream.heartbeat-interval-millis:15000}") long heartbeatIntervalMillis,
            @Value("${pheeeew.press-race.stream.send-mode:ISOLATED}") PressStreamSendMode sendMode,
            @Value("${pheeeew.press-race.stream.send-threads:4}") int sendThreads,
            @Value("${pheeeew.press-race.stream.send-queue-capacity:300}") int sendQueueCapacity,
            @Value("${pheeeew.press-race.stream.send-timeout-millis:5000}") long sendTimeoutMillis) {
        this(pressSnapshotService, clock, Duration.ofMillis(publishIntervalMillis),
                Duration.ofMillis(heartbeatIntervalMillis), sendMode, sendThreads, sendQueueCapacity,
                Duration.ofMillis(sendTimeoutMillis));
    }

    PressStreamPublisher(PressSnapshotService pressSnapshotService, Clock clock, Duration publishInterval,
            Duration heartbeatInterval, PressStreamSendMode sendMode, int sendThreads, int sendQueueCapacity,
            Duration sendTimeout) {
        this.pressSnapshotService = pressSnapshotService;
        this.clock = clock;
        this.publishInterval = publishInterval;
        this.heartbeatInterval = heartbeatInterval;
        this.sendMode = sendMode;
        this.sendThreads = sendThreads;
        this.sendQueueCapacity = sendQueueCapacity;
        this.sendTimeout = sendTimeout;
        if (sendMode == PressStreamSendMode.ISOLATED) {
            this.sender = newSender();
        }
    }

    /** 멈춘 뒤 다시 시작할 수 있도록 시작할 때마다 발행 실행기를 새로 만들고, 멈춘 전송 실행기도 새로 만든다. */
    @Override
    public synchronized void start() {
        long period = publishInterval.toMillis();
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("press-stream-publisher").daemon().factory());
        scheduler.scheduleWithFixedDelay(new DelegatingErrorHandlingRunnable(this::publish,
                new ScheduledTaskErrorHandler()), period, period, TimeUnit.MILLISECONDS);
        if (sendMode == PressStreamSendMode.ISOLATED && sender.isShutdown()) {
            sender = newSender();
        }
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
        if (sender != null) {
            sender.shutdownNow();
        }
        subscriptions.forEach(Subscription::close);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 현재 상태를 먼저 보낸 뒤 구독을 등록한다. 상한을 넘거나 최초 상태를 만들지 못하면 예외를 던지고 등록하지 않는다.
     * 최초 상태는 두 전송 방식 모두 호출한 스레드에서 보낸다. 컨트롤러가 연결을 반환하기 전이라 소켓 쓰기를 기다리지 않는다.
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
            if (!now.isBefore(subscription.expiresAt) || subscription.isSendTimedOut(now)) {
                subscription.close();
                continue;
            }
            PressScreen screen = snapshot == null ? null
                    : snapshot.findScreen(subscription.devicePublicId, subscription.view);
            if (sendMode == PressStreamSendMode.ISOLATED) {
                subscription.offer(screen, heartbeat);
                continue;
            }
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

    private ThreadPoolExecutor newSender() {
        return new ThreadPoolExecutor(sendThreads, sendThreads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(sendQueueCapacity),
                Thread.ofPlatform().name("press-stream-sender-", 0).daemon().factory());
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
        private final Object sequentialSend = new Object();
        private PressScreen lastSent;
        private PressScreen lastQueued;
        private PressScreen pending;
        private boolean pendingHeartbeat;
        private boolean scheduled;
        private Instant sendingSince;

        private Subscription(UUID devicePublicId, PressView view, Instant expiresAt, PressStreamListener listener) {
            this.devicePublicId = devicePublicId;
            this.view = view;
            this.expiresAt = expiresAt;
            this.listener = listener;
        }

        @Override
        public synchronized void cancel() {
            markClosed();
        }

        /** 전송 중이면 연결은 전송 스레드가 IO 에서 돌아온 뒤 닫는다. */
        private void close() {
            boolean sending;
            synchronized (this) {
                if (!markClosed()) {
                    return;
                }
                sending = sendingSince != null;
            }
            if (!sending) {
                listener.close();
            }
        }

        private boolean markClosed() {
            if (!closed.compareAndSet(false, true)) {
                return false;
            }
            pending = null;
            pendingHeartbeat = false;
            subscriptions.remove(this);
            release(devicePublicId);
            return true;
        }

        private boolean isClosed() {
            return closed.get();
        }

        /** 차례 전송과 최초 전송. 전송 중에도 닫기가 기다리지 않도록 대기 상태와 다른 잠금을 쓴다. */
        private void send(PressScreen screen, boolean heartbeat) throws IOException {
            synchronized (sequentialSend) {
                if (closed.get()) {
                    return;
                }
                write(screen, heartbeat);
            }
        }

        private synchronized boolean isSendTimedOut(Instant now) {
            return sendingSince != null && !now.isBefore(sendingSince.plus(sendTimeout));
        }

        /**
         * 대기를 최신 화면 하나와 heartbeat 표시 하나로 바꾸고, 예약된 전송이 없으면 전송 실행기에 넣는다.
         * 마지막으로 넘긴 화면과 같거나 오래된 화면은 넘기지 않는다. 보낼 것이 없는 주기에는 실행기를 쓰지 않는다.
         */
        private synchronized void offer(PressScreen screen, boolean heartbeat) {
            if (closed.get()) {
                return;
            }
            PressScreen reference = lastQueued == null ? lastSent : lastQueued;
            if (isNewer(screen, reference)) {
                pending = screen;
                lastQueued = screen;
            }
            pendingHeartbeat |= heartbeat;
            if (scheduled || (pending == null && !pendingHeartbeat)) {
                return;
            }

            scheduled = true;
            try {
                sender.execute(this::drain);
            } catch (RejectedExecutionException exception) {
                scheduled = false;
            }
        }

        private void drain() {
            while (true) {
                PressScreen screen;
                boolean heartbeat;
                synchronized (this) {
                    if (closed.get() || (pending == null && !pendingHeartbeat)) {
                        scheduled = false;
                        return;
                    }
                    screen = pending;
                    heartbeat = pendingHeartbeat;
                    pending = null;
                    pendingHeartbeat = false;
                    sendingSince = clock.instant();
                }

                boolean failed = false;
                try {
                    write(screen, heartbeat);
                } catch (IOException | RuntimeException exception) {
                    failed = true;
                }

                synchronized (this) {
                    sendingSince = null;
                    if (failed) {
                        markClosed();
                    }
                    if (!closed.get()) {
                        continue;
                    }
                    scheduled = false;
                }
                listener.close();
                return;
            }
        }

        private void write(PressScreen screen, boolean heartbeat) throws IOException {
            if (isNewer(screen, lastSent)) {
                listener.sendSnapshot(screen);
                lastSent = screen;
            }
            if (heartbeat && !closed.get()) {
                listener.sendHeartbeat();
            }
        }

        private boolean isNewer(PressScreen screen, PressScreen reference) {
            if (screen == null) {
                return false;
            }
            boolean older = reference != null && screen.generatedAt().isBefore(reference.generatedAt());
            return !older && !screen.hasSameStateAs(reference);
        }
    }
}
