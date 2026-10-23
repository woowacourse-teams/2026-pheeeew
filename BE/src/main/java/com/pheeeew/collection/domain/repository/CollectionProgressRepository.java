package com.pheeeew.collection.domain.repository;

import com.pheeeew.collection.domain.CollectionCardCounts;
import com.pheeeew.collection.domain.repository.projection.CollectionActivityCounts;
import com.pheeeew.collection.domain.repository.projection.CollectionAssetPaths;
import com.pheeeew.collection.domain.repository.projection.CollectionCardDetail;
import com.pheeeew.collection.domain.repository.projection.CollectionHistoryCard;
import com.pheeeew.collection.domain.repository.projection.CollectionHistoryPage;
import com.pheeeew.collection.domain.repository.projection.CollectionTodayProgress;
import com.pheeeew.emotion.domain.EmotionState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class CollectionProgressRepository {

    private static final String ACTIVITY_COUNTS_SQL = """
                SELECT state, SUM(stamp_count)::bigint AS stamp_count, SUM(press_count)::bigint AS press_count
                FROM (
                    SELECT state, stamp_count, NULL::bigint AS press_count
                    FROM device_daily_stamps
                    WHERE stamp_date = :date AND device_id = :deviceId
                    UNION ALL
                    SELECT state, NULL::bigint AS stamp_count, press_count
                    FROM device_daily_presses
                    WHERE press_date = :date AND device_id = :deviceId
                ) activity
                GROUP BY state
                """;

    private static final String CARD_DETAILS_SQL = """
                SELECT card.id, card.public_id, card.card_date, card.state, card.card_version,
                       activity_stamps.stamp_count AS stamp_count,
                       presses.press_count AS press_count,
                       characters.image_path, stamps.round_image_path, stamps.straight_image_path
                FROM collection_cards card
                LEFT JOIN device_daily_stamps activity_stamps
                  ON activity_stamps.device_id = card.device_id AND activity_stamps.stamp_date = card.card_date
                 AND activity_stamps.state = card.state
                LEFT JOIN device_daily_presses presses
                  ON presses.device_id = card.device_id AND presses.press_date = card.card_date
                 AND presses.state = card.state
                LEFT JOIN collection_character_assets characters
                  ON characters.card_version = card.card_version AND characters.state = card.state
                 AND characters.asset_number = card.character_number
                LEFT JOIN collection_stamp_assets stamps
                  ON stamps.card_version = card.card_version AND stamps.state = card.state
                 AND stamps.asset_number = card.stamp_number
                """;

    private final JdbcClient jdbc;

    public Optional<CollectionCardDetail> findDetail(UUID cardId, Long deviceId) {
        return jdbc.sql(CARD_DETAILS_SQL + " WHERE card.public_id = :cardId AND card.device_id = :deviceId")
                .param("cardId", cardId)
                .param("deviceId", deviceId)
                .query((row, index) -> mapDetail(row))
                .optional();
    }

    public CollectionHistoryPage findHistory(Long deviceId, LocalDate beforeDate,
            LocalDate lastDate, long lastId, EmotionState emotion, int fetchSize) {
        String filter = emotion == null ? "" : " AND card.state = :emotion";
        var query = jdbc.sql("""
                WITH counts AS (
                    SELECT COUNT(*) FILTER (WHERE state = 'FRUSTRATED') AS count_frustrated,
                           COUNT(*) FILTER (WHERE state = 'IRRITATED') AS count_irritated,
                           COUNT(*) FILTER (WHERE state = 'EXHAUSTED') AS count_exhausted,
                           COUNT(*) FILTER (WHERE state = 'DISCOURAGED') AS count_discouraged,
                           COUNT(*) FILTER (WHERE state = 'ANGRY') AS count_angry
                    FROM collection_cards
                    WHERE device_id = :deviceId AND card_date < :beforeDate
                ), page AS (
                """ + CARD_DETAILS_SQL + """
                 WHERE card.device_id = :deviceId AND card.card_date < :beforeDate
                   AND (card.card_date, card.id) < (:lastDate, :lastId)
                """ + filter + " ORDER BY card.card_date DESC, card.id DESC LIMIT :fetchSize" + """
                )
                SELECT page.*, counts.*
                FROM counts LEFT JOIN page ON TRUE
                ORDER BY page.card_date DESC, page.id DESC
                """)
                .param("deviceId", deviceId)
                .param("beforeDate", beforeDate)
                .param("lastDate", lastDate)
                .param("lastId", lastId)
                .param("fetchSize", fetchSize);
        if (emotion != null) {
            query = query.param("emotion", emotion.name());
        }
        return query.query((ResultSetExtractor<CollectionHistoryPage>) rows -> {
            rows.next(); // 집계는 카드가 없어도 한 행을 반환한다.
            var counts = new EnumMap<EmotionState, Long>(EmotionState.class);
            counts.put(EmotionState.FRUSTRATED, rows.getLong("count_frustrated"));
            counts.put(EmotionState.IRRITATED, rows.getLong("count_irritated"));
            counts.put(EmotionState.EXHAUSTED, rows.getLong("count_exhausted"));
            counts.put(EmotionState.DISCOURAGED, rows.getLong("count_discouraged"));
            counts.put(EmotionState.ANGRY, rows.getLong("count_angry"));
            var cards = new ArrayList<CollectionHistoryCard>();
            do {
                if (rows.getObject("id") != null) {
                    cards.add(CollectionHistoryCard.of(rows.getLong("id"), mapDetail(rows)));
                }
            } while (rows.next());
            return CollectionHistoryPage.of(cards, CollectionCardCounts.of(counts));
        });
    }

    public List<CollectionActivityCounts> findByDateAndDeviceId(LocalDate date, Long deviceId) {
        return jdbc.sql(ACTIVITY_COUNTS_SQL)
                .param("date", date)
                .param("deviceId", deviceId)
                .query((row, index) -> CollectionActivityCounts.of(EmotionState.valueOf(row.getString("state")),
                        row.getLong("stamp_count"), row.getLong("press_count")))
                .list();
    }

    public List<CollectionTodayProgress> findTodayByDateAndDeviceId(LocalDate date, Long deviceId) {
        return jdbc.sql("WITH activity AS (" + ACTIVITY_COUNTS_SQL + """
                ), cards AS (
                    SELECT * FROM collection_cards
                    WHERE card_date = :date AND device_id = :deviceId
                )
                SELECT COALESCE(activity.state, cards.state) AS state,
                       activity.stamp_count AS stamp_count,
                       activity.press_count AS press_count,
                       cards.public_id, cards.card_date, cards.cycle, cards.combination_index,
                       cards.card_version, cards.claimed_at,
                       characters.image_path, stamps.round_image_path, stamps.straight_image_path
                FROM activity
                FULL OUTER JOIN cards ON cards.state = activity.state
                LEFT JOIN collection_character_assets characters
                  ON characters.card_version = cards.card_version AND characters.state = cards.state
                 AND characters.asset_number = cards.character_number
                LEFT JOIN collection_stamp_assets stamps
                  ON stamps.card_version = cards.card_version AND stamps.state = cards.state
                 AND stamps.asset_number = cards.stamp_number
                """)
                .param("date", date)
                .param("deviceId", deviceId)
                .query((row, index) -> {
                    UUID publicId = row.getObject("public_id", UUID.class);
                    var card = publicId == null ? null : CollectionTodayProgress.Card.of(
                            publicId, row.getObject("card_date", LocalDate.class), row.getLong("cycle"),
                            row.getInt("combination_index"), row.getString("card_version"),
                            row.getObject("claimed_at", OffsetDateTime.class).toInstant(),
                            CollectionAssetPaths.of(row.getString("image_path"),
                                    row.getString("round_image_path"), row.getString("straight_image_path")));
                    return CollectionTodayProgress.of(EmotionState.valueOf(row.getString("state")),
                            row.getObject("stamp_count", Long.class), row.getObject("press_count", Long.class), card);
                })
                .list();
    }

    private CollectionCardDetail mapDetail(ResultSet row) throws SQLException {
        return CollectionCardDetail.of(row.getObject("public_id", UUID.class),
                row.getObject("card_date", LocalDate.class), EmotionState.valueOf(row.getString("state")),
                row.getObject("stamp_count", Long.class), row.getObject("press_count", Long.class),
                row.getString("card_version"), CollectionAssetPaths.of(row.getString("image_path"),
                        row.getString("round_image_path"), row.getString("straight_image_path")));
    }
}
