package com.pheeeew.pressrace.v2.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.pressrace.v2.fixture.PressTodayFixture.하루_기록;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.github.benmanes.caffeine.cache.Ticker;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.pressrace.v2.application.PressRedisFlushWriter;
import com.pheeeew.pressrace.v2.application.PressRedisScreenReader;
import com.pheeeew.pressrace.v2.application.PressRedisSeeder;
import com.pheeeew.pressrace.v2.application.PressRedisTodayBoard;
import com.pheeeew.pressrace.v2.application.PressRedisTodayBoardFixture;
import com.pheeeew.pressrace.v2.domain.PressStoredDay;
import com.pheeeew.pressrace.v2.domain.PressTodayParticipant;
import com.pheeeew.pressrace.exception.PressRaceException;
import com.pheeeew.pressrace.v2.infra.PressBufferRepository;
import com.pheeeew.pressrace.v2.infra.PressRedisStore;
import com.pheeeew.pressrace.v2.domain.PressParticipant;
import com.pheeeew.pressrace.v2.domain.PressRanker;
import com.pheeeew.pressrace.v2.domain.PressScreen;
import com.pheeeew.pressrace.v2.domain.PressSnapshot;
import com.pheeeew.pressrace.v2.domain.PressView;
import com.pheeeew.pressrace.v2.domain.repository.PressSnapshotRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** REDIS 모드 기본 폴링이 순위표에서 바로 읽은 화면을 같은 Redis 상태의 전원 정렬 화면(PressSnapshot)과 비교한다. */
@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PressRedisScreenSnapshotIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final EmotionState[] STATES = EmotionState.values();
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connections;
    private static StringRedisTemplate redis;

    @Autowired
    private PressBufferRepository pressBufferRepository;
    @Autowired
    private PressRedisFlushWriter pressRedisFlushWriter;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private JdbcClient jdbc;

    private final List<Device> devices = new ArrayList<>();
    private final Ticker ticker = new AtomicLong()::get;
    private PressRedisStore store;
    private LocalDate today;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connections = 연결(0);
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
        store = new PressRedisStore(redis);
        today = LocalDate.now(KST);
    }

    @AfterEach
    void tearDown() {
        for (Device device : devices) {
            jdbc.sql("DELETE FROM device_daily_presses WHERE device_id = :device").param("device", device.getId()).update();
            jdbc.sql("DELETE FROM press_daily_scores WHERE device_id = :device").param("device", device.getId()).update();
            deviceRepository.deleteById(device.getId());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 150})
    void 시딩한_순위표에서_바로_읽은_화면은_모든_요청자와_화면에서_같은_데이터의_전원_정렬_화면과_같다(int size) {
        // given
        Random random = new Random(20261110L + size);
        List<UUID> participants = 무작위_참가자를_시딩한다(random, size);
        store.markSeeded(today);
        닉네임_사본을_채운다(random, participants);

        // when
        List<Integer> activeCounts = 모든_요청자의_화면이_전원_정렬_화면과_같다(participants);

        // then
        if (size == 150) {
            List<PressTodayParticipant> ordered = 오늘_정렬_순서();
            assertThat(ordered.get(99).total()).isEqualTo(ordered.get(100).total());
            assertThat(activeCounts).as("활동 창 경계가 읽기 도중 지나가야 경계를 비교한 것이다")
                    .anySatisfy(count -> assertThat(count).isStrictlyBetween(0, size));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 150})
    void 시딩_뒤_기존_기기와_새_기기가_입력한_순위표에서_바로_읽은_화면도_같은_데이터의_전원_정렬_화면과_같다(int size) {
        // given
        Random random = new Random(20261111L + size);
        List<UUID> participants = new ArrayList<>(무작위_참가자를_시딩한다(random, size));
        store.markSeeded(today);
        for (UUID participant : participants) {
            if (random.nextInt(3) == 0) {
                store.write(participant, today, 무작위_입력(random), UUID.randomUUID());
            }
        }
        for (int count = 0; count < 3; count++) {
            UUID newcomer = UUID.randomUUID();
            store.write(newcomer, today, 무작위_입력(random), UUID.randomUUID());
            participants.add(newcomer);
        }
        닉네임_사본을_채운다(random, participants);

        // when
        모든_요청자의_화면이_전원_정렬_화면과_같다(participants);

        // then
        assertThat(redis.opsForZSet().zCard("{press}:rank:" + today)).isEqualTo(participants.size());
    }

    @Test
    void JVM_시계가_어제여도_Redis_날짜의_화면을_읽고_어제_키는_만들지_않는다() {
        // given
        LocalDate yesterday = today.minusDays(1);
        Clock yesterdayClock = Clock.fixed(yesterday.atTime(12, 0).atZone(KST).toInstant(), KST);
        UUID device = UUID.randomUUID();
        store.markSeeded(today);
        store.write(device, today, Map.of(EmotionState.ANGRY, 2), UUID.randomUUID());

        // when
        PressScreen screen = 기본_폴링(리더(store, yesterdayClock)).findSnapshot(device, PressView.PRESS);

        // then
        assertThat(screen.date()).isEqualTo(today);
        assertThat(screen.me().score()).isEqualTo(2);
        assertThat(redis.hasKey("{press}:seeded:" + yesterday)).isFalse();
    }

    @Test
    void 시딩_표시가_없으면_DB의_오늘_값을_시딩한_뒤_읽는다() {
        // given
        Device device = 기기("n" + UUID.randomUUID().toString().substring(0, 8));
        pressRedisFlushWriter.write(List.of(하루_기록(device.getPublicId(), today, Map.of(EmotionState.ANGRY, 4L),
                Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MILLIS), 1)));

        // when
        PressScreen screen = 기본_폴링(리더(store, Clock.system(KST))).findSnapshot(device.getPublicId(), PressView.ALL);

        // then
        assertThat(screen.me().score()).isEqualTo(4);
        assertThat(redis.hasKey("{press}:seeded:" + today)).isTrue();
    }

    @Test
    void 세_번_읽어도_시딩_표시가_보이지_않으면_STORE_UNAVAILABLE로_실패한다() {
        // given: 시더가 다른 Redis 데이터베이스에 시딩 표시를 남겨 읽기 쪽은 매번 시딩 전으로 본다.
        LettuceConnectionFactory otherDatabase = 연결(1);
        PressRedisStore elsewhere = new PressRedisStore(new StringRedisTemplate(otherDatabase));
        PressRedisScreenReader reader = new PressRedisScreenReader(store,
                new PressRedisSeeder(pressBufferRepository, elsewhere, Clock.system(KST)), 보드(store), Clock.system(KST));

        try {
            // when, then
            assertThatThrownBy(() -> 기본_폴링(reader).findSnapshot(UUID.randomUUID(), PressView.PRESS))
                    .isInstanceOfSatisfying(PressRaceException.class, exception -> {
                        assertThat(exception.code()).isEqualTo("STORE_UNAVAILABLE");
                        assertThat(exception.retryAfterSeconds()).isEqualTo(1);
                    });
            assertThat(redis.hasKey("{press}:seeded:" + today)).isFalse();
        } finally {
            otherDatabase.destroy();
        }
    }

    @Test
    void Redis에_닿지_않으면_보드에_최근_관찰이_있어도_유예_없이_STORE_UNAVAILABLE로_실패한다() {
        // given
        store.markSeeded(today);
        UUID device = UUID.randomUUID();
        store.write(device, today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());
        PressRedisTodayBoard board = 보드(store);
        PressRedisTodayBoardFixture.틱(board);
        LettuceConnectionFactory closed = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1));
        closed.afterPropertiesSet();
        closed.start();
        PressRedisStore unreachable = new PressRedisStore(new StringRedisTemplate(closed));
        PressRedisScreenReader reader = new PressRedisScreenReader(unreachable,
                new PressRedisSeeder(pressBufferRepository, unreachable, Clock.system(KST)), board, Clock.system(KST));

        try {
            // when, then
            assertThat(board.current().participants()).extracting(PressTodayParticipant::devicePublicId)
                    .containsExactly(device);
            assertThatThrownBy(() -> 기본_폴링(reader).findSnapshot(device, PressView.PRESS))
                    .isInstanceOfSatisfying(PressRaceException.class, exception -> {
                        assertThat(exception.code()).isEqualTo("STORE_UNAVAILABLE");
                        assertThat(exception.retryAfterSeconds()).isEqualTo(1);
                    });
        } finally {
            closed.destroy();
        }
    }

    @Test
    void 닉네임_사본이_없으면_보드가_마지막으로_본_닉네임을_쓴다() {
        // given: DB 에 닿지 않는 보드라 사본 채우기는 실패하고 보드의 마지막 관찰만 남는다.
        PressBufferRepository unreachable = new PressBufferRepository(
                JdbcClient.create(new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:1/none")));
        UUID named = UUID.randomUUID();
        UUID newcomer = UUID.randomUUID();
        store.markSeeded(today);
        store.write(named, today, Map.of(EmotionState.ANGRY, 2), UUID.randomUUID());
        store.fillNicknames(Map.of(named, "별"));
        PressRedisTodayBoard board = PressRedisTodayBoardFixture.보드(store, unreachable, Clock.system(KST),
                new SimpleMeterRegistry(), ticker);
        PressRedisTodayBoardFixture.틱(board);
        redis.delete("{press}:nickname:" + named);
        store.write(newcomer, today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());
        PressRedisScreenReader reader = new PressRedisScreenReader(store,
                new PressRedisSeeder(unreachable, store, Clock.system(KST)), board, Clock.system(KST));

        // when
        PressScreen screen = 기본_폴링(reader).findSnapshot(newcomer, PressView.PRESS);

        // then
        assertThat(screen.nearby()).extracting(PressRanker::score, PressRanker::nickname)
                .containsExactly(tuple(2L, "별"), tuple(1L, "익명"));
    }

    @Test
    void 닉네임_사본이_없고_보드도_모르면_익명이고_채우기가_끝난_다음_읽기부터_DB_닉네임이다() {
        // given
        String nickname = "n" + UUID.randomUUID().toString().substring(0, 8);
        Device device = 기기(nickname);
        store.markSeeded(today);
        store.write(device.getPublicId(), today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());
        PressSnapshotService service = 기본_폴링(리더(store, Clock.system(KST)));

        // when
        PressScreen first = service.findSnapshot(device.getPublicId(), PressView.PRESS);
        PressScreen second = service.findSnapshot(device.getPublicId(), PressView.PRESS);

        // then
        assertThat(first.me().nickname()).isEqualTo("익명");
        assertThat(second.me().nickname()).isEqualTo(nickname);
        assertThat(redis.opsForValue().get("{press}:nickname:" + device.getPublicId())).isEqualTo(nickname);
    }

    private List<Integer> 모든_요청자의_화면이_전원_정렬_화면과_같다(List<UUID> participants) {
        PressSnapshotService service = 기본_폴링(리더(store, Clock.system(KST)));
        List<PressParticipant> all = 오늘_정렬_순서().stream().map(this::참가자).toList();
        List<UUID> requesters = new ArrayList<>(participants);
        requesters.add(UUID.randomUUID());
        List<Integer> activeCounts = new ArrayList<>();
        for (UUID requester : requesters) {
            for (PressView view : PressView.values()) {
                PressScreen actual = service.findSnapshot(requester, view);
                PressScreen expected = PressSnapshot.of(today, actual.generatedAt(), all).findScreen(requester, view);
                assertThat(actual).as("요청자 %d번째, 화면 %s", requesters.indexOf(requester), view).isEqualTo(expected);
                activeCounts.add(actual.activeCount());
            }
        }
        return activeCounts;
    }

    private List<UUID> 무작위_참가자를_시딩한다(Random random, int size) {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        List<UUID> publicIds = new ArrayList<>();
        List<Long> totals = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            publicIds.add(UUID.randomUUID());
            totals.add(1L + random.nextInt(6));
        }
        if (size > 100) {
            백번째와_백한번째를_동점으로_만든다(publicIds, totals);
        }
        List<UUID> devices = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            UUID device = UUID.randomUUID();
            Instant lastAppliedAt = base.minusMillis(2_300 + random.nextInt(3_001));
            store.seedDay(PressStoredDay.of(device, today, 감정_개수(random, totals.get(index)), totals.get(index),
                    lastAppliedAt, publicIds.get(index), 1 + random.nextInt(5)));
            devices.add(device);
        }
        return devices;
    }

    private void 백번째와_백한번째를_동점으로_만든다(List<UUID> publicIds, List<Long> totals) {
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < totals.size(); index++) {
            order.add(index);
        }
        order.sort(Comparator.<Integer>comparingLong(totals::get).reversed()
                .thenComparing(index -> publicIds.get(index).toString()));
        totals.set(order.get(100), totals.get(order.get(99)));
    }

    private Map<EmotionState, Long> 감정_개수(Random random, long total) {
        Map<EmotionState, Long> counts = new EnumMap<>(EmotionState.class);
        for (long unit = 0; unit < total; unit++) {
            counts.merge(STATES[random.nextInt(STATES.length)], 1L, Long::sum);
        }
        return counts;
    }

    private Map<EmotionState, Integer> 무작위_입력(Random random) {
        Map<EmotionState, Integer> counts = new EnumMap<>(EmotionState.class);
        감정_개수(random, 1 + random.nextInt(6)).forEach((state, count) -> counts.put(state, count.intValue()));
        return counts;
    }

    private void 닉네임_사본을_채운다(Random random, List<UUID> participants) {
        Map<UUID, String> nicknames = new HashMap<>();
        for (int index = 0; index < participants.size(); index++) {
            nicknames.put(participants.get(index), random.nextInt(4) == 0 ? "" : "별" + index);
        }
        store.fillNicknames(nicknames);
    }

    private List<PressTodayParticipant> 오늘_정렬_순서() {
        return store.readToday(today, "", -1, -1).participants().stream()
                .sorted(Comparator.comparingLong(PressTodayParticipant::total).reversed()
                        .thenComparing(participant -> participant.publicId().toString()))
                .toList();
    }

    private PressParticipant 참가자(PressTodayParticipant participant) {
        return PressParticipant.of(participant.devicePublicId(), participant.publicId(), participant.nickname(),
                participant.total(), participant.lastAppliedAt(), participant.representativeState());
    }

    private PressSnapshotService 기본_폴링(PressRedisScreenReader reader) {
        return new PressSnapshotService(new PressDatabaseSnapshotService(new PressSnapshotRepository(jdbc)),
                Optional.empty(), Optional.of(reader));
    }

    private PressRedisScreenReader 리더(PressRedisStore readStore, Clock clock) {
        return new PressRedisScreenReader(readStore, new PressRedisSeeder(pressBufferRepository, readStore, clock),
                보드(readStore), clock);
    }

    private PressRedisTodayBoard 보드(PressRedisStore boardStore) {
        return PressRedisTodayBoardFixture.보드(boardStore, pressBufferRepository, Clock.system(KST),
                new SimpleMeterRegistry(), ticker);
    }

    private Device 기기(String nickname) {
        Device device = deviceRepository.saveAndFlush(기본_기기_빌더().nickname(nickname).build());
        devices.add(device);
        return device;
    }

    private static LettuceConnectionFactory 연결(int database) {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(REDIS.getHost(),
                REDIS.getMappedPort(6379));
        configuration.setDatabase(database);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }
}
