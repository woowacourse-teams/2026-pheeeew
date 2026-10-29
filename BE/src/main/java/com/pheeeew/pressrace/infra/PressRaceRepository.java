package com.pheeeew.pressrace.infra;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.DeviceDailyPressRepository;
import com.pheeeew.pressrace.exception.PressRaceException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class PressRaceRepository {

    private final JdbcClient jdbc;
    private final DeviceDailyPressRepository presses;

    /** 일반 입력끼리는 병렬이고, 활성 서버 변경 UPDATE만 커밋까지 기다린다. 잠금 승격은 하지 않는다. */
    public Map<String, Object> lockServerFence() {
        return jdbc.sql("SELECT * FROM press_race_control WHERE id = TRUE FOR SHARE").query().singleRow();
    }

    public long deviceId(UUID publicId) {
        return jdbc.sql("SELECT id FROM devices WHERE public_id = :id").param("id", publicId)
                .query(Long.class).optional().orElseThrow(() -> new PressRaceException("DEVICE_NOT_FOUND"));
    }

    public Map<String, Object> lockDevice(long deviceId) {
        jdbc.sql("INSERT INTO press_device_controls(device_id) VALUES (:id) ON CONFLICT DO NOTHING")
                .param("id", deviceId).update();
        return jdbc.sql("SELECT * FROM press_device_controls WHERE device_id = :id FOR UPDATE")
                .param("id", deviceId).query().singleRow();
    }

    public Instant now() {
        return jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single().toInstant();
    }

    public Optional<Map<String, Object>> input(long deviceId, UUID requestId) {
        return jdbc.sql("SELECT * FROM press_inputs WHERE device_id = :device AND request_id = :request FOR UPDATE")
                .param("device", deviceId).param("request", requestId).query().listOfRows().stream().findFirst();
    }

    public void receive(long deviceId, UUID requestId, String normalized, String hash, UUID generation, Instant now) {
        jdbc.sql("""
                INSERT INTO press_inputs(device_id, request_id, normalized_counts, content_hash,
                    first_received_at, original_generation, status)
                VALUES (:device, :request, :counts, :hash, :now, :generation, 'PENDING')
                """).param("device", deviceId).param("request", requestId).param("counts", normalized)
                .param("hash", hash).param("now", now.atOffset(java.time.ZoneOffset.UTC))
                .param("generation", generation).update();
    }

    public void connection(long deviceId, UUID generation, boolean ready) {
        jdbc.sql("UPDATE press_device_controls SET connection_generation = :generation, ready = :ready WHERE device_id = :device")
                .param("generation", generation).param("ready", ready).param("device", deviceId).update();
    }

    public void recover(long deviceId, UUID generation, Instant now) {
        jdbc.sql("""
                UPDATE press_inputs SET status = 'REJECTED', rejection_code = 'NOT_APPLIED_ON_RECOVERY',
                    recovered_by_generation = :generation, finalized_at = :now
                WHERE device_id = :device AND status = 'PENDING' AND original_generation <> :generation
                """).param("generation", generation).param("now", now.atOffset(java.time.ZoneOffset.UTC))
                .param("device", deviceId).update();
    }

    public void tombstone(long deviceId, UUID requestId, String normalized, String hash, UUID generation, Instant now) {
        jdbc.sql("""
                INSERT INTO press_inputs(device_id, request_id, normalized_counts, content_hash, status,
                    rejection_code, recovered_by_generation, finalized_at)
                VALUES (:device, :request, :counts, :hash, 'REJECTED', 'NOT_APPLIED_ON_RECOVERY', :generation, :now)
                """).param("device", deviceId).param("request", requestId).param("counts", normalized)
                .param("hash", hash).param("generation", generation)
                .param("now", now.atOffset(java.time.ZoneOffset.UTC)).update();
    }

    public void reject(long deviceId, UUID requestId, String code, Instant now) {
        jdbc.sql("""
                UPDATE press_inputs SET status = 'REJECTED', rejection_code = :code, finalized_at = :now
                WHERE device_id = :device AND request_id = :request AND status = 'PENDING'
                """).param("code", code).param("now", now.atOffset(java.time.ZoneOffset.UTC))
                .param("device", deviceId).param("request", requestId).update();
    }

    public boolean consume(long deviceId, Map<String, Object> device, int count, Instant now) {
        double tokens = ((Number) device.get("tokens")).doubleValue();
        double elapsed = Math.max(0, java.time.Duration.between(instant(device.get("refilled_at")), now).toNanos() / 1e9);
        double available = Math.min(100, tokens + elapsed * 20);
        if (available < count) {
            return false;
        }
        jdbc.sql("UPDATE press_device_controls SET tokens = :tokens, refilled_at = :now WHERE device_id = :device")
                .param("tokens", available - count).param("now", now.atOffset(java.time.ZoneOffset.UTC))
                .param("device", deviceId).update();
        return true;
    }

    public void project(long deviceId, LocalDate date, Instant now) {
        jdbc.sql("""
                INSERT INTO press_daily_scores(device_id, press_date, total, last_applied_at, public_id)
                SELECT :device, :date, SUM(press_count), :now, :publicId FROM device_daily_presses
                WHERE device_id = :device AND press_date = :date
                ON CONFLICT (device_id, press_date) DO UPDATE
                  SET total = EXCLUDED.total, last_applied_at = EXCLUDED.last_applied_at
                """).param("device", deviceId).param("date", date)
                .param("now", now.atOffset(java.time.ZoneOffset.UTC)).param("publicId", UUID.randomUUID()).update();
    }

    public void applied(long deviceId, UUID requestId, LocalDate date, Instant now) {
        jdbc.sql("""
                UPDATE press_inputs SET status = 'APPLIED', applied_date = :date,
                    finalized_at = :now WHERE device_id = :device AND request_id = :request
                """).param("date", date).param("now", now.atOffset(java.time.ZoneOffset.UTC))
                .param("device", deviceId).param("request", requestId).update();
    }

    public Map<EmotionState, Long> counts(long deviceId, LocalDate date) {
        Map<EmotionState, Long> counts = new EnumMap<>(EmotionState.class);
        for (EmotionState state : EmotionState.values()) {
            counts.put(state, 0L);
        }
        presses.findByPressDateAndDeviceId(date, deviceId)
                .forEach(press -> counts.put(press.state(), press.pressCount()));
        return Map.copyOf(counts);
    }

    public static Instant instant(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return ((OffsetDateTime) value).toInstant();
    }
}
