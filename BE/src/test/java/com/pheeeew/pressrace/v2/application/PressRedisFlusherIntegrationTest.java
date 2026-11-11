package com.pheeeew.pressrace.v2.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.pressrace.v2.domain.PressStoredDay;
import com.pheeeew.pressrace.v2.infra.PressRedisStore;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
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
class PressRedisFlusherIntegrationTest {

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connections;
    private static StringRedisTemplate redis;

    @Autowired
    private PressRedisFlushWriter pressRedisFlushWriter;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private JdbcClient jdbc;

    private PressRedisStore store;
    private PressRedisFlusher flusher;
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
        flusher = new PressRedisFlusher(store, pressRedisFlushWriter, new SimpleMeterRegistry());
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        store.markSeeded(today);
    }

    @Test
    void 반영하면_DB_누적값이_Redis와_같아지고_변경_표시가_비워진다() {
        // given
        Device device = 기기를_저장한다();
        store.write(device.getPublicId(), today, Map.of(EmotionState.ANGRY, 3), UUID.randomUUID());
        store.write(device.getPublicId(), today, Map.of(EmotionState.ANGRY, 2, EmotionState.IRRITATED, 1), UUID.randomUUID());

        // when
        flusher.flush();

        // then
        assertThat(합계(device)).isEqualTo(6L);
        assertThat(감정_개수(device, EmotionState.ANGRY)).isEqualTo(5L);
        assertThat(감정_개수(device, EmotionState.IRRITATED)).isEqualTo(1L);
        assertThat(store.findDirty()).isEmpty();
    }

    @Test
    void 반영_뒤_들어온_입력은_다음_반영에서_누적값으로_덮어쓴다() {
        // given
        Device device = 기기를_저장한다();
        store.write(device.getPublicId(), today, Map.of(EmotionState.ANGRY, 3), UUID.randomUUID());
        flusher.flush();
        store.write(device.getPublicId(), today, Map.of(EmotionState.ANGRY, 4), UUID.randomUUID());

        // when
        flusher.flush();

        // then
        assertThat(합계(device)).isEqualTo(7L);
        assertThat(감정_개수(device, EmotionState.ANGRY)).isEqualTo(7L);
    }

    @Test
    void DB에_없는_기기의_변경_표시는_반영하지_않고_비운다() {
        // given
        store.write(UUID.randomUUID(), today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());

        // when
        flusher.flush();

        // then
        assertThat(store.findDirty()).isEmpty();
    }

    @Test
    void 자정_전에_쌓인_지난_날짜의_변경도_그_날짜로_반영한다() {
        // given
        Device device = 기기를_저장한다();
        LocalDate yesterday = today.minusDays(1);
        store.seedDay(PressStoredDay.of(device.getPublicId(), yesterday, Map.of(EmotionState.ANGRY, 5L), 5,
                Instant.parse("2026-11-08T14:59:59Z"), UUID.randomUUID(), 2));
        redis.opsForZSet().add("{press}:dirty", yesterday + ":" + device.getPublicId(), 2);

        // when
        flusher.flush();

        // then
        assertThat(jdbc.sql("SELECT total FROM press_daily_scores WHERE device_id = :device AND press_date = :date")
                .param("device", device.getId()).param("date", yesterday).query(Long.class).single()).isEqualTo(5L);
        assertThat(store.findDirty()).isEmpty();
    }

    private Device 기기를_저장한다() {
        return deviceRepository.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
    }

    private long 합계(Device device) {
        return jdbc.sql("SELECT total FROM press_daily_scores WHERE device_id = :device AND press_date = :date")
                .param("device", device.getId()).param("date", today).query(Long.class).single();
    }

    private long 감정_개수(Device device, EmotionState state) {
        return jdbc.sql("""
                SELECT press_count FROM device_daily_presses
                WHERE device_id = :device AND press_date = :date AND state = :state
                """).param("device", device.getId()).param("date", today).param("state", state.name())
                .query(Long.class).single();
    }
}
