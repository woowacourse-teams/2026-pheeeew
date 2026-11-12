package com.pheeeew.pressevent.infra;

import com.pheeeew.pressevent.application.PressEventAttemptResult;
import com.pheeeew.pressevent.application.PressEventCursor;
import com.pheeeew.pressevent.application.PressEventRankingResult.MyRank;
import com.pheeeew.pressevent.application.PressEventRankingResult.Standing;
import com.pheeeew.pressevent.domain.PressEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
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
public class PressEventRepository {

    private static final String EVENT_BY_ID = """
            SELECT starts_at, ends_at, statement_timestamp() AS observed_at
            FROM press_events WHERE id = :event
            """;
    private static final String RANK = """
            (1 + (SELECT COUNT(*) FROM press_event_bests ahead
                  WHERE ahead.event_id = best.event_id AND ahead.score > best.score))
            """;
    private static final String AFTER_CURSOR = """
            AND (score < :score OR
                 (score = :score AND (achieved_at, public_id) > (:achievedAt, :publicId)))
            """;

    private final JdbcClient jdbc;

    public Optional<PressEvent> findEvent(UUID eventId) {
        return findEvent(eventId, EVENT_BY_ID);
    }

    public Optional<PressEvent> findEventForSubmission(UUID eventId) {
        return findEvent(eventId, EVENT_BY_ID + "FOR SHARE");
    }

    public Instant now() {
        return jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single().toInstant();
    }

    public Optional<SavedAttempt> findAttempt(UUID eventId, long deviceId, UUID requestId) {
        return jdbc.sql("""
                SELECT normalized_counts, total, best_score, rank, ranked_at
                FROM press_event_attempts
                WHERE event_id = :event AND device_id = :device AND request_id = :request
                """)
                .param("event", eventId).param("device", deviceId).param("request", requestId)
                .query((row, index) -> SavedAttempt.of(
                        row.getString("normalized_counts"),
                        PressEventAttemptResult.of(requestId, row.getInt("total"), row.getInt("best_score"),
                                row.getLong("rank"), instant(row, "ranked_at"), true)))
                .optional();
    }

    public void updateBest(UUID eventId, long deviceId, int score, Instant now) {
        jdbc.sql("""
                INSERT INTO press_event_bests(event_id, device_id, public_id, score, achieved_at)
                VALUES (:event, :device, :publicId, :score, :now)
                ON CONFLICT (event_id, device_id) DO UPDATE
                  SET score = EXCLUDED.score, achieved_at = EXCLUDED.achieved_at
                  WHERE EXCLUDED.score > press_event_bests.score
                """)
                .param("event", eventId).param("device", deviceId).param("publicId", UUID.randomUUID())
                .param("score", score).param("now", now.atOffset(ZoneOffset.UTC)).update();
    }

    public void saveAttempt(UUID eventId, long deviceId, String normalizedCounts, Instant now,
            PressEventAttemptResult result) {
        jdbc.sql("""
                INSERT INTO press_event_attempts(event_id, device_id, request_id, normalized_counts,
                    total, submitted_at, best_score, rank, ranked_at)
                VALUES (:event, :device, :request, :counts, :total, :now, :bestScore, :rank, :rankedAt)
                """)
                .param("event", eventId).param("device", deviceId).param("request", result.attemptId())
                .param("counts", normalizedCounts).param("total", result.total()).param("now", now.atOffset(ZoneOffset.UTC))
                .param("bestScore", result.bestScore()).param("rank", result.rank())
                .param("rankedAt", result.rankedAt().atOffset(ZoneOffset.UTC)).update();
    }

    public Optional<MyRank> findMyRank(UUID eventId, long deviceId) {
        return jdbc.sql("""
                SELECT best.score, %s AS rank, statement_timestamp() AS ranked_at
                FROM press_event_bests best WHERE best.event_id = :event AND best.device_id = :device
                """.formatted(RANK))
                .param("event", eventId).param("device", deviceId)
                .query((row, index) -> MyRank.of(row.getInt("score"), row.getLong("rank"), instant(row, "ranked_at")))
                .optional();
    }

    public Optional<Standing> findLeader(UUID eventId, long deviceId) {
        return jdbc.sql("""
                SELECT 1 AS rank, best.public_id, COALESCE(device.nickname, '익명') AS nickname,
                       best.score, best.device_id = :device AS mine, best.achieved_at
                FROM press_event_bests best JOIN devices device ON device.id = best.device_id
                WHERE best.event_id = :event
                ORDER BY best.score DESC, best.achieved_at, best.public_id
                LIMIT 1
                """)
                .param("event", eventId).param("device", deviceId)
                .query(PressEventRepository::standing).optional();
    }

    public List<Standing> findPage(UUID eventId, long deviceId, PressEventCursor cursor, int limit) {
        JdbcClient.StatementSpec query = jdbc.sql("""
                SELECT %s AS rank, best.public_id, COALESCE(device.nickname, '익명') AS nickname,
                       best.score, best.device_id = :device AS mine, best.achieved_at
                FROM (
                    SELECT event_id, device_id, public_id, score, achieved_at
                    FROM press_event_bests
                    WHERE event_id = :event
                    %s
                    ORDER BY score DESC, achieved_at, public_id
                    LIMIT :limit
                ) best JOIN devices device ON device.id = best.device_id
                ORDER BY best.score DESC, best.achieved_at, best.public_id
                """.formatted(RANK, cursor == null ? "" : AFTER_CURSOR))
                .param("event", eventId).param("device", deviceId).param("limit", limit);
        if (cursor != null) {
            query.param("score", cursor.score()).param("achievedAt", cursor.achievedAt().atOffset(ZoneOffset.UTC))
                    .param("publicId", cursor.publicId());
        }
        return query.query(PressEventRepository::standing).list();
    }

    private Optional<PressEvent> findEvent(UUID eventId, String query) {
        return jdbc.sql(query).param("event", eventId)
                .query((row, index) -> PressEvent.of(
                        instant(row, "starts_at"), instant(row, "ends_at"), instant(row, "observed_at")))
                .optional();
    }

    private static Standing standing(ResultSet row, int index) throws SQLException {
        return Standing.of(row.getLong("rank"), row.getObject("public_id", UUID.class),
                row.getString("nickname"), row.getInt("score"), row.getBoolean("mine"), instant(row, "achieved_at"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        return row.getObject(column, OffsetDateTime.class).toInstant();
    }

    public record SavedAttempt(String normalizedCounts, PressEventAttemptResult result) {

        public static SavedAttempt of(String normalizedCounts, PressEventAttemptResult result) {
            return new SavedAttempt(normalizedCounts, result);
        }
    }
}
