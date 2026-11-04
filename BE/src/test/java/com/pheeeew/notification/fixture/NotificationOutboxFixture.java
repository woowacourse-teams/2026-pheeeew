package com.pheeeew.notification.fixture;

import com.pheeeew.notification.domain.outbox.CollectionClaimableOutbox;
import com.pheeeew.notification.domain.outbox.CollectionReminderOutbox;
import com.pheeeew.notification.domain.outbox.RankDroppedOutbox;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class NotificationOutboxFixture {

    private static final String 모든_작업 = """
            SELECT id, 'COLLECTION_CLAIMABLE' AS type, device_id, target_date, NULL::timestamptz AS round_at,
                   NULL::integer AS previous_rank, NULL::integer AS current_rank, status, result_code, attempt_count,
                   discovered_at, available_at, expires_at, claim_token, claim_expires_at, finished_at
              FROM collection_claimable_outbox
            UNION ALL
            SELECT id, 'COLLECTION_CLAIM_REMINDER', device_id, target_date, NULL::timestamptz, NULL::integer,
                   NULL::integer, status, result_code, attempt_count, discovered_at, available_at, expires_at,
                   claim_token, claim_expires_at, finished_at
              FROM collection_reminder_outbox
            UNION ALL
            SELECT id, 'RANK_DROPPED', device_id, target_date, round_at, previous_rank, current_rank, status,
                   result_code, attempt_count, discovered_at, available_at, expires_at, claim_token,
                   claim_expires_at, finished_at
              FROM rank_dropped_outbox
            """;

    private NotificationOutboxFixture() {
    }

    public static CollectionClaimableOutbox 발급_가능_작업(
            Long deviceId,
            LocalDate targetDate,
            Instant discoveredAt,
            Instant availableAt,
            Instant expiresAt
    ) {
        return CollectionClaimableOutbox.builder()
                .deviceId(deviceId)
                .targetDate(targetDate)
                .discoveredAt(discoveredAt)
                .availableAt(availableAt)
                .expiresAt(expiresAt)
                .build();
    }

    public static CollectionReminderOutbox 리마인더_작업(
            Long deviceId,
            LocalDate targetDate,
            Instant discoveredAt,
            Instant expiresAt
    ) {
        return CollectionReminderOutbox.builder()
                .deviceId(deviceId)
                .targetDate(targetDate)
                .discoveredAt(discoveredAt)
                .availableAt(discoveredAt)
                .expiresAt(expiresAt)
                .build();
    }

    public static RankDroppedOutbox 순위_작업(
            Long deviceId,
            LocalDate targetDate,
            Instant roundAt,
            int previousRank,
            int currentRank,
            Instant discoveredAt,
            Instant expiresAt
    ) {
        return RankDroppedOutbox.builder()
                .deviceId(deviceId)
                .targetDate(targetDate)
                .roundAt(roundAt)
                .previousRank(previousRank)
                .currentRank(currentRank)
                .discoveredAt(discoveredAt)
                .availableAt(discoveredAt)
                .expiresAt(expiresAt)
                .build();
    }

    public static List<작업> 기기의_작업들(JdbcClient jdbc, Long deviceId) {
        return jdbc.sql("SELECT * FROM (" + 모든_작업 + ") jobs WHERE device_id = :deviceId"
                        + " ORDER BY discovered_at, id, type")
                .param("deviceId", deviceId)
                .query((row, index) -> 작업으로(row))
                .list();
    }

    public static 작업 기기의_작업(JdbcClient jdbc, Long deviceId, String type) {
        return jdbc.sql("SELECT * FROM (" + 모든_작업 + ") jobs WHERE device_id = :deviceId AND type = :type")
                .param("deviceId", deviceId)
                .param("type", type)
                .query((row, index) -> 작업으로(row))
                .single();
    }

    public static Map<Long, Integer> 회차의_스냅샷(JdbcClient jdbc, Instant roundAt) {
        return jdbc.sql("SELECT device_id, rank FROM notification_rank_snapshots WHERE round_at = :roundAt")
                .param("roundAt", roundAt.atOffset(ZoneOffset.UTC))
                .query((row, index) -> Map.entry(row.getLong("device_id"), row.getInt("rank")))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static List<Instant> 날짜의_회차들(JdbcClient jdbc, LocalDate targetDate) {
        return jdbc.sql("SELECT round_at FROM notification_rank_rounds WHERE target_date = :targetDate ORDER BY round_at")
                .param("targetDate", targetDate)
                .query((row, index) -> row.getObject("round_at", OffsetDateTime.class).toInstant())
                .list();
    }

    public static void 알림_데이터를_지운다(JdbcClient jdbc, Collection<LocalDate> targetDates, Collection<Long> deviceIds) {
        jdbc.sql("DELETE FROM notification_rank_rounds WHERE target_date IN (:targetDates)")
                .param("targetDates", targetDates)
                .update();
        if (deviceIds.isEmpty()) {
            return;
        }
        for (String table : List.of("collection_claimable_outbox", "collection_reminder_outbox", "rank_dropped_outbox",
                "notification_rank_snapshots", "device_push_tokens", "device_notification_settings", "collection_cards",
                "device_daily_stamps", "device_daily_presses")) {
            jdbc.sql("DELETE FROM " + table + " WHERE device_id IN (:deviceIds)")
                    .param("deviceIds", deviceIds)
                    .update();
        }
        jdbc.sql("DELETE FROM devices WHERE id IN (:deviceIds)")
                .param("deviceIds", deviceIds)
                .update();
    }

    private static 작업 작업으로(ResultSet row) throws SQLException {
        return new 작업(
                row.getLong("id"),
                row.getString("type"),
                row.getLong("device_id"),
                row.getObject("target_date", LocalDate.class),
                시각(row, "round_at"),
                row.getObject("previous_rank", Integer.class),
                row.getObject("current_rank", Integer.class),
                row.getString("status"),
                row.getString("result_code"),
                row.getInt("attempt_count"),
                시각(row, "discovered_at"),
                시각(row, "available_at"),
                시각(row, "expires_at"),
                row.getObject("claim_token", UUID.class),
                시각(row, "claim_expires_at"),
                시각(row, "finished_at")
        );
    }

    private static Instant 시각(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public record 작업(
            Long id,
            String type,
            Long deviceId,
            LocalDate targetDate,
            Instant roundAt,
            Integer previousRank,
            Integer currentRank,
            String status,
            String resultCode,
            int attemptCount,
            Instant discoveredAt,
            Instant availableAt,
            Instant expiresAt,
            UUID claimToken,
            Instant claimExpiresAt,
            Instant finishedAt
    ) {
    }
}
