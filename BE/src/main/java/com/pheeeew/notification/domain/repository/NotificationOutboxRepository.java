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

    private static final String COLLECTION_TABLE = "notification_collection_outbox";
    private static final String RANK_TABLE = "notification_rank_outbox";
    private static final List<String> TABLES = List.of(RANK_TABLE, COLLECTION_TABLE);

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
                INSERT INTO notification_collection_outbox
                     (type, device_id, target_date, status, discovered_at, available_at, expires_at, updated_at)
                     VALUES (:type, :deviceId, :targetDate, :pending, :now, :availableAt, :expiresAt, :now)
                ON CONFLICT DO NOTHING
                """).param("type", type.name())
                .param("pending", PENDING.name())
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
                INSERT INTO notification_rank_outbox
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

    public int expire(NotificationOutboxStatus status, String resultCode, Instant now) {
        return TABLES.stream()
                .mapToInt(table -> jdbc.sql("""
                        UPDATE %s
                           SET status = :status, result_code = :resultCode, finished_at = :now,
                               claim_token = NULL, claim_expires_at = NULL, updated_at = :now
                         WHERE status = :pending AND expires_at <= :now
                           AND (claim_expires_at IS NULL OR claim_expires_at <= :now)
                        """.formatted(table)).param("status", status.name())
                        .param("resultCode", resultCode)
                        .param("pending", PENDING.name())
                        .param("now", utc(now))
                        .update())
                .sum();
    }

    public Optional<NotificationOutbox> claim(Instant now, UUID claimToken, Instant claimExpiresAt) {
        return claimRank(now, claimToken, claimExpiresAt)
                .or(() -> claimCollection(now, claimToken, claimExpiresAt));
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

    private Optional<NotificationOutbox> claimRank(Instant now, UUID claimToken, Instant claimExpiresAt) {
        return jdbc.sql("""
                UPDATE notification_rank_outbox
                   SET claim_token = :claimToken, claim_expires_at = :claimExpiresAt,
                       attempt_count = attempt_count + 1, updated_at = :now
                 WHERE id = (SELECT id FROM notification_rank_outbox
                              WHERE status = '%s' AND available_at <= :now AND expires_at > :now
                                AND (claim_expires_at IS NULL OR claim_expires_at <= :now)
                              ORDER BY expires_at, available_at, id
                              LIMIT 1
                              FOR UPDATE SKIP LOCKED)
                RETURNING id, device_id, target_date, previous_rank, current_rank, attempt_count,
                          expires_at, claim_token
                """.formatted(PENDING.name())).param("claimToken", claimToken)
                .param("claimExpiresAt", utc(claimExpiresAt))
                .param("now", utc(now))
                .query((row, rowNumber) -> toOutbox(row, NotificationOutboxType.RANK_DROPPED,
                        row.getInt("previous_rank"), row.getInt("current_rank")))
                .optional();
    }

    private Optional<NotificationOutbox> claimCollection(Instant now, UUID claimToken, Instant claimExpiresAt) {
        return jdbc.sql("""
                UPDATE notification_collection_outbox
                   SET claim_token = :claimToken, claim_expires_at = :claimExpiresAt,
                       attempt_count = attempt_count + 1, updated_at = :now
                 WHERE id = (SELECT id FROM notification_collection_outbox
                              WHERE status = '%s' AND available_at <= :now AND expires_at > :now
                                AND (claim_expires_at IS NULL OR claim_expires_at <= :now)
                              ORDER BY expires_at, available_at, id
                              LIMIT 1
                              FOR UPDATE SKIP LOCKED)
                RETURNING id, type, device_id, target_date, attempt_count, expires_at, claim_token
                """.formatted(PENDING.name())).param("claimToken", claimToken)
                .param("claimExpiresAt", utc(claimExpiresAt))
                .param("now", utc(now))
                .query((row, rowNumber) -> toOutbox(row, NotificationOutboxType.valueOf(row.getString("type")),
                        null, null))
                .optional();
    }

    private NotificationOutbox toOutbox(
            ResultSet row,
            NotificationOutboxType type,
            Integer previousRank,
            Integer currentRank
    ) throws SQLException {
        return NotificationOutbox.of(
                row.getLong("id"),
                type,
                row.getLong("device_id"),
                row.getObject("target_date", LocalDate.class),
                previousRank,
                currentRank,
                row.getInt("attempt_count"),
                row.getObject("expires_at", OffsetDateTime.class).toInstant(),
                row.getObject("claim_token", UUID.class)
        );
    }

    private String tableOf(NotificationOutboxType type) {
        return type.isCollection() ? COLLECTION_TABLE : RANK_TABLE;
    }

    private OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
