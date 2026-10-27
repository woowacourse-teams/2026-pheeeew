package com.pheeeew.notification.domain.repository;

import static com.pheeeew.notification.domain.NotificationOutboxStatus.PENDING;

import com.pheeeew.notification.domain.NotificationOutbox;
import com.pheeeew.notification.domain.NotificationOutboxStatus;
import com.pheeeew.notification.domain.NotificationOutboxType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class NotificationOutboxRepository {

    private static final String CLAIMABLE_TABLE = "collection_claimable_outbox";
    private static final String REMINDER_TABLE = "collection_reminder_outbox";
    private static final String RANK_TABLE = "rank_dropped_outbox";
    private static final List<String> TABLES = List.of(CLAIMABLE_TABLE, REMINDER_TABLE, RANK_TABLE);

    private final JdbcClient jdbc;

    public void saveCollection(
            NotificationOutboxType type,
            Long deviceId,
            LocalDate targetDate,
            Instant now,
            Instant availableAt,
            Instant expiresAt
    ) {
        jdbc.sql("""
                INSERT INTO %s
                     (device_id, target_date, status, discovered_at, available_at, expires_at, updated_at)
                     VALUES (:deviceId, :targetDate, :pending, :now, :availableAt, :expiresAt, :now)
                ON CONFLICT DO NOTHING
                """.formatted(tableOf(type))).param("pending", PENDING.name())
                .param("deviceId", deviceId)
                .param("targetDate", targetDate)
                .param("now", utc(now))
                .param("availableAt", utc(availableAt))
                .param("expiresAt", utc(expiresAt))
                .update();
    }

    public void saveRank(
            Long deviceId,
            LocalDate targetDate,
            Instant roundAt,
            int previousRank,
            int currentRank,
            Instant now,
            Instant expiresAt
    ) {
        jdbc.sql("""
                INSERT INTO rank_dropped_outbox
                     (device_id, target_date, round_at, previous_rank, current_rank, status,
                      discovered_at, available_at, expires_at, updated_at)
                     VALUES (:deviceId, :targetDate, :roundAt, :previousRank, :currentRank, :pending,
                             :now, :now, :expiresAt, :now)
                ON CONFLICT DO NOTHING
                """).param("deviceId", deviceId)
                .param("targetDate", targetDate)
                .param("roundAt", utc(roundAt))
                .param("previousRank", previousRank)
                .param("currentRank", currentRank)
                .param("pending", PENDING.name())
                .param("now", utc(now))
                .param("expiresAt", utc(expiresAt))
                .update();
    }

    public int expire(NotificationOutboxType type, NotificationOutboxStatus status, String resultCode, Instant now) {
        return jdbc.sql("""
                UPDATE %s
                   SET status = :status, result_code = :resultCode, finished_at = :now,
                       claim_token = NULL, claim_expires_at = NULL, updated_at = :now
                 WHERE status = :pending AND expires_at <= :now
                   AND (claim_expires_at IS NULL OR claim_expires_at <= :now)
                """.formatted(tableOf(type))).param("status", status.name())
                .param("resultCode", resultCode)
                .param("pending", PENDING.name())
                .param("now", utc(now))
                .update();
    }

    public Optional<NotificationOutbox> claim(
            NotificationOutboxType type,
            Instant now,
            UUID claimToken,
            Instant claimExpiresAt
    ) {
        return jdbc.sql("""
                UPDATE %1$s
                   SET claim_token = :claimToken, claim_expires_at = :claimExpiresAt,
                       attempt_count = attempt_count + 1, updated_at = :now
                 WHERE id = (SELECT id FROM %1$s
                              WHERE status = '%2$s' AND available_at <= :now AND expires_at > :now
                                AND (claim_expires_at IS NULL OR claim_expires_at <= :now)
                              ORDER BY expires_at, available_at, id
                              LIMIT 1
                              FOR UPDATE SKIP LOCKED)
                RETURNING *
                """.formatted(tableOf(type), PENDING.name())).param("claimToken", claimToken)
                .param("claimExpiresAt", utc(claimExpiresAt))
                .param("now", utc(now))
                .query((row, rowNumber) -> toOutbox(row, type))
                .optional();
    }

    public boolean finish(
            NotificationOutboxType type,
            Long id,
            UUID claimToken,
            NotificationOutboxStatus status,
            String resultCode,
            Instant now
    ) {
        return jdbc.sql("""
                UPDATE %s
                   SET status = :status, result_code = :resultCode, finished_at = :now,
                       claim_token = NULL, claim_expires_at = NULL, updated_at = :now
                 WHERE id = :id AND claim_token = :claimToken AND status = :pending
                """.formatted(tableOf(type))).param("status", status.name())
                .param("resultCode", resultCode)
                .param("pending", PENDING.name())
                .param("now", utc(now))
                .param("id", id)
                .param("claimToken", claimToken)
                .update() == 1;
    }

    public boolean retry(
            NotificationOutboxType type,
            Long id,
            UUID claimToken,
            String resultCode,
            Instant availableAt,
            Instant now
    ) {
        return jdbc.sql("""
                UPDATE %s
                   SET available_at = :availableAt, result_code = :resultCode,
                       claim_token = NULL, claim_expires_at = NULL, updated_at = :now
                 WHERE id = :id AND claim_token = :claimToken AND status = :pending
                """.formatted(tableOf(type))).param("availableAt", utc(availableAt))
                .param("resultCode", resultCode)
                .param("pending", PENDING.name())
                .param("now", utc(now))
                .param("id", id)
                .param("claimToken", claimToken)
                .update() == 1;
    }

    public int deleteByTargetDateBefore(LocalDate date) {
        return TABLES.stream()
                .mapToInt(table -> jdbc.sql("DELETE FROM %s WHERE target_date < :date".formatted(table))
                        .param("date", date)
                        .update())
                .sum();
    }

    private String tableOf(NotificationOutboxType type) {
        return switch (type) {
            case COLLECTION_CLAIMABLE -> CLAIMABLE_TABLE;
            case COLLECTION_CLAIM_REMINDER -> REMINDER_TABLE;
            case RANK_DROPPED -> RANK_TABLE;
        };
    }

    private OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private NotificationOutbox toOutbox(ResultSet row, NotificationOutboxType type) throws SQLException {
        boolean rank = type == NotificationOutboxType.RANK_DROPPED;
        return NotificationOutbox.of(
                row.getLong("id"),
                type,
                row.getLong("device_id"),
                row.getObject("target_date", LocalDate.class),
                rank ? row.getInt("previous_rank") : null,
                rank ? row.getInt("current_rank") : null,
                row.getInt("attempt_count"),
                row.getObject("expires_at", OffsetDateTime.class).toInstant(),
                row.getObject("claim_token", UUID.class)
        );
    }
}
