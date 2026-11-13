package com.pheeeew.pressrace.v2.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.pressrace.v2.fixture.PressTodayFixture.하루_기록;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.github.benmanes.caffeine.cache.Ticker;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.pressrace.application.PressRaceService;
import com.pheeeew.pressrace.exception.PressRaceException;
import com.pheeeew.pressrace.infra.PressRaceRepository;
import com.pheeeew.pressrace.v2.application.PressDatabaseSnapshotService;
import com.pheeeew.pressrace.v2.application.PressSnapshotService;
import com.pheeeew.pressrace.v2.domain.PressScreen;
import com.pheeeew.pressrace.v2.domain.PressSnapshot;
import com.pheeeew.pressrace.v2.domain.PressTodayObservation;
import com.pheeeew.pressrace.v2.domain.PressTodayParticipant;
import com.pheeeew.pressrace.v2.domain.PressView;
import com.pheeeew.pressrace.v2.domain.repository.PressSnapshotRepository;
import com.pheeeew.pressrace.v2.infra.PressBufferRepository;
import com.pheeeew.pressrace.v2.infra.PressRedisStore;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PressRedisTodayBoardIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long 초 = TimeUnit.SECONDS.toNanos(1);
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connections;
    private static StringRedisTemplate redis;

    @Autowired
    private PressRaceService pressRaceService;
    @Autowired
    private PressRedisFlushWriter pressRedisFlushWriter;
    @Autowired
    private PressBufferRepository pressBufferRepository;
    @Autowired
    private PressRaceRepository pressRaceRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private DataSource dataSource;

    private final List<Device> devices = new ArrayList<>();
    private final AtomicLong nanos = new AtomicLong();
    private final Ticker ticker = nanos::get;
    private SimpleMeterRegistry meterRegistry;
    private PressRedisStore store;
    private LocalDate today;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connections = new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connections.afterPropertiesSet();
        connections.start();
        redis = new StringRedisTemplate(connections);
    }

    @AfterAll
    static void stopRedis() {
        connections.destroy();
        REDIS.stop();
    }

    @BeforeEach
    void setUp() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        meterRegistry = new SimpleMeterRegistry();
        store = new PressRedisStore(redis);
        today = LocalDate.now(KST);
        jdbc.sql("""
                UPDATE press_race_control SET active_server_generation = :generation, enabled = TRUE,
                  activation_date = (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date
                """).param("generation", pressRaceService.serverGeneration()).update();
    }

    @AfterEach
    void tearDown() {
        for (Device device : devices) {
            jdbc.sql("DELETE FROM device_daily_presses WHERE device_id = :device").param("device", device.getId()).update();
            jdbc.sql("DELETE FROM press_daily_scores WHERE device_id = :device").param("device", device.getId()).update();
            deviceRepository.deleteById(device.getId());
        }
        jdbc.sql("UPDATE press_race_control SET active_server_generation = NULL, enabled = FALSE, activation_date = NULL")
                .update();
    }

    @Test
    void DB의_오늘_값을_시딩해_보드로_만든_공통_순위는_동점_대표_감정_닉네임_없음까지_DB_경로의_화면과_같다() {
        // given
        List<Device> participants = 동점과_닉네임_없는_참가자를_DB에_기록한다();
        Device observer = 기기(닉네임());
        PressRedisTodayBoard board = 보드(Clock.system(KST));
        PressSnapshotService databaseService = new PressSnapshotService(데이터베이스_공통_순위(), Optional.empty(), Optional.empty());
        PressSnapshotService boardService = new PressSnapshotService(데이터베이스_공통_순위(), Optional.of(board), Optional.empty());

        // when
        board.refresh();
        board.refresh();

        // then
        for (Device device : List.of(participants.get(0), participants.get(1), participants.get(2), observer)) {
            for (PressView view : PressView.values()) {
                PressScreen expected = databaseService.findSnapshot(device.getPublicId(), view);
                PressScreen actual = boardService.findSnapshot(device.getPublicId(), view);
                assertThat(actual).usingRecursiveComparison().ignoringFields("generatedAt").isEqualTo(expected);
            }
        }
        PressScreen tied = boardService.findSnapshot(participants.get(0).getPublicId(), PressView.PRESS);
        PressScreen unnamed = boardService.findSnapshot(participants.get(1).getPublicId(), PressView.PRESS);
        assertThat(tied.me().representativeState()).isEqualTo(EmotionState.ANGRY);
        assertThat(unnamed.me().nickname()).isEqualTo("익명");
    }

    @Test
    void JVM_시계가_어제여도_Redis_날짜의_순위를_만들고_어제_키는_읽지_않는다() {
        // given
        LocalDate yesterday = today.minusDays(1);
        Clock yesterdayClock = Clock.fixed(yesterday.atTime(12, 0).atZone(KST).toInstant(), KST);
        UUID device = UUID.randomUUID();
        store.markSeeded(today);
        store.write(device, today, Map.of(EmotionState.ANGRY, 2), UUID.randomUUID());
        PressRedisTodayBoard board = 보드(yesterdayClock);

        // when
        board.refresh();

        // then
        PressTodayObservation observation = board.current();
        assertThat(observation.date()).isEqualTo(today);
        assertThat(observation.participants()).extracting(PressTodayParticipant::devicePublicId, PressTodayParticipant::total)
                .containsExactly(tuple(device, 2L));
        assertThat(redis.hasKey("{press}:seeded:" + yesterday)).isFalse();
    }

    @Test
    void Redis가_비어_다시_시딩되면_버전이_같아도_전체를_다시_읽어_DB의_오늘_값을_보인다() {
        // given
        Device device = 기기(닉네임());
        DB에_오늘_기록한다(device, Map.of(EmotionState.ANGRY, 5L), 3);
        PressRedisTodayBoard board = 보드(Clock.system(KST));
        board.refresh();
        long before = 합계(board.current(), device);
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        DB에_오늘_기록한다(device, Map.of(EmotionState.ANGRY, 8L), 4);

        // when
        board.refresh();

        // then
        assertThat(before).isEqualTo(5L);
        assertThat(합계(board.current(), device)).isEqualTo(8L);
        assertThat(redis.hasKey("{press}:seeded:" + today)).isTrue();
    }

    @Test
    void 버전이_같은_틱에도_관찰_시각이_새_Redis_시각으로_나아가고_활동_인원은_그_시각으로_센다() {
        // given
        UUID active = UUID.randomUUID();
        UUID idle = UUID.randomUUID();
        store.markSeeded(today);
        store.seedDay(하루_기록(idle, today, Map.of(EmotionState.ANGRY, 1L), Instant.now().minus(Duration.ofHours(1)), 1));
        store.write(active, today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());
        PressRedisTodayBoard board = 보드(Clock.system(KST));
        PressSnapshotService service = new PressSnapshotService(데이터베이스_공통_순위(), Optional.of(board), Optional.empty());
        board.refresh();
        board.refresh();
        Instant first = board.current().observedAt();

        // when
        board.refresh();
        PressSnapshot snapshot = service.findTodaySnapshot();

        // then
        PressTodayObservation observation = board.current();
        PressScreen screen = snapshot.findScreen(active, PressView.PRESS);
        assertThat(meterRegistry.counter("pheeeew.press.redis.read.unchanged").count()).isEqualTo(1.0);
        assertThat(observation.observedAt()).isAfter(first);
        assertThat(screen.generatedAt()).isEqualTo(observation.observedAt());
        assertThat(screen.activeCount()).isEqualTo(observation.participants().stream()
                .filter(participant -> participant.lastAppliedAt().plusSeconds(5).isAfter(observation.observedAt()))
                .count());
        assertThat(screen.activeCount()).isEqualTo(1);
    }

    @Test
    void Redis가_멈추면_3초_동안은_마지막_관찰을_돌려주고_넘으면_저장소_사용_불가이며_회복한_첫_틱부터_다시_보인다() {
        // given
        LettuceConnectionFactory shortTimeout = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(300)).build());
        shortTimeout.afterPropertiesSet();
        shortTimeout.start();
        PressRedisStore pausable = new PressRedisStore(new StringRedisTemplate(shortTimeout));
        pausable.markSeeded(today);
        pausable.write(UUID.randomUUID(), today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());
        PressRedisTodayBoard board = PressRedisTodayBoardFixture.보드(pausable, pressBufferRepository,
                Clock.system(KST), meterRegistry, ticker);
        board.refresh();
        PressTodayObservation last = board.current();

        try {
            // when
            REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
            nanos.set(3 * 초 - 1);
            board.refresh();
            PressTodayObservation held = board.current();
            nanos.set(3 * 초);

            // then
            assertThat(held).isSameAs(last);
            assertThat(meterRegistry.counter("pheeeew.press.redis.read.failures").count()).isEqualTo(1.0);
            assertThatThrownBy(board::current).isInstanceOfSatisfying(PressRaceException.class, exception -> {
                assertThat(exception.code()).isEqualTo("STORE_UNAVAILABLE");
                assertThat(exception.retryAfterSeconds()).isEqualTo(1);
            });
        } finally {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
        board.refresh();
        assertThat(board.current().observedAt()).isAfterOrEqualTo(last.observedAt());
        shortTimeout.destroy();
    }

    @Test
    void 닉네임_사본이_없으면_DB_닉네임으로_채우고_10분짜리_사본을_남긴다() {
        // given
        String nickname = 닉네임();
        Device named = 기기(nickname);
        Device unnamed = 기기(null);
        store.markSeeded(today);
        store.write(named.getPublicId(), today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());
        store.write(unnamed.getPublicId(), today, Map.of(EmotionState.ANGRY, 2), UUID.randomUUID());
        PressRedisTodayBoard board = 보드(Clock.system(KST));

        // when
        board.refresh();
        board.refresh();

        // then
        assertThat(board.current().nicknames()).containsEntry(named.getPublicId(), nickname)
                .containsEntry(unnamed.getPublicId(), null);
        assertThat(redis.opsForValue().get("{press}:nickname:" + named.getPublicId())).isEqualTo(nickname);
        assertThat(redis.opsForValue().get("{press}:nickname:" + unnamed.getPublicId())).isEmpty();
        assertThat(redis.getExpire("{press}:nickname:" + named.getPublicId(), TimeUnit.MILLISECONDS))
                .isBetween(1L, 600_000L);
    }

    @Test
    void 닉네임을_DB에서_읽지_못하면_직전_닉네임을_쓰고_다음_틱에_전체를_다시_읽는다() {
        // given
        PressBufferRepository unreachable = new PressBufferRepository(
                JdbcClient.create(new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:1/none")));
        UUID named = UUID.randomUUID();
        UUID newcomer = UUID.randomUUID();
        store.markSeeded(today);
        store.write(named, today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());
        store.fillNicknames(Map.of(named, "별"));
        PressRedisTodayBoard board = PressRedisTodayBoardFixture.보드(store, unreachable, Clock.system(KST),
                meterRegistry, ticker);
        board.refresh();
        redis.delete("{press}:nickname:" + named);
        store.write(newcomer, today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());

        // when
        board.refresh();
        Map<UUID, String> fallback = board.current().nicknames();
        board.refresh();

        // then
        assertThat(fallback).containsEntry(named, "별").containsEntry(newcomer, null);
        assertThat(meterRegistry.counter("pheeeew.press.redis.read.full").count()).isEqualTo(3.0);
        assertThat(meterRegistry.counter("pheeeew.press.redis.read.unchanged").count()).isZero();
        assertThat(meterRegistry.counter("pheeeew.press.redis.nickname.fill.failures").count()).isEqualTo(2.0);
        assertThat(board.current().nicknames()).containsEntry(named, "별");
    }

    @Test
    void DB가_응답하지_않는_동안에도_보드_틱은_채우기를_기다리지_않고_성공해_3초가_지나도_순위를_내보낸다() throws Exception {
        // given
        BlockingDataSource blocked = new BlockingDataSource(dataSource);
        ExecutorService filler = 채우기_실행기();
        Device device = 기기(닉네임());
        store.markSeeded(today);
        store.write(device.getPublicId(), today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());
        PressRedisTodayBoard board = 막힌_DB의_보드(blocked, filler);

        try {
            // when
            기다리지_않는_틱(board);
            nanos.set(2 * 초);
            기다리지_않는_틱(board);
            nanos.set(4 * 초);
            기다리지_않는_틱(board);
            nanos.set(6 * 초);

            // then
            assertThat(blocked.requests()).isEqualTo(1);
            assertThat(board.current().participants()).extracting(PressTodayParticipant::devicePublicId)
                    .containsExactly(device.getPublicId());
            assertThat(meterRegistry.counter("pheeeew.press.redis.read.failures").count()).isZero();
        } finally {
            blocked.release();
            filler.shutdownNow();
        }
    }

    @Test
    void 닉네임_채우기가_DB를_기다리는_동안_새_참가자가_와도_채우기_작업은_하나만_들어간다() throws Exception {
        // given
        BlockingDataSource blocked = new BlockingDataSource(dataSource);
        ExecutorService filler = 채우기_실행기();
        store.markSeeded(today);
        store.write(UUID.randomUUID(), today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());
        PressRedisTodayBoard board = 막힌_DB의_보드(blocked, filler);
        기다리지_않는_틱(board);

        try {
            // when
            store.write(UUID.randomUUID(), today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());
            기다리지_않는_틱(board);
            store.write(UUID.randomUUID(), today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());
            기다리지_않는_틱(board);
            int requestsWhileBlocked = blocked.requests();
            blocked.release();
            채우기가_끝날_때까지_기다린다(filler);
            기다리지_않는_틱(board);
            채우기가_끝날_때까지_기다린다(filler);

            // then
            assertThat(requestsWhileBlocked).isEqualTo(1);
            assertThat(blocked.requests()).isEqualTo(2);
        } finally {
            blocked.release();
            filler.shutdownNow();
        }
    }

    @Test
    void DB가_회복되어_채우기가_끝나면_다음_전체_읽기에_DB_닉네임이_보인다() throws Exception {
        // given
        String nickname = 닉네임();
        BlockingDataSource blocked = new BlockingDataSource(dataSource);
        ExecutorService filler = 채우기_실행기();
        Device device = 기기(nickname);
        store.markSeeded(today);
        store.write(device.getPublicId(), today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());
        PressRedisTodayBoard board = 막힌_DB의_보드(blocked, filler);

        try {
            기다리지_않는_틱(board);
            Map<UUID, String> whileBlocked = board.current().nicknames();

            // when
            blocked.release();
            채우기가_끝날_때까지_기다린다(filler);
            기다리지_않는_틱(board);

            // then
            assertThat(whileBlocked).containsEntry(device.getPublicId(), null);
            assertThat(board.current().nicknames()).containsEntry(device.getPublicId(), nickname);
            assertThat(redis.opsForValue().get("{press}:nickname:" + device.getPublicId())).isEqualTo(nickname);
        } finally {
            blocked.release();
            filler.shutdownNow();
        }
    }

    @Test
    void REDIS_모드의_지난_날짜와_미래_날짜_순위는_DB_경로와_같고_Redis_오늘_날짜만_보드_순위다() {
        // given
        Device device = 기기(닉네임());
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);
        pressRedisFlushWriter.write(List.of(하루_기록(device.getPublicId(), yesterday, Map.of(EmotionState.ANGRY, 4L),
                Instant.now().minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MILLIS), 1)));
        DB에_오늘_기록한다(device, Map.of(EmotionState.ANGRY, 2L), 1);
        PressRedisTodayBoard board = 보드(Clock.system(KST));
        board.refresh();
        store.write(device.getPublicId(), today, Map.of(EmotionState.ANGRY, 3), UUID.randomUUID());
        board.refresh();
        PressSnapshotService databaseService = new PressSnapshotService(데이터베이스_공통_순위(), Optional.empty(), Optional.empty());
        PressSnapshotService boardService = new PressSnapshotService(데이터베이스_공통_순위(), Optional.of(board), Optional.empty());

        // when
        PressScreen yesterdayScreen = boardService.findSnapshotOn(yesterday).findScreen(device.getPublicId(), PressView.ALL);
        PressScreen tomorrowScreen = boardService.findSnapshotOn(tomorrow).findScreen(device.getPublicId(), PressView.ALL);
        PressScreen todayScreen = boardService.findSnapshotOn(today).findScreen(device.getPublicId(), PressView.ALL);

        // then
        assertThat(yesterdayScreen).usingRecursiveComparison().ignoringFields("generatedAt")
                .isEqualTo(databaseService.findSnapshotOn(yesterday).findScreen(device.getPublicId(), PressView.ALL));
        assertThat(yesterdayScreen.me().score()).isEqualTo(4);
        assertThat(tomorrowScreen).usingRecursiveComparison().ignoringFields("generatedAt")
                .isEqualTo(databaseService.findSnapshotOn(tomorrow).findScreen(device.getPublicId(), PressView.ALL));
        assertThat(tomorrowScreen.me().participating()).isFalse();
        assertThat(todayScreen.me().score()).isEqualTo(5);
        assertThat(databaseService.findSnapshotOn(today).findScreen(device.getPublicId(), PressView.ALL).me().score())
                .isEqualTo(2);
    }

    private List<Device> 동점과_닉네임_없는_참가자를_DB에_기록한다() {
        Device tiedNamed = 기기(닉네임());
        Device tiedUnnamed = 기기(null);
        Device third = 기기(닉네임());
        DB에_오늘_기록한다(tiedNamed, Map.of(EmotionState.ANGRY, 3L, EmotionState.FRUSTRATED, 3L), 2);
        DB에_오늘_기록한다(tiedUnnamed, Map.of(EmotionState.EXHAUSTED, 6L), 1);
        DB에_오늘_기록한다(third, Map.of(EmotionState.IRRITATED, 2L, EmotionState.DISCOURAGED, 2L), 4);
        return List.of(tiedNamed, tiedUnnamed, third);
    }

    private void DB에_오늘_기록한다(Device device, Map<EmotionState, Long> counts, long version) {
        pressRedisFlushWriter.write(List.of(하루_기록(device.getPublicId(), today, counts,
                Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MILLIS), version)));
    }

    private PressRedisTodayBoard 막힌_DB의_보드(BlockingDataSource blocked, ExecutorService filler) {
        return PressRedisTodayBoardFixture.보드(store, new PressBufferRepository(JdbcClient.create(blocked)),
                Clock.system(KST), meterRegistry, ticker, filler);
    }

    private ExecutorService 채우기_실행기() {
        return Executors.newSingleThreadExecutor(Thread.ofPlatform().name("test-press-redis-nickname").daemon().factory());
    }

    private void 기다리지_않는_틱(PressRedisTodayBoard board) {
        assertTimeoutPreemptively(Duration.ofSeconds(2), board::refresh);
    }

    private void 채우기가_끝날_때까지_기다린다(ExecutorService filler) throws Exception {
        filler.submit(() -> { }).get(10, TimeUnit.SECONDS);
    }

    private PressRedisTodayBoard 보드(Clock clock) {
        return PressRedisTodayBoardFixture.보드(store, pressBufferRepository, clock, meterRegistry, ticker);
    }

    private PressDatabaseSnapshotService 데이터베이스_공통_순위() {
        return new PressDatabaseSnapshotService(new PressSnapshotRepository(jdbc));
    }

    private long 합계(PressTodayObservation observation, Device device) {
        return observation.participants().stream()
                .filter(participant -> participant.devicePublicId().equals(device.getPublicId()))
                .findFirst().orElseThrow().total();
    }

    private Device 기기(String nickname) {
        Device device = deviceRepository.saveAndFlush(기본_기기_빌더().nickname(nickname).build());
        devices.add(device);
        return device;
    }

    private String 닉네임() {
        return "n" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** 풀 고갈이나 응답 없는 DB 처럼 연결 요청이 열릴 때까지 막힌다. 연결 요청 수를 센다. */
    private static final class BlockingDataSource extends DelegatingDataSource {

        private final CountDownLatch gate = new CountDownLatch(1);
        private final AtomicInteger requests = new AtomicInteger();

        private BlockingDataSource(DataSource target) {
            super(target);
        }

        private int requests() {
            return requests.get();
        }

        private void release() {
            gate.countDown();
        }

        @Override
        public Connection getConnection() throws SQLException {
            requests.incrementAndGet();
            try {
                gate.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new SQLException("연결 대기가 중단되었습니다.", exception);
            }
            return super.getConnection();
        }
    }
}
