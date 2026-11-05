package com.pheeeew.pressrace.v2.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.command.EmotionPressService;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.pressrace.application.PressRaceService;
import com.pheeeew.pressrace.v2.domain.PressScreen;
import com.pheeeew.pressrace.v2.domain.PressView;
import com.pheeeew.pressrace.v2.domain.repository.PressSnapshotRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PressStreamIsolatedSendIntegrationTest {

    private static final Duration PUBLISH_INTERVAL = Duration.ofSeconds(1);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration LONG_SEND_TIMEOUT = Duration.ofMinutes(1);

    @Autowired
    private EmotionPressService emotionPressService;

    @Autowired
    private PressRaceService pressRaceService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private JdbcClient jdbc;

    private final List<Device> devices = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private MutableClock clock;
    private PressSnapshotRepository repository;
    private PressStreamPublisher publisher;

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                UPDATE press_race_control SET active_server_generation = :generation, enabled = TRUE,
                  activation_date = (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date
                """).param("generation", pressRaceService.serverGeneration()).update();
        clock = new MutableClock(Instant.now());
        repository = spy(new PressSnapshotRepository(jdbc));
    }

    @AfterEach
    void tearDown() {
        listeners.forEach(Listener::release);
        if (publisher != null) {
            publisher.stop();
        }
        for (Device device : devices) {
            jdbc.sql("DELETE FROM device_daily_presses WHERE device_id = :device")
                    .param("device", device.getId()).update();
            deviceRepository.deleteById(device.getId());
        }
        jdbc.sql("UPDATE press_race_control SET active_server_generation = NULL, enabled = FALSE, activation_date = NULL")
                .update();
    }

    @Test
    void 막힌_구독이_있어도_정상_구독은_최신_snapshot_과_heartbeat_를_받고_발행기는_주기마다_공통_순위를_만든다() throws Exception {
        // given: heartbeat 간격만큼 시계를 옮겨도 막힌 구독이 닫히지 않도록 전송 제한 시간을 길게 둔다.
        발행기(4, 300, LONG_SEND_TIMEOUT);
        Device slow = 기기();
        Device normal = 기기();
        Listener slowListener = 구독한다(slow, PressView.PRESS);
        Listener normalListener = 구독한다(normal, PressView.PRESS);
        slowListener.block(new CountDownLatch(1));
        누른다(normal, 1);

        // when
        발행한다();
        assertThat(slowListener.entered.await(5, TimeUnit.SECONDS)).isTrue();
        누른다(normal, 1);
        clock.advance(HEARTBEAT_INTERVAL);
        발행한다();

        // then
        기다린다(() -> normalListener.heartbeats.get() == 2 && normalListener.snapshots.size() == 3);
        assertThat(normalListener.snapshots).extracting(screen -> screen.me().score()).containsExactly(0L, 1L, 2L);
        assertThat(slowListener.snapshots).hasSize(1);
        assertThat(publisher.subscriptionCount(slow.getPublicId())).isEqualTo(1);
        verify(repository, times(3)).findParticipants(any());
    }

    @Test
    void 막힌_전송이_풀리면_중간_상태가_아니라_최신_상태와_heartbeat_하나를_받고_전송은_하나씩_진행된다() throws Exception {
        // given: heartbeat 간격만큼 시계를 옮겨도 막힌 구독이 닫히지 않도록 전송 제한 시간을 길게 둔다.
        발행기(4, 300, LONG_SEND_TIMEOUT);
        Device device = 기기();
        Listener listener = 구독한다(device, PressView.PRESS);
        CountDownLatch gate = new CountDownLatch(1);
        listener.block(gate);
        누른다(device, 1);
        발행한다();
        assertThat(listener.entered.await(5, TimeUnit.SECONDS)).isTrue();

        // when: 전송이 막힌 동안 두 주기 모두 heartbeat 와 새 상태를 넘긴다.
        누른다(device, 1);
        clock.advance(HEARTBEAT_INTERVAL);
        발행한다();
        누른다(device, 1);
        clock.advance(HEARTBEAT_INTERVAL);
        발행한다();
        gate.countDown();

        // then
        기다린다(() -> listener.snapshots.size() == 3 && listener.heartbeats.get() == 2);
        assertThat(listener.snapshots).extracting(screen -> screen.me().score()).containsExactly(0L, 1L, 3L);
        assertThat(listener.maxSending.get()).isEqualTo(1);
        assertThat(listener.closed.get()).isZero();
    }

    @Test
    void 한_번의_전송이_제한_시간_이상_걸리면_구독을_닫고_반납하며_연결은_전송이_끝난_뒤_닫는다() throws Exception {
        // given
        발행기(1, 1);
        Device device = 기기();
        Listener listener = 구독한다(device, PressView.PRESS);
        CountDownLatch gate = new CountDownLatch(1);
        listener.block(gate);
        누른다(device, 1);
        발행한다();
        assertThat(listener.entered.await(5, TimeUnit.SECONDS)).isTrue();

        // when
        clock.advance(SEND_TIMEOUT.minusMillis(1));
        발행한다();
        int beforeTimeout = publisher.subscriptionCount(device.getPublicId());
        clock.advance(Duration.ofMillis(1));
        발행한다();
        int afterTimeout = publisher.subscriptionCount(device.getPublicId());
        int closedWhileSending = listener.closed.get();
        누른다(device, 1);
        발행한다();
        gate.countDown();

        // then
        기다린다(() -> listener.closed.get() == 1);
        assertThat(beforeTimeout).isEqualTo(1);
        assertThat(afterTimeout).isZero();
        assertThat(closedWhileSending).isZero();
        assertThat(listener.snapshots).extracting(screen -> screen.me().score()).containsExactly(0L, 1L);
        assertThat(listener.heartbeats.get()).isZero();
    }

    @Test
    void 전송에_실패한_구독은_닫고_반납하며_더_보내지_않는다() {
        // given
        발행기(1, 1);
        Device device = 기기();
        Listener listener = 구독한다(device, PressView.PRESS);
        listener.failing = true;
        누른다(device, 1);

        // when
        발행한다();
        기다린다(() -> listener.closed.get() == 1);
        listener.failing = false;
        누른다(device, 1);
        발행한다();

        // then
        assertThat(publisher.subscriptionCount(device.getPublicId())).isZero();
        assertThat(listener.snapshots).hasSize(1);
        assertThat(listener.heartbeats.get()).isZero();
        assertThat(listener.closed.get()).isEqualTo(1);
    }

    @Test
    void 전송_중에_연결이_끊기면_대기를_버리고_반납하며_더_보내지_않는다() throws Exception {
        // given
        발행기(1, 1);
        Device device = 기기();
        Listener listener = new Listener();
        listeners.add(listener);
        PressStreamSubscription subscription = publisher.subscribe(device.getPublicId(), PressView.PRESS,
                clock.instant().plus(Duration.ofMinutes(30)), listener);
        CountDownLatch gate = new CountDownLatch(1);
        listener.block(gate);
        누른다(device, 1);
        발행한다();
        assertThat(listener.entered.await(5, TimeUnit.SECONDS)).isTrue();
        누른다(device, 1);
        발행한다();

        // when
        subscription.cancel();
        gate.countDown();

        // then
        기다린다(() -> listener.closed.get() == 1);
        assertThat(publisher.subscriptionCount(device.getPublicId())).isZero();
        assertThat(listener.snapshots).extracting(screen -> screen.me().score()).containsExactly(0L, 1L);
    }

    @Test
    void 전송_중에_토큰이_만료되면_대기를_버리고_반납하며_더_보내지_않는다() throws Exception {
        // given
        발행기(1, 1);
        Device device = 기기();
        Listener listener = new Listener();
        listeners.add(listener);
        publisher.subscribe(device.getPublicId(), PressView.PRESS, clock.instant().plusSeconds(3), listener);
        CountDownLatch gate = new CountDownLatch(1);
        listener.block(gate);
        누른다(device, 1);
        발행한다();
        assertThat(listener.entered.await(5, TimeUnit.SECONDS)).isTrue();

        // when
        누른다(device, 1);
        clock.advance(Duration.ofSeconds(3));
        발행한다();
        int afterExpiry = publisher.subscriptionCount(device.getPublicId());
        gate.countDown();

        // then
        기다린다(() -> listener.closed.get() == 1);
        assertThat(afterExpiry).isZero();
        assertThat(listener.snapshots).extracting(screen -> screen.me().score()).containsExactly(0L, 1L);
    }

    @Test
    void 전송_실행기가_가득_차면_구독을_유지하고_이후_주기에_최신_상태를_보낸다() throws Exception {
        // given: 스레드 1개가 첫 구독을 보내는 동안 대기열 1칸이 둘째 구독으로 차서 셋째 구독은 거절된다.
        발행기(1, 1);
        Device other = 기기();
        List<Listener> subscribers = List.of(구독한다(기기(), PressView.ALL), 구독한다(기기(), PressView.ALL),
                구독한다(기기(), PressView.ALL));
        CountDownLatch gate = new CountDownLatch(1);
        subscribers.forEach(listener -> listener.block(gate));
        누른다(other, 1);
        발행한다();
        기다린다(() -> subscribers.stream().anyMatch(listener -> listener.entered.getCount() == 0));
        누른다(other, 1);
        발행한다();

        // when: 첫 구독은 보낸 뒤 남은 대기를 다시 넣지만 대기열이 둘째 구독으로 차 있어 거절된다.
        gate.countDown();
        기다린다(() -> 보낸_수(subscribers) == 5);
        List<List<Long>> beforeRetry = 보낸_점수(subscribers);
        발행하며_기다린다(() -> 보낸_수(subscribers) == 7);

        // then
        assertThat(beforeRetry).containsExactlyInAnyOrder(List.of(1L), List.of(2L), List.of());
        assertThat(보낸_점수(subscribers)).containsExactlyInAnyOrder(List.of(1L, 2L), List.of(2L), List.of(2L));
        assertThat(subscribers).allSatisfy(listener -> assertThat(listener.closed.get()).isZero());
    }

    @Test
    void 전송이_오래_걸리는_구독도_한_번_보낸_뒤_스레드를_돌려줘_다른_구독이_차례를_얻는다() throws Exception {
        // given: 전송 스레드 1개를 매 전송이 막히는 구독이 쓰는 동안 다른 구독의 전송이 대기열에 있다.
        발행기(1, 300, LONG_SEND_TIMEOUT);
        Device other = 기기();
        Listener slow = 구독한다(기기(), PressView.ALL);
        Semaphore permits = new Semaphore(0);
        slow.blockEachSend(permits);
        누른다(other, 1);
        발행한다();
        assertThat(slow.entries.tryAcquire(5, TimeUnit.SECONDS)).isTrue();
        Listener normal = 구독한다(기기(), PressView.ALL);
        누른다(other, 1);
        발행한다();

        // when: 느린 구독의 첫 전송만 끝낸다. 느린 구독의 대기에는 다음 상태가 남아 있다.
        permits.release();

        // then
        기다린다(() -> normal.snapshots.size() == 2);
        assertThat(slow.entries.tryAcquire(5, TimeUnit.SECONDS)).isTrue();
        assertThat(slow.snapshots).hasSize(2);
    }

    @Test
    void 멈출_때_막힌_전송을_기다리지_않고_모두_반납하며_막힌_연결은_전송이_끝난_뒤_닫고_다시_시작하면_보낸다() throws Exception {
        // given
        발행기(1, 1);
        Device blocked = 기기();
        Device idle = 기기();
        Listener blockedListener = 구독한다(blocked, PressView.PRESS);
        CountDownLatch gate = new CountDownLatch(1);
        blockedListener.block(gate);
        누른다(blocked, 1);
        발행한다();
        assertThat(blockedListener.entered.await(5, TimeUnit.SECONDS)).isTrue();
        Listener idleListener = 구독한다(idle, PressView.PRESS);

        // when
        CompletableFuture.runAsync(publisher::stop).get(5, TimeUnit.SECONDS);

        // then
        assertThat(blockedListener.closed.get()).isZero();
        assertThat(idleListener.closed.get()).isEqualTo(1);
        assertThat(publisher.subscriptionCount(blocked.getPublicId())).isZero();
        assertThat(publisher.subscriptionCount(idle.getPublicId())).isZero();
        assertThat(publisher.isRunning()).isFalse();
        gate.countDown();
        기다린다(() -> blockedListener.closed.get() == 1);

        publisher.start();
        Listener restarted = 구독한다(idle, PressView.PRESS);
        누른다(idle, 1);
        발행한다();
        기다린다(() -> restarted.snapshots.size() == 2);
    }

    @Test
    void 기기마다_자기_화면을_받고_heartbeat_는_간격마다_보낸다() {
        // given
        발행기(4, 300);
        Device leader = 기기();
        Device follower = 기기();
        Listener leaderListener = 구독한다(leader, PressView.PRESS);
        Listener followerListener = 구독한다(follower, PressView.ALL);
        누른다(leader, 5);
        누른다(follower, 3);

        // when
        발행한다();
        clock.advance(HEARTBEAT_INTERVAL.minusMillis(1));
        발행한다();
        기다린다(() -> leaderListener.snapshots.size() == 2 && followerListener.snapshots.size() == 2
                && leaderListener.heartbeats.get() == 1);
        int beforeInterval = leaderListener.heartbeats.get();
        clock.advance(Duration.ofMillis(1));
        발행한다();

        // then
        기다린다(() -> leaderListener.heartbeats.get() == 2);
        assertThat(beforeInterval).isEqualTo(1);
        assertThat(leaderListener.snapshots.getLast().me().rank()).isEqualTo(1);
        assertThat(leaderListener.snapshots.getLast().top100()).isEmpty();
        assertThat(followerListener.snapshots.getLast().me().rank()).isEqualTo(2);
        assertThat(followerListener.snapshots.getLast().top100()).hasSize(2);
    }

    private void 발행기(int sendThreads, int sendQueueCapacity) {
        발행기(sendThreads, sendQueueCapacity, SEND_TIMEOUT);
    }

    private void 발행기(int sendThreads, int sendQueueCapacity, Duration sendTimeout) {
        publisher = new PressStreamPublisher(new PressSnapshotService(repository), clock, PUBLISH_INTERVAL,
                HEARTBEAT_INTERVAL, PressStreamSendMode.ISOLATED, sendThreads, sendQueueCapacity, sendTimeout);
    }

    /** 발행기가 전송을 기다리지 않는지 확인하려고 제한 시간 안에 끝나야 한다. */
    private void 발행한다() {
        try {
            CompletableFuture.runAsync(publisher::publish).get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("발행 주기가 전송을 기다렸습니다.", exception);
        }
    }

    /** 대기열 1칸을 두고 여러 구독이 다시 넣기를 다툴 수 있어, 조건이 맞을 때까지 발행 주기를 반복한다. */
    private void 발행하며_기다린다(BooleanSupplier condition) throws InterruptedException {
        for (int cycle = 0; cycle < 20 && !condition.getAsBoolean(); cycle++) {
            발행한다();
            Thread.sleep(50);
        }
        assertThat(condition.getAsBoolean()).as("발행 주기를 반복해도 조건이 맞지 않았습니다.").isTrue();
    }

    private Listener 구독한다(Device device, PressView view) {
        Listener listener = new Listener();
        listeners.add(listener);
        publisher.subscribe(device.getPublicId(), view, clock.instant().plus(Duration.ofMinutes(30)), listener);
        return listener;
    }

    private void 누른다(Device device, int count) {
        emotionPressService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, count));
    }

    private Device 기기() {
        Device device = deviceRepository.save(기본_기기_빌더().build());
        devices.add(device);
        return device;
    }

    private int 보낸_수(List<Listener> subscribers) {
        return subscribers.stream().mapToInt(listener -> listener.snapshots.size()).sum();
    }

    /** 최초 snapshot 을 뺀 뒤 받은 상태들의 다른 기기 점수. */
    private List<List<Long>> 보낸_점수(List<Listener> subscribers) {
        return subscribers.stream()
                .map(listener -> listener.snapshots.stream().skip(1)
                        .map(screen -> screen.top100().getFirst().score())
                        .toList())
                .toList();
    }

    private void 기다린다(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("조건을 기다리는 시간이 지났습니다.").isLessThan(deadline);
            Thread.onSpinWait();
        }
    }

    private static final class Listener implements PressStreamListener {

        private final List<PressScreen> snapshots = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger heartbeats = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();
        private final AtomicInteger sending = new AtomicInteger();
        private final AtomicInteger maxSending = new AtomicInteger();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final ReentrantLock writeLock = new ReentrantLock();
        private final Semaphore entries = new Semaphore(0);
        private volatile Semaphore permits;
        private volatile CountDownLatch gate;
        private volatile boolean failing;

        @Override
        public void sendSnapshot(PressScreen screen) throws IOException {
            writeLock.lock();
            try {
                enter();
                try {
                    waitGate();
                    waitPermit();
                    snapshots.add(screen);
                } finally {
                    sending.decrementAndGet();
                }
            } finally {
                writeLock.unlock();
            }
        }

        @Override
        public void sendHeartbeat() throws IOException {
            writeLock.lock();
            try {
                enter();
                try {
                    heartbeats.incrementAndGet();
                } finally {
                    sending.decrementAndGet();
                }
            } finally {
                writeLock.unlock();
            }
        }

        /** SseEmitter 처럼 전송과 같은 잠금을 쓴다. 전송이 막혀 있으면 닫기도 기다린다. */
        @Override
        public void close() {
            writeLock.lock();
            try {
                closed.incrementAndGet();
            } finally {
                writeLock.unlock();
            }
        }

        private void block(CountDownLatch gate) {
            this.gate = gate;
        }

        /** 화면 전송마다 허가 하나를 기다린다. 전송을 시작할 때마다 entries 를 하나 올린다. */
        private void blockEachSend(Semaphore permits) {
            this.permits = permits;
        }

        private void release() {
            CountDownLatch current = gate;
            if (current != null) {
                current.countDown();
            }
            Semaphore currentPermits = permits;
            if (currentPermits != null) {
                currentPermits.release(Integer.MAX_VALUE / 2);
            }
        }

        private void enter() throws IOException {
            maxSending.accumulateAndGet(sending.incrementAndGet(), Math::max);
            if (failing) {
                sending.decrementAndGet();
                throw new IOException("연결이 끊겼습니다.");
            }
        }

        /** 소켓 쓰기가 막힌 상황을 대신한다. 막힌 소켓 쓰기처럼 인터럽트로는 풀리지 않는다. */
        private void waitGate() throws IOException {
            CountDownLatch current = gate;
            if (current == null) {
                return;
            }
            entered.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean interrupted = false;
            try {
                while (current.getCount() > 0) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        throw new IOException("전송이 풀리지 않았습니다.");
                    }
                    try {
                        current.await(remaining, TimeUnit.NANOSECONDS);
                    } catch (InterruptedException exception) {
                        interrupted = true;
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void waitPermit() throws IOException {
            Semaphore current = permits;
            if (current == null) {
                return;
            }
            entries.release();
            try {
                if (!current.tryAcquire(10, TimeUnit.SECONDS)) {
                    throw new IOException("전송이 풀리지 않았습니다.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("전송이 중단되었습니다.", exception);
            }
        }
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> now;

        private MutableClock(Instant now) {
            this.now = new AtomicReference<>(now);
        }

        private void advance(Duration duration) {
            now.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
