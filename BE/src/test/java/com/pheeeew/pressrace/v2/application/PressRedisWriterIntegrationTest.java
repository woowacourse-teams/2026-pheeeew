package com.pheeeew.pressrace.v2.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.PressCounts;
import com.pheeeew.emotion.domain.repository.DeviceDailyPressRepository;
import com.pheeeew.pressrace.application.PressRaceService;
import com.pheeeew.pressrace.exception.PressRaceException;
import com.pheeeew.pressrace.infra.PressRaceRepository;
import com.pheeeew.pressrace.v2.domain.PressStoredDay;
import com.pheeeew.pressrace.v2.infra.PressBufferRepository;
import com.pheeeew.pressrace.v2.infra.PressRedisStore;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PressRedisWriterIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
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
    private DeviceDailyPressRepository deviceDailyPressRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private JdbcClient jdbc;

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
        store = new PressRedisStore(redis);
        today = LocalDate.now(KST);
    }

    @AfterEach
    void tearDown() {
        jdbc.sql("DELETE FROM device_daily_presses").update();
        jdbc.sql("DELETE FROM press_daily_scores").update();
        deviceRepository.deleteAllInBatch();
        jdbc.sql("UPDATE press_race_control SET active_server_generation = NULL").update();
    }

    @Test
    void 그날_시딩_표시가_없으면_DB의_오늘_값을_채운_뒤_이어서_센다() {
        // given
        Device device = 기기를_저장한다();
        pressRedisFlushWriter.write(List.of(PressStoredDay.of(device.getPublicId(), today, Map.of(EmotionState.ANGRY, 10L), 10,
                Instant.now(), UUID.randomUUID(), 4)));

        // when
        var counts = 작성기(Clock.system(KST)).press(device.getPublicId(), PressCounts.from(Map.of(EmotionState.ANGRY, 1)));

        // then
        assertThat(counts).containsEntry(EmotionState.ANGRY, 11L);
        assertThat(store.findDirty()).singleElement().satisfies(day -> assertThat(day.version()).isEqualTo(5L));
    }

    @Test
    void 서버_날짜가_Redis_날짜와_다르면_Redis_날짜로_다시_써서_반영한다() {
        // given
        Device device = 기기를_저장한다();
        Clock yesterday = Clock.fixed(today.minusDays(1).atTime(12, 0).atZone(KST).toInstant(), KST);

        // when
        var counts = 작성기(yesterday).press(device.getPublicId(), PressCounts.from(Map.of(EmotionState.ANGRY, 2)));

        // then
        assertThat(counts).containsEntry(EmotionState.ANGRY, 2L);
        assertThat(redis.opsForHash().get("{press}:device:" + today + ":" + device.getPublicId(), "total")).isEqualTo("2");
    }

    @Test
    void 같은_요청_식별자로_다시_보내면_한_번만_반영하고_오늘_집계를_돌려준다() {
        // given
        Device device = 기기를_저장한다();
        UUID requestId = UUID.randomUUID();
        var writer = 작성기(Clock.system(KST));
        writer.press(device.getPublicId(), PressCounts.from(Map.of(EmotionState.ANGRY, 3)), requestId);

        // when
        var counts = writer.press(device.getPublicId(), PressCounts.from(Map.of(EmotionState.ANGRY, 3)), requestId);

        // then
        assertThat(counts).containsEntry(EmotionState.ANGRY, 3L);
        assertThat(redis.opsForHash().get("{press}:device:" + today + ":" + device.getPublicId(), "total")).isEqualTo("3");
    }

    @Test
    void 같은_요청_식별자에_다른_횟수를_보내면_거절하고_반영하지_않는다() {
        // given
        Device device = 기기를_저장한다();
        UUID requestId = UUID.randomUUID();
        var writer = 작성기(Clock.system(KST));
        writer.press(device.getPublicId(), PressCounts.from(Map.of(EmotionState.ANGRY, 3)), requestId);

        // when, then
        assertThatThrownBy(() -> writer.press(device.getPublicId(), PressCounts.from(Map.of(EmotionState.ANGRY, 4)), requestId))
                .isInstanceOfSatisfying(PressRaceException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INPUT_CONFLICT"));
        assertThat(redis.opsForHash().get("{press}:device:" + today + ":" + device.getPublicId(), "total")).isEqualTo("3");
    }

    @Test
    void 기기가_없으면_Redis에_쓰기_전에_기기_오류를_낸다() {
        // when, then
        assertThatThrownBy(() -> 작성기(Clock.system(KST)).press(UUID.randomUUID(), PressCounts.from(Map.of(EmotionState.ANGRY, 1))))
                .isInstanceOf(DeviceException.class);
        assertThat(redis.keys("{press}:*")).isEmpty();
    }

    @Test
    void Redis에_연결할_수_없으면_DB로_우회하지_않고_저장소_사용_불가로_거절한다() {
        // given
        Device device = 기기를_저장한다();
        var unreachable = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1));
        unreachable.afterPropertiesSet();
        unreachable.start();
        var unreachableStore = new PressRedisStore(new StringRedisTemplate(unreachable));
        var writer = new PressRedisWriter(unreachableStore,
                new PressRedisSeeder(pressBufferRepository, unreachableStore, Clock.system(KST)),
                pressRaceService, deviceRepository, Clock.system(KST));

        // when, then
        try {
            assertThatThrownBy(() -> writer.press(device.getPublicId(), PressCounts.from(Map.of(EmotionState.ANGRY, 1))))
                    .isInstanceOfSatisfying(PressRaceException.class,
                            exception -> assertThat(exception.code()).isEqualTo("STORE_UNAVAILABLE"));
        } finally {
            unreachable.destroy();
        }
    }

    private PressRedisWriter 작성기(Clock clock) {
        return new PressRedisWriter(store, new PressRedisSeeder(pressBufferRepository, store, clock),
                pressRaceService, deviceRepository, clock);
    }

    private Device 기기를_저장한다() {
        return deviceRepository.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
    }
}
