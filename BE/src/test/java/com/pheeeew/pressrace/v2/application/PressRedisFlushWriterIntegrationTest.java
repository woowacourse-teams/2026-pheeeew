package com.pheeeew.pressrace.v2.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.pressrace.v2.domain.PressStoredDay;
import com.pheeeew.pressrace.v2.infra.PressBufferRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@PostgisDataJpaTest
class PressRedisFlushWriterIntegrationTest {

    private static final LocalDate DATE = LocalDate.parse("2026-11-09");
    private static final Instant APPLIED_AT = Instant.parse("2026-11-09T03:00:00Z");

    @Autowired
    private PressRedisFlushWriter pressRedisFlushWriter;
    @Autowired
    private PressBufferRepository pressBufferRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private JdbcClient jdbc;

    @Test
    void 처음_반영하면_합계_행과_감정_행을_누적값과_버전으로_만든다() {
        // given
        Device device = 기기를_저장한다();

        // when
        int accepted = pressRedisFlushWriter.write(List.of(하루(device, 3, Map.of(EmotionState.ANGRY, 5L, EmotionState.IRRITATED, 2L))));

        // then
        assertThat(accepted).isEqualTo(1);
        assertThat(합계(device)).isEqualTo(7L);
        assertThat(버전(device)).isEqualTo(3L);
        assertThat(감정_개수(device, EmotionState.ANGRY)).isEqualTo(5L);
        assertThat(감정_개수(device, EmotionState.IRRITATED)).isEqualTo(2L);
    }

    @Test
    void 더_높은_버전은_더하지_않고_누적값으로_덮어쓴다() {
        // given
        Device device = 기기를_저장한다();
        pressRedisFlushWriter.write(List.of(하루(device, 3, Map.of(EmotionState.ANGRY, 5L))));

        // when
        pressRedisFlushWriter.write(List.of(하루(device, 4, Map.of(EmotionState.ANGRY, 6L))));

        // then
        assertThat(합계(device)).isEqualTo(6L);
        assertThat(감정_개수(device, EmotionState.ANGRY)).isEqualTo(6L);
    }

    @Test
    void 같거나_낮은_버전은_합계_행과_감정_행을_바꾸지_않는다() {
        // given
        Device device = 기기를_저장한다();
        pressRedisFlushWriter.write(List.of(하루(device, 5, Map.of(EmotionState.ANGRY, 9L))));

        // when
        int accepted = pressRedisFlushWriter.write(List.of(
                하루(device, 4, Map.of(EmotionState.ANGRY, 1L)),
                하루(device, 5, Map.of(EmotionState.ANGRY, 2L))));

        // then
        assertThat(accepted).isZero();
        assertThat(합계(device)).isEqualTo(9L);
        assertThat(버전(device)).isEqualTo(5L);
        assertThat(감정_개수(device, EmotionState.ANGRY)).isEqualTo(9L);
    }

    @Test
    void DB에_없는_기기는_건너뛴다() {
        // given
        var unknown = PressStoredDay.of(UUID.randomUUID(), DATE, Map.of(EmotionState.ANGRY, 1L), 1, APPLIED_AT,
                UUID.randomUUID(), 1);

        // when
        int accepted = pressRedisFlushWriter.write(List.of(unknown));

        // then
        assertThat(accepted).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM press_daily_scores WHERE press_date = :date")
                .param("date", DATE).query(Long.class).single()).isZero();
    }

    @Test
    void 시딩용으로_그날의_합계와_감정별_개수와_버전을_읽는다() {
        // given
        Device device = 기기를_저장한다();
        var day = 하루(device, 7, Map.of(EmotionState.ANGRY, 4L, EmotionState.EXHAUSTED, 1L));
        pressRedisFlushWriter.write(List.of(day));

        // when
        var stored = pressBufferRepository.findStoredDays(DATE);

        // then
        assertThat(stored).singleElement().satisfies(found -> {
            assertThat(found.devicePublicId()).isEqualTo(device.getPublicId());
            assertThat(found.total()).isEqualTo(5L);
            assertThat(found.version()).isEqualTo(7L);
            assertThat(found.publicId()).isEqualTo(day.publicId());
            assertThat(found.counts()).containsExactlyInAnyOrderEntriesOf(Map.of(EmotionState.ANGRY, 4L, EmotionState.EXHAUSTED, 1L));
            assertThat(found.lastAppliedAt()).isEqualTo(APPLIED_AT.truncatedTo(ChronoUnit.MICROS));
        });
    }

    private Device 기기를_저장한다() {
        return deviceRepository.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
    }

    private PressStoredDay 하루(Device device, long version, Map<EmotionState, Long> counts) {
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return PressStoredDay.of(device.getPublicId(), DATE, counts, total, APPLIED_AT, UUID.randomUUID(), version);
    }

    private long 합계(Device device) {
        return jdbc.sql("SELECT total FROM press_daily_scores WHERE device_id = :device AND press_date = :date")
                .param("device", device.getId()).param("date", DATE).query(Long.class).single();
    }

    private long 버전(Device device) {
        return jdbc.sql("SELECT source_version FROM press_daily_scores WHERE device_id = :device AND press_date = :date")
                .param("device", device.getId()).param("date", DATE).query(Long.class).single();
    }

    private long 감정_개수(Device device, EmotionState state) {
        return jdbc.sql("""
                SELECT press_count FROM device_daily_presses
                WHERE device_id = :device AND press_date = :date AND state = :state
                """).param("device", device.getId()).param("date", DATE).param("state", state.name())
                .query(Long.class).single();
    }
}
