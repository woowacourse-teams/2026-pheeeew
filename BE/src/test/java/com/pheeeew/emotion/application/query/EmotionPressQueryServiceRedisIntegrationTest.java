package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.DeviceDailyPressFixture.개인_프레스를_저장한다;
import static com.pheeeew.pressrace.v2.fixture.PressTodayFixture.하루_기록;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.dto.EmotionPressDailyResult;
import com.pheeeew.emotion.application.dto.EmotionPressTotalResult;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.DeviceDailyPressRepository;
import com.pheeeew.pressrace.v2.application.PressRedisDayReader;
import com.pheeeew.pressrace.v2.application.PressRedisFlushWriter;
import com.pheeeew.pressrace.v2.application.PressRedisSeeder;
import com.pheeeew.pressrace.exception.PressRaceException;
import com.pheeeew.pressrace.v2.infra.PressBufferRepository;
import com.pheeeew.pressrace.v2.infra.PressRedisStore;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@PostgisDataJpaTest
class EmotionPressQueryServiceRedisIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connections;
    private static StringRedisTemplate redis;

    @Autowired
    private DeviceDailyPressRepository deviceDailyPressRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private PressBufferRepository pressBufferRepository;
    @Autowired
    private PressRedisFlushWriter pressRedisFlushWriter;
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

    @Test
    void 지난_날짜의_내_개수와_총합은_Redis가_아니라_DB에서_읽는다() {
        // given
        Device device = 기기를_저장한다();
        LocalDate yesterday = today.minusDays(1);
        개인_프레스를_저장한다(jdbc, device.getId(), yesterday, EmotionState.ANGRY, 4);
        store.seedDay(하루_기록(device.getPublicId(), yesterday, Map.of(EmotionState.ANGRY, 9L), Instant.now(), 1));
        store.markSeeded(yesterday);
        EmotionPressQueryService service = 조회_서비스(store, Clock.system(KST));

        // when
        EmotionPressDailyResult mine = service.findMyDailyPresses(device.getPublicId(), 1);
        EmotionPressTotalResult total = service.findDailyTotal(1);

        // then
        assertThat(mine.pressDate()).isEqualTo(yesterday);
        assertThat(mine.counts()).containsEntry(EmotionState.ANGRY, 4L);
        assertThat(total.pressDate()).isEqualTo(yesterday);
        assertThat(total.total()).isEqualTo(deviceDailyPressRepository.sumByPressDate(yesterday));
    }

    @Test
    void 시딩_표시가_없으면_DB의_오늘_값을_시딩한_뒤_내_개수와_총합을_Redis에서_읽는다() {
        // given
        Device device = 기기를_저장한다();
        pressRedisFlushWriter.write(List.of(하루_기록(device.getPublicId(), today,
                Map.of(EmotionState.ANGRY, 3L, EmotionState.EXHAUSTED, 1L),
                Instant.now().minus(Duration.ofMinutes(1)).truncatedTo(ChronoUnit.MILLIS), 2)));
        EmotionPressQueryService service = 조회_서비스(store, Clock.system(KST));

        // when
        EmotionPressDailyResult mine = service.findMyDailyPresses(device.getPublicId(), 0);
        EmotionPressTotalResult total = service.findDailyTotal(0);

        // then
        assertThat(mine.pressDate()).isEqualTo(today);
        assertThat(mine.counts()).containsEntry(EmotionState.ANGRY, 3L).containsEntry(EmotionState.EXHAUSTED, 1L)
                .containsEntry(EmotionState.IRRITATED, 0L).hasSize(EmotionState.values().length);
        assertThat(total.total()).isEqualTo(deviceDailyPressRepository.sumByPressDate(today));
        assertThat(redis.hasKey("{press}:seeded:" + today)).isTrue();
    }

    @Test
    void 서버_날짜가_Redis_날짜보다_하루_늦어도_Redis_날짜의_내_개수와_총합을_돌려준다() {
        // given
        Device device = 기기를_저장한다();
        Clock yesterday = Clock.fixed(today.minusDays(1).atTime(12, 0).atZone(KST).toInstant(), KST);
        store.markSeeded(today);
        store.write(device.getPublicId(), today, Map.of(EmotionState.ANGRY, 2), UUID.randomUUID());
        EmotionPressQueryService service = 조회_서비스(store, yesterday);

        // when
        EmotionPressDailyResult mine = service.findMyDailyPresses(device.getPublicId(), 0);
        EmotionPressTotalResult total = service.findDailyTotal(0);

        // then
        assertThat(mine.pressDate()).isEqualTo(today);
        assertThat(mine.counts()).containsEntry(EmotionState.ANGRY, 2L);
        assertThat(total.pressDate()).isEqualTo(today);
        assertThat(total.total()).isEqualTo(2L);
    }

    @Test
    void Redis에_연결할_수_없으면_DB로_우회하지_않고_오늘_조회를_저장소_사용_불가로_거절한다() {
        // given
        Device device = 기기를_저장한다();
        개인_프레스를_저장한다(jdbc, device.getId(), today, EmotionState.ANGRY, 4);
        LettuceConnectionFactory unreachable = 연결할_수_없는_Redis();
        EmotionPressQueryService service = 조회_서비스(new PressRedisStore(new StringRedisTemplate(unreachable)),
                Clock.system(KST));

        // when, then
        try {
            assertThatThrownBy(() -> service.findMyDailyPresses(device.getPublicId(), 0))
                    .isInstanceOfSatisfying(PressRaceException.class, exception -> {
                        assertThat(exception.code()).isEqualTo("STORE_UNAVAILABLE");
                        assertThat(exception.retryAfterSeconds()).isEqualTo(1);
                    });
            assertThatThrownBy(() -> service.findDailyTotal(0))
                    .isInstanceOfSatisfying(PressRaceException.class,
                            exception -> assertThat(exception.code()).isEqualTo("STORE_UNAVAILABLE"));
        } finally {
            unreachable.destroy();
        }
    }

    @Test
    void 없는_기기의_오늘_개수는_Redis를_읽기_전에_기기_오류로_거절한다() {
        // given
        LettuceConnectionFactory unreachable = 연결할_수_없는_Redis();
        EmotionPressQueryService service = 조회_서비스(new PressRedisStore(new StringRedisTemplate(unreachable)),
                Clock.system(KST));

        // when, then
        try {
            assertThatThrownBy(() -> service.findMyDailyPresses(UUID.randomUUID(), 0))
                    .isInstanceOf(DeviceException.class);
        } finally {
            unreachable.destroy();
        }
    }

    private EmotionPressQueryService 조회_서비스(PressRedisStore pressRedisStore, Clock clock) {
        PressRedisDayReader reader = new PressRedisDayReader(pressRedisStore,
                new PressRedisSeeder(pressBufferRepository, pressRedisStore, clock), clock);
        return new EmotionPressQueryService(deviceDailyPressRepository, deviceRepository, clock, Optional.of(reader));
    }

    private LettuceConnectionFactory 연결할_수_없는_Redis() {
        LettuceConnectionFactory unreachable = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1));
        unreachable.afterPropertiesSet();
        unreachable.start();
        return unreachable;
    }

    private Device 기기를_저장한다() {
        return deviceRepository.saveAndFlush(기본_기기_빌더().build());
    }
}
