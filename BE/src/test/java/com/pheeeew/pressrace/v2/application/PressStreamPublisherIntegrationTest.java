package com.pheeeew.pressrace.v2.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.command.EmotionPressService;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.pressrace.application.PressRaceService;
import com.pheeeew.pressrace.v2.domain.PressRanker;
import com.pheeeew.pressrace.v2.domain.PressScreen;
import com.pheeeew.pressrace.v2.domain.PressView;
import com.pheeeew.pressrace.v2.domain.repository.PressSnapshotRepository;
import com.pheeeew.pressrace.v2.exception.PressStreamErrorCode;
import com.pheeeew.pressrace.v2.exception.PressStreamException;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PressStreamPublisherIntegrationTest {

    private static final Duration PUBLISH_INTERVAL = Duration.ofSeconds(1);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    @Autowired
    private EmotionPressService emotionPressService;

    @Autowired
    private PressRaceService pressRaceService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private JdbcClient jdbc;

    private final List<Device> devices = new ArrayList<>();
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
        publisher = new PressStreamPublisher(new PressSnapshotService(new PressDatabaseSnapshotService(repository), Optional.empty(), Optional.empty()), clock, PUBLISH_INTERVAL,
                HEARTBEAT_INTERVAL, PressStreamSendMode.SEQUENTIAL, 1, 1, Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        for (Device device : devices) {
            jdbc.sql("DELETE FROM device_daily_presses WHERE device_id = :device")
                    .param("device", device.getId()).update();
            deviceRepository.deleteById(device.getId());
        }
        jdbc.sql("UPDATE press_race_control SET active_server_generation = NULL, enabled = FALSE, activation_date = NULL")
                .update();
    }

    @Test
    void 구독_직후_각_기기는_자기_화면의_현재_상태를_받는다() {
        // given
        Device leader = 기기();
        Device follower = 기기();
        emotionPressService.press(leader.getPublicId(), Map.of(EmotionState.ANGRY, 5));
        emotionPressService.press(follower.getPublicId(), Map.of(EmotionState.ANGRY, 3));
        RecordingListener leaderListener = new RecordingListener();
        RecordingListener followerListener = new RecordingListener();

        // when
        구독한다(leader, PressView.PRESS, leaderListener);
        구독한다(follower, PressView.ALL, followerListener);

        // then
        assertThat(leaderListener.snapshots).singleElement().satisfies(screen -> {
            assertThat(screen.me().score()).isEqualTo(5);
            assertThat(screen.me().rank()).isEqualTo(1);
            assertThat(screen.top100()).isEmpty();
        });
        assertThat(followerListener.snapshots).singleElement().satisfies(screen -> {
            assertThat(screen.me().score()).isEqualTo(3);
            assertThat(screen.me().rank()).isEqualTo(2);
            assertThat(screen.top100()).hasSize(2);
        });
        assertThat(leaderListener.heartbeats).isZero();
    }

    @Test
    void 최초_구독은_발행_주기_안의_공통_순위를_재사용하고_지나면_새로_만든다() {
        // given
        Device first = 기기();
        Device second = 기기();
        Device third = 기기();
        구독한다(first, PressView.PRESS, new RecordingListener());

        // when
        clock.advance(PUBLISH_INTERVAL);
        RecordingListener reused = new RecordingListener();
        구독한다(second, PressView.PRESS, reused);
        clock.advance(Duration.ofMillis(1));
        RecordingListener regenerated = new RecordingListener();
        구독한다(third, PressView.PRESS, regenerated);

        // then
        verify(repository, times(2)).findParticipants(any());
        assertThat(regenerated.snapshots.getFirst().generatedAt()).isAfter(reused.snapshots.getFirst().generatedAt());
    }

    @Test
    void 앱_시계의_KST_날짜가_공통_순위_날짜와_다르면_발행_주기_안이어도_새로_만든다() {
        // given
        구독한다(기기(), PressView.PRESS, new RecordingListener());

        // when: 시간 조건은 통과하도록 시계를 하루 앞으로 되돌려 날짜 조건만 다르게 만든다.
        clock.advance(Duration.ofDays(-1));
        구독한다(기기(), PressView.PRESS, new RecordingListener());

        // then
        verify(repository, times(2)).findParticipants(any());
    }

    @Test
    void 멈추면_모든_구독을_닫고_반납하며_멈춘_뒤의_구독도_바로_닫는다() {
        // given
        Device device = 기기();
        RecordingListener before = new RecordingListener();
        구독한다(device, PressView.PRESS, before);

        // when
        publisher.stop();
        RecordingListener after = new RecordingListener();
        구독한다(device, PressView.PRESS, after);

        // then
        assertThat(before.closed).isEqualTo(1);
        assertThat(after.snapshots).hasSize(1);
        assertThat(after.closed).isEqualTo(1);
        assertThat(publisher.subscriptionCount(device.getPublicId())).isZero();
        assertThat(publisher.isRunning()).isFalse();
    }

    @Test
    void 멈춘_뒤_다시_시작하면_새_구독을_받는다() {
        // given
        Device device = 기기();
        publisher.start();
        publisher.stop();

        // when
        publisher.start();
        RecordingListener listener = new RecordingListener();
        구독한다(device, PressView.PRESS, listener);

        // then
        assertThat(publisher.isRunning()).isTrue();
        assertThat(listener.snapshots).hasSize(1);
        assertThat(listener.closed).isZero();
        assertThat(publisher.subscriptionCount(device.getPublicId())).isEqualTo(1);
        publisher.stop();
    }

    @Test
    void 동시에_몰린_최초_구독도_공통_순위를_한_번만_만든다() throws Exception {
        // given
        List<Device> subscribers = List.of(기기(), 기기(), 기기(), 기기());
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            loading.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(repository).findParticipants(any());
        List<RecordingListener> listeners = Collections.synchronizedList(new ArrayList<>());

        // when
        CompletableFuture<Void> loader = CompletableFuture.runAsync(() -> listeners.add(구독(subscribers.getFirst())));
        assertThat(loading.await(10, TimeUnit.SECONDS)).isTrue();
        List<Thread> waiters = new ArrayList<>();
        for (Device device : subscribers.subList(1, subscribers.size())) {
            waiters.add(Thread.ofPlatform().start(() -> listeners.add(구독(device))));
        }
        기다린다(waiters);
        release.countDown();
        loader.get(10, TimeUnit.SECONDS);
        for (Thread waiter : waiters) {
            waiter.join(TimeUnit.SECONDS.toMillis(10));
        }

        // then
        assertThat(listeners).hasSize(4).allSatisfy(listener -> assertThat(listener.snapshots).hasSize(1));
        verify(repository, times(1)).findParticipants(any());
    }

    @Test
    void 커밋된_여러_입력은_다음_주기에_최신_상태_하나로_전달된다() {
        // given
        Device device = 기기();
        RecordingListener listener = new RecordingListener();
        구독한다(device, PressView.PRESS, listener);
        emotionPressService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, 1));
        emotionPressService.press(device.getPublicId(), Map.of(EmotionState.EXHAUSTED, 2));

        // when
        publisher.publish();

        // then
        assertThat(listener.snapshots).hasSize(2);
        assertThat(listener.snapshots.getFirst().me().participating()).isFalse();
        assertThat(listener.snapshots.getLast().me().score()).isEqualTo(3);
    }

    @Test
    void 화면_상태가_그대로면_관찰_시각이_바뀌어도_다시_보내지_않는다() {
        // given
        Device device = 기기();
        emotionPressService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, 1));
        지난_시간(device, 10);
        RecordingListener listener = new RecordingListener();
        구독한다(device, PressView.PRESS, listener);

        // when
        clock.advance(PUBLISH_INTERVAL);
        publisher.publish();
        clock.advance(PUBLISH_INTERVAL);
        publisher.publish();

        // then
        assertThat(listener.snapshots).hasSize(1);
        verify(repository, times(3)).findParticipants(any());
    }

    @Test
    void 프레스가_없어도_시간이_지나면_활동_인원이_줄어든_상태를_받는다() {
        // given
        Device device = 기기();
        emotionPressService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, 1));
        RecordingListener listener = new RecordingListener();
        구독한다(device, PressView.PRESS, listener);
        지난_시간(device, 5);

        // when
        publisher.publish();

        // then
        assertThat(listener.snapshots).extracting(PressScreen::activeCount).containsExactly(1, 0);
        assertThat(listener.snapshots.getLast().me().score()).isEqualTo(1);
    }

    @Test
    void 구독자_수와_무관하게_주기마다_공통_순위를_한_번_만든다() {
        // given
        구독한다(기기(), PressView.PRESS, new RecordingListener());
        구독한다(기기(), PressView.ALL, new RecordingListener());
        구독한다(기기(), PressView.PRESS, new RecordingListener());

        // when
        publisher.publish();

        // then
        verify(repository, times(2)).findNow();
        verify(repository, times(2)).findParticipants(any());
    }

    @Test
    void 구독자가_없으면_DB를_읽지_않는다() {
        // given
        Device device = 기기();
        PressStreamSubscription subscription = 구독한다(device, PressView.PRESS, new RecordingListener());
        subscription.cancel();

        // when
        publisher.publish();

        // then
        verify(repository, times(1)).findNow();
    }

    @Test
    void heartbeat_는_간격마다_보낸다() {
        // given
        RecordingListener listener = new RecordingListener();
        구독한다(기기(), PressView.PRESS, listener);

        // when
        publisher.publish();
        int first = listener.heartbeats;
        clock.advance(HEARTBEAT_INTERVAL.minusMillis(1));
        publisher.publish();
        int beforeInterval = listener.heartbeats;
        clock.advance(Duration.ofMillis(1));
        publisher.publish();

        // then
        assertThat(first).isEqualTo(1);
        assertThat(beforeInterval).isEqualTo(1);
        assertThat(listener.heartbeats).isEqualTo(2);
    }

    @Test
    void 토큰이_만료된_구독은_더_보내지_않고_닫고_구독_수를_반납한다() {
        // given
        Device device = 기기();
        RecordingListener listener = new RecordingListener();
        publisher.subscribe(device.getPublicId(), PressView.PRESS, clock.instant().plusSeconds(10), listener);

        // when
        clock.advance(Duration.ofSeconds(10));
        emotionPressService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, 4));
        publisher.publish();

        // then
        assertThat(listener.snapshots).hasSize(1);
        assertThat(listener.heartbeats).isZero();
        assertThat(listener.closed).isEqualTo(1);
        assertThat(publisher.subscriptionCount(device.getPublicId())).isZero();
    }

    @Test
    void 전송에_실패한_구독은_정리하고_다음_주기에_보내지_않는다() {
        // given
        Device device = 기기();
        RecordingListener listener = new RecordingListener();
        구독한다(device, PressView.PRESS, listener);
        listener.failing = true;

        // when
        publisher.publish();
        listener.failing = false;
        publisher.publish();

        // then
        assertThat(listener.heartbeats).isZero();
        assertThat(listener.closed).isEqualTo(1);
        assertThat(publisher.subscriptionCount(device.getPublicId())).isZero();
    }

    @Test
    void 같은_기기의_세_번째_구독은_거절하고_기존_구독은_유지한다() {
        // given
        Device device = 기기();
        RecordingListener first = new RecordingListener();
        PressStreamSubscription firstSubscription = 구독한다(device, PressView.PRESS, first);
        구독한다(device, PressView.ALL, new RecordingListener());
        RecordingListener rejected = new RecordingListener();

        // when & then
        assertThatThrownBy(() -> 구독한다(device, PressView.PRESS, rejected))
                .isInstanceOfSatisfying(PressStreamException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(PressStreamErrorCode.PRESS_STREAM_SUBSCRIPTION_LIMIT_EXCEEDED));
        assertThat(rejected.snapshots).isEmpty();
        assertThat(publisher.subscriptionCount(device.getPublicId())).isEqualTo(2);
        publisher.publish();
        assertThat(first.heartbeats).isEqualTo(1);

        firstSubscription.cancel();
        firstSubscription.cancel();
        assertThat(publisher.subscriptionCount(device.getPublicId())).isEqualTo(1);
        구독한다(device, PressView.PRESS, new RecordingListener());
        assertThat(publisher.subscriptionCount(device.getPublicId())).isEqualTo(2);
    }

    @Test
    void 최초_공통_순위를_만들지_못하면_오래된_순위를_보내지_않고_구독하지_않는다() {
        // given
        Device device = 기기();
        구독한다(기기(), PressView.PRESS, new RecordingListener());
        clock.advance(PUBLISH_INTERVAL.plusMillis(1));
        doThrow(new DataAccessResourceFailureException("DB 연결 실패")).when(repository).findParticipants(any());
        RecordingListener listener = new RecordingListener();

        // when & then
        assertThatThrownBy(() -> 구독한다(device, PressView.PRESS, listener))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(listener.snapshots).isEmpty();
        assertThat(publisher.subscriptionCount(device.getPublicId())).isZero();
    }

    @Test
    void 주기_중_공통_순위를_만들지_못하면_화면을_보내지_않고_연결은_유지한다() {
        // given
        Device device = 기기();
        RecordingListener listener = new RecordingListener();
        구독한다(device, PressView.PRESS, listener);
        emotionPressService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, 2));
        doThrow(new DataAccessResourceFailureException("DB 연결 실패")).when(repository).findParticipants(any());

        // when
        publisher.publish();
        doCallRealMethod().when(repository).findParticipants(any());
        publisher.publish();

        // then
        assertThat(listener.snapshots).hasSize(2);
        assertThat(listener.snapshots.getLast().me().score()).isEqualTo(2);
        assertThat(listener.heartbeats).isEqualTo(1);
        assertThat(listener.closed).isZero();
    }

    private PressStreamSubscription 구독한다(Device device, PressView view, RecordingListener listener) {
        return publisher.subscribe(device.getPublicId(), view, clock.instant().plus(Duration.ofMinutes(30)), listener);
    }

    private RecordingListener 구독(Device device) {
        RecordingListener listener = new RecordingListener();
        구독한다(device, PressView.PRESS, listener);
        return listener;
    }

    private Device 기기() {
        Device device = deviceRepository.save(기본_기기_빌더().build());
        devices.add(device);
        return device;
    }

    private void 지난_시간(Device device, int seconds) {
        jdbc.sql("""
                UPDATE press_daily_scores SET last_applied_at = last_applied_at - make_interval(secs => :seconds)
                WHERE device_id = :device
                """).param("seconds", seconds).param("device", device.getId()).update();
    }

    private void 기다린다(List<Thread> waiters) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (waiters.stream().anyMatch(thread -> thread.getState() == Thread.State.RUNNABLE
                || thread.getState() == Thread.State.NEW)) {
            assertThat(System.nanoTime()).isLessThan(deadline);
            Thread.sleep(10);
        }
    }

    private static final class RecordingListener implements PressStreamListener {

        private final List<PressScreen> snapshots = Collections.synchronizedList(new ArrayList<>());
        private int heartbeats;
        private int closed;
        private volatile boolean failing;

        @Override
        public void sendSnapshot(PressScreen screen) throws IOException {
            fail();
            snapshots.add(screen);
        }

        @Override
        public void sendHeartbeat() throws IOException {
            fail();
            heartbeats++;
        }

        @Override
        public void close() {
            closed++;
        }

        private void fail() throws IOException {
            if (failing) {
                throw new IOException("연결이 끊겼습니다.");
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
