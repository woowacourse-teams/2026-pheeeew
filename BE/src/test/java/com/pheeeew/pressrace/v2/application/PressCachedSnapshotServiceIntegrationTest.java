package com.pheeeew.pressrace.v2.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.github.benmanes.caffeine.cache.Ticker;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.command.EmotionPressService;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.pressrace.application.PressRaceService;
import com.pheeeew.pressrace.v2.domain.PressRanker;
import com.pheeeew.pressrace.v2.domain.PressScreen;
import com.pheeeew.pressrace.v2.domain.PressView;
import com.pheeeew.pressrace.v2.domain.repository.PressSnapshotRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Import({PressSnapshotRepository.class, PressDatabaseSnapshotService.class, PressSnapshotService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PressCachedSnapshotServiceIntegrationTest {

    private static final Duration TTL = Duration.ofSeconds(1);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private PressSnapshotService pressSnapshotService;

    @Autowired
    private EmotionPressService emotionPressService;

    @Autowired
    private PressRaceService pressRaceService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private JdbcClient jdbc;

    private final List<Device> devices = new ArrayList<>();
    private final AtomicLong nanos = new AtomicLong();
    private MutableClock clock;
    private PressSnapshotRepository repository;
    private PressCachedSnapshotService cachedService;

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                UPDATE press_race_control SET active_server_generation = :generation, enabled = TRUE,
                  activation_date = (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date
                """).param("generation", pressRaceService.serverGeneration()).update();
        clock = new MutableClock(Instant.now());
        repository = spy(new PressSnapshotRepository(jdbc));
        Ticker ticker = nanos::get;
        cachedService = new PressCachedSnapshotService(new PressSnapshotService(new PressDatabaseSnapshotService(repository), Optional.empty(), Optional.empty()), clock,
                new SimpleMeterRegistry(), TTL, ticker);
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
    void 같은_DB_상태에서_기본_폴링과_같은_화면을_돌려준다() {
        // given
        Device first = 기기();
        Device second = 기기();
        Device observer = 기기();
        emotionPressService.press(first.getPublicId(), Map.of(EmotionState.ANGRY, 5));
        emotionPressService.press(second.getPublicId(), Map.of(EmotionState.FRUSTRATED, 5, EmotionState.ANGRY, 1));

        for (Device device : List.of(first, second, observer)) {
            for (PressView view : PressView.values()) {
                // when
                PressScreen basic = pressSnapshotService.findSnapshot(device.getPublicId(), view);
                PressScreen cached = cachedService.findSnapshot(device.getPublicId(), view);

                // then
                assertThat(cached).usingRecursiveComparison().ignoringFields("generatedAt").isEqualTo(basic);
            }
        }
    }

    @Test
    void 여러_기기가_같은_스냅샷을_써도_각자의_내_화면을_받는다() {
        // given
        Device leader = 기기();
        Device follower = 기기();
        Device observer = 기기();
        emotionPressService.press(leader.getPublicId(), Map.of(EmotionState.ANGRY, 5));
        emotionPressService.press(follower.getPublicId(), Map.of(EmotionState.ANGRY, 3));

        // when
        PressScreen leaderScreen = cachedService.findSnapshot(leader.getPublicId(), PressView.PRESS);
        PressScreen followerScreen = cachedService.findSnapshot(follower.getPublicId(), PressView.PRESS);
        PressScreen observerScreen = cachedService.findSnapshot(observer.getPublicId(), PressView.PRESS);

        // then
        assertThat(leaderScreen.me().score()).isEqualTo(5);
        assertThat(leaderScreen.me().rank()).isEqualTo(1);
        assertThat(followerScreen.me().score()).isEqualTo(3);
        assertThat(followerScreen.me().rank()).isEqualTo(2);
        assertThat(observerScreen.me()).isEqualTo(PressRanker.absent());
        assertThat(observerScreen.nearby()).extracting(PressRanker::score).containsExactly(5L, 3L);
        verify(repository, times(1)).findParticipants(any());
    }

    @Test
    void 유효시간_안에는_DB를_다시_조회하지_않고_지나면_새로_조회한다() {
        // given
        Device device = 기기();
        emotionPressService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, 2));
        PressScreen first = cachedService.findSnapshot(device.getPublicId(), PressView.PRESS);
        emotionPressService.press(device.getPublicId(), Map.of(EmotionState.EXHAUSTED, 3));

        // when
        nanos.addAndGet(TTL.minusMillis(1).toNanos());
        PressScreen withinTtl = cachedService.findSnapshot(device.getPublicId(), PressView.PRESS);
        nanos.addAndGet(Duration.ofMillis(1).toNanos());
        PressScreen afterTtl = cachedService.findSnapshot(device.getPublicId(), PressView.PRESS);

        // then
        assertThat(first.me().score()).isEqualTo(2);
        assertThat(withinTtl).isEqualTo(first);
        assertThat(afterTtl.me().score()).isEqualTo(5);
        assertThat(afterTtl.generatedAt()).isAfter(first.generatedAt());
        verify(repository, times(2)).findNow();
        verify(repository, times(2)).findParticipants(any());
    }

    @Test
    void 새_프레스가_없으면_유효시간이_지난_뒤_활동_인원이_줄어든다() {
        // given
        Device device = 기기();
        emotionPressService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, 1));
        int active = cachedService.findSnapshot(device.getPublicId(), PressView.PRESS).activeCount();
        지난_시간(device, 5);

        // when
        int withinTtl = cachedService.findSnapshot(device.getPublicId(), PressView.PRESS).activeCount();
        nanos.addAndGet(TTL.toNanos());
        PressScreen afterTtl = cachedService.findSnapshot(device.getPublicId(), PressView.PRESS);

        // then
        assertThat(active).isEqualTo(1);
        assertThat(withinTtl).isEqualTo(1);
        assertThat(afterTtl.activeCount()).isZero();
        assertThat(afterTtl.me().score()).isEqualTo(1);
    }

    @Test
    void KST_날짜가_바뀌면_유효시간_안이어도_전날_스냅샷을_쓰지_않는다() {
        // given
        Device device = 기기();
        emotionPressService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, 4));
        LocalDate today = LocalDate.ofInstant(clock.instant(), KST);
        PressScreen todayScreen = cachedService.findSnapshot(device.getPublicId(), PressView.PRESS);

        // when
        clock.advance(Duration.ofDays(1));
        PressScreen nextDayScreen = cachedService.findSnapshot(device.getPublicId(), PressView.PRESS);

        // then
        assertThat(todayScreen.date()).isEqualTo(today);
        assertThat(todayScreen.me().participating()).isTrue();
        assertThat(nextDayScreen.date()).isEqualTo(today.plusDays(1));
        assertThat(nextDayScreen.me().participating()).isFalse();
        assertThat(nextDayScreen.nearby()).isEmpty();
        verify(repository).findParticipants(today);
        verify(repository).findParticipants(today.plusDays(1));
    }

    @Test
    void 캐시가_비었을_때_동시에_들어온_요청은_DB를_한_번만_조회한다() throws Exception {
        // given
        Device device = 기기();
        emotionPressService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, 7));
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            loading.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(repository).findParticipants(any());

        // when
        CompletableFuture<PressScreen> loader = CompletableFuture.supplyAsync(
                () -> cachedService.findSnapshot(device.getPublicId(), PressView.PRESS));
        assertThat(loading.await(10, TimeUnit.SECONDS)).isTrue();
        List<Thread> waiters = new ArrayList<>();
        List<PressScreen> screens = Collections.synchronizedList(new ArrayList<>());
        for (int count = 0; count < 4; count++) {
            Thread waiter = Thread.ofPlatform().start(
                    () -> screens.add(cachedService.findSnapshot(device.getPublicId(), PressView.PRESS)));
            waiters.add(waiter);
        }
        기다린다(waiters);
        release.countDown();
        screens.add(loader.get(10, TimeUnit.SECONDS));
        for (Thread waiter : waiters) {
            waiter.join(TimeUnit.SECONDS.toMillis(10));
        }

        // then
        assertThat(screens).hasSize(5).allSatisfy(screen -> assertThat(screen.me().score()).isEqualTo(7));
        verify(repository, times(1)).findParticipants(any());
    }

    @Test
    void 오늘_참가자가_없으면_빈_목록과_미참여_상태를_돌려준다() {
        // given
        Device device = 기기();
        clock.set(Instant.parse("2099-01-01T03:00:00Z"));

        // when
        PressScreen screen = cachedService.findSnapshot(device.getPublicId(), PressView.ALL);

        // then
        assertThat(screen.date()).isEqualTo(LocalDate.of(2099, 1, 1));
        assertThat(screen.activeCount()).isZero();
        assertThat(screen.me()).isEqualTo(PressRanker.absent());
        assertThat(screen.nearby()).isEmpty();
        assertThat(screen.top100()).isEmpty();
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

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> now;

        private MutableClock(Instant now) {
            this.now = new AtomicReference<>(now);
        }

        private void advance(Duration duration) {
            now.updateAndGet(value -> value.plus(duration));
        }

        private void set(Instant instant) {
            now.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return KST;
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
