package com.pheeeew.notification.domain.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class NotificationRankRoundRepository {

    private final JdbcClient jdbc;

    public boolean saveRound(Instant roundAt, LocalDate targetDate, Instant now) {
        return jdbc.sql("""
                INSERT INTO notification_rank_rounds (round_at, target_date, created_at)
                     VALUES (:roundAt, :targetDate, :now)
                ON CONFLICT DO NOTHING
                """).param("roundAt", utc(roundAt))
                .param("targetDate", targetDate)
                .param("now", utc(now))
                .update() == 1;
    }

    public Map<Long, Integer> findPreviousSnapshot(LocalDate targetDate, Instant roundAt) {
        return jdbc.sql("""
                SELECT device_id, rank
                  FROM notification_rank_snapshots
                 WHERE round_at = (SELECT MAX(round_at) FROM notification_rank_rounds
                                    WHERE target_date = :targetDate AND round_at < :roundAt)
                """).param("targetDate", targetDate)
                .param("roundAt", utc(roundAt))
                .query((row, rowNumber) -> Map.entry(row.getLong("device_id"), row.getInt("rank")))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void saveSnapshot(Instant roundAt, Long deviceId, int rank) {
        jdbc.sql("""
                INSERT INTO notification_rank_snapshots (round_at, device_id, rank)
                     VALUES (:roundAt, :deviceId, :rank)
                """).param("roundAt", utc(roundAt))
                .param("deviceId", deviceId)
                .param("rank", rank)
                .update();
    }

    public int deleteByTargetDateBefore(LocalDate date) {
        return jdbc.sql("DELETE FROM notification_rank_rounds WHERE target_date < :date")
                .param("date", date)
                .update();
    }

    private OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
