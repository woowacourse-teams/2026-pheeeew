package com.pheeeew.notification.domain.repository;

import com.pheeeew.notification.domain.NotificationOutbox;
import com.pheeeew.notification.domain.NotificationOutboxType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class NotificationOutboxRepository {

    private static final String SENT = "SENT";
    private static final String CANCELED = "CANCELED";
    private static final String FAILED = "FAILED";

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
                INSERT INTO notification_outbox
                     (type, device_id, target_date, status, discovered_at, available_at, expires_at, updated_at)
                     VALUES (:type, :deviceId, :targetDate, 'PENDING', :now, :availableAt, :expiresAt, :now)
                ON CONFLICT DO NOTHING
                """).param("type", type.name())
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
                INSERT INTO notification_outbox
                     (type, device_id, target_date, round_at, previous_rank, current_rank, status,
                      discovered_at, available_at, expires_at, updated_at)
                     VALUES (:type, :deviceId, :targetDate, :roundAt, :previousRank, :currentRank, 'PENDING',
                             :now, :now, :expiresAt, :now)
                ON CONFLICT DO NOTHING
                """).param("type", NotificationOutboxType.RANK_DROPPED.name())
                .param("deviceId", deviceId)
                .param("targetDate", targetDate)
                .param("roundAt", utc(roundAt))
                .param("previousRank", previousRank)
                .param("currentRank", currentRank)
                .param("now", utc(now))
                .param("expiresAt", utc(expiresAt))
                .update();
    }

    public int expire(Instant now) {
        return jdbc.sql("""
                UPDATE notification_outbox
                   SET status = 'CANCELED', result_code = 'EXPIRED', finished_at = :now,
                       claim_token = NULL, claim_expires_at = NULL, updated_at = :now
                 WHERE status = 'PENDING' AND expires_at <= :now
                   AND (claim_expires_at IS NULL OR claim_expires_at <= :now)
                """).param("now", utc(now))
                .update();
    }

    public Optional<NotificationOutbox> claim(Instant now, UUID claimToken, Instant claimExpiresAt) {
        return jdbc.sql("""
                UPDATE notification_outbox
                   SET claim_token = :claimToken, claim_expires_at = :claimExpiresAt,
                       attempt_count = attempt_count + 1, updated_at = :now
                 WHERE id = (SELECT id FROM notification_outbox
                              WHERE status = 'PENDING' AND available_at <= :now AND expires_at > :now
                                AND (claim_expires_at IS NULL OR claim_expires_at <= :now)
                              ORDER BY expires_at, available_at, id
                              LIMIT 1
                              FOR UPDATE SKIP LOCKED)
                RETURNING id, type, device_id, target_date, previous_rank, current_rank, attempt_count,
                          expires_at, claim_token
                """).param("claimToken", claimToken)
                .param("claimExpiresAt", utc(claimExpiresAt))
                .param("now", utc(now))
                .query((row, rowNumber) -> toOutbox(row))
                .optional();
    }

    public boolean markSent(Long id, UUID claimToken, Instant now) {
        return finish(id, claimToken, SENT, null, now);
    }

    public boolean cancel(Long id, UUID claimToken, String resultCode, Instant now) {
        return finish(id, claimToken, CANCELED, resultCode, now);
    }

    public boolean fail(Long id, UUID claimToken, String resultCode, Instant now) {
        return finish(id, claimToken, FAILED, resultCode, now);
    }

    public boolean retry(Long id, UUID claimToken, Instant availableAt, Instant now) {
        return jdbc.sql("""
                UPDATE notification_outbox
                   SET available_at = :availableAt, result_code = 'RETRYABLE',
                       claim_token = NULL, claim_expires_at = NULL, updated_at = :now
                 WHERE id = :id AND claim_token = :claimToken AND status = 'PENDING'
                """).param("availableAt", utc(availableAt))
                .param("now", utc(now))
                .param("id", id)
                .param("claimToken", claimToken)
                .update() == 1;
    }

    public int deleteByTargetDateBefore(LocalDate date) {
        return jdbc.sql("DELETE FROM notification_outbox WHERE target_date < :date")
                .param("date", date)
                .update();
    }

    private boolean finish(Long id, UUID claimToken, String status, String resultCode, Instant now) {
        return jdbc.sql("""
                UPDATE notification_outbox
                   SET status = :status, result_code = :resultCode, finished_at = :now,
                       claim_token = NULL, claim_expires_at = NULL, updated_at = :now
                 WHERE id = :id AND claim_token = :claimToken AND status = 'PENDING'
                """).param("status", status)
                .param("resultCode", resultCode)
                .param("now", utc(now))
                .param("id", id)
                .param("claimToken", claimToken)
                .update() == 1;
    }

    private NotificationOutbox toOutbox(ResultSet row) throws SQLException {
        return NotificationOutbox.of(
                row.getLong("id"),
                NotificationOutboxType.valueOf(row.getString("type")),
                row.getLong("device_id"),
                row.getObject("target_date", LocalDate.class),
                row.getObject("previous_rank", Integer.class),
                row.getObject("current_rank", Integer.class),
                row.getInt("attempt_count"),
                row.getObject("expires_at", OffsetDateTime.class).toInstant(),
                row.getObject("claim_token", UUID.class)
        );
    }

    private OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
