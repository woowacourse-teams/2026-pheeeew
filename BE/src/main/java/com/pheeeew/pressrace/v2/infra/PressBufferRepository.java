package com.pheeeew.pressrace.v2.infra;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.pressrace.v2.domain.PressDaySnapshot;
import com.pheeeew.pressrace.v2.domain.PressStoredDay;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class PressBufferRepository {

    private final JdbcClient jdbc;

    public void save(PressDaySnapshot snapshot) {
        OffsetDateTime appliedAt = snapshot.lastAppliedAt().atOffset(ZoneOffset.UTC);
        saveDailyPresses(snapshot.deviceId(), snapshot.date(), snapshot.counts(), appliedAt);
        jdbc.sql("""
                INSERT INTO press_daily_scores(device_id, press_date, total, last_applied_at, public_id)
                VALUES (:device, :date, :total, :appliedAt, :publicId)
                ON CONFLICT (device_id, press_date) DO UPDATE
                  SET total = EXCLUDED.total, last_applied_at = EXCLUDED.last_applied_at
                """).param("device", snapshot.deviceId()).param("date", snapshot.date())
                .param("total", snapshot.total()).param("appliedAt", appliedAt)
                .param("publicId", UUID.randomUUID()).update();
    }

    public Map<UUID, Long> findDeviceIds(Collection<UUID> devicePublicIds) {
        if (devicePublicIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> ids = new HashMap<>();
        jdbc.sql("SELECT id, public_id FROM devices WHERE public_id IN (:ids)")
                .param("ids", devicePublicIds)
                .query((row, index) -> ids.put(row.getObject("public_id", UUID.class), row.getLong("id")))
                .list();
        return ids;
    }

    public Map<UUID, String> findNicknames(Collection<UUID> devicePublicIds) {
        if (devicePublicIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> nicknames = new HashMap<>();
        jdbc.sql("SELECT public_id, nickname FROM devices WHERE public_id IN (:ids)")
                .param("ids", devicePublicIds)
                .query((row, index) -> nicknames.put(row.getObject("public_id", UUID.class), row.getString("nickname")))
                .list();
        return nicknames;
    }

    public boolean saveVersioned(long deviceId, PressStoredDay day) {
        OffsetDateTime appliedAt = day.lastAppliedAt().atOffset(ZoneOffset.UTC);
        boolean accepted = jdbc.sql("""
                INSERT INTO press_daily_scores(device_id, press_date, total, last_applied_at, public_id, source_version)
                VALUES (:device, :date, :total, :appliedAt, :publicId, :version)
                ON CONFLICT (device_id, press_date) DO UPDATE
                  SET total = EXCLUDED.total, last_applied_at = EXCLUDED.last_applied_at,
                      source_version = EXCLUDED.source_version
                  WHERE press_daily_scores.source_version < EXCLUDED.source_version
                RETURNING device_id
                """).param("device", deviceId).param("date", day.date()).param("total", day.total())
                .param("appliedAt", appliedAt).param("publicId", day.publicId()).param("version", day.version())
                .query(Long.class).optional().isPresent();
        if (accepted) {
            saveDailyPresses(deviceId, day.date(), day.counts(), appliedAt);
        }
        return accepted;
    }

    public List<PressStoredDay> findStoredDays(LocalDate date) {
        Map<Long, Map<EmotionState, Long>> counts = new HashMap<>();
        jdbc.sql("SELECT device_id, state, press_count FROM device_daily_presses WHERE press_date = :date")
                .param("date", date)
                .query((row, index) -> counts.computeIfAbsent(row.getLong("device_id"), ignored -> new EnumMap<>(EmotionState.class))
                        .put(EmotionState.valueOf(row.getString("state")), row.getLong("press_count")))
                .list();
        return jdbc.sql("""
                SELECT s.device_id, d.public_id AS device_public_id, s.total, s.last_applied_at, s.public_id, s.source_version
                FROM press_daily_scores s JOIN devices d ON d.id = s.device_id
                WHERE s.press_date = :date
                """).param("date", date)
                .query((row, index) -> PressStoredDay.of(
                        row.getObject("device_public_id", UUID.class),
                        date,
                        Map.copyOf(counts.getOrDefault(row.getLong("device_id"), Map.of())),
                        row.getLong("total"),
                        row.getObject("last_applied_at", OffsetDateTime.class).toInstant(),
                        row.getObject("public_id", UUID.class),
                        row.getLong("source_version")))
                .list();
    }

    private void saveDailyPresses(long deviceId, LocalDate date, Map<EmotionState, Long> counts, OffsetDateTime appliedAt) {
        counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(EmotionState::name)))
                .forEach(entry -> jdbc.sql("""
                        INSERT INTO device_daily_presses (press_date, state, device_id, press_count, created_at, updated_at)
                        VALUES (:date, :state, :device, :count, :appliedAt, :appliedAt)
                        ON CONFLICT (press_date, state, device_id) DO UPDATE
                          SET press_count = EXCLUDED.press_count, updated_at = EXCLUDED.updated_at
                        """).param("date", date).param("state", entry.getKey().name())
                        .param("device", deviceId).param("count", entry.getValue())
                        .param("appliedAt", appliedAt).update());
    }
}
