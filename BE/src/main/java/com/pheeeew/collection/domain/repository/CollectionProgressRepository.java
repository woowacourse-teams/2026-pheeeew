package com.pheeeew.collection.domain.repository;

import com.pheeeew.collection.domain.repository.projection.CollectionActivityCounts;
import com.pheeeew.collection.domain.repository.projection.CollectionCardDetail;
import com.pheeeew.collection.domain.repository.projection.CollectionAssetPaths;
import com.pheeeew.collection.domain.repository.projection.CollectionTodayProgress;
import com.pheeeew.emotion.domain.EmotionState;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

    private final JdbcClient jdbc;

    public Optional<CollectionCardDetail> findDetail(UUID cardId, Long deviceId) {
        return jdbc.sql("""
                SELECT card.public_id, card.card_date, card.state, card.card_version,
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
                WHERE card.public_id = :cardId AND card.device_id = :deviceId
                """)
                .param("cardId", cardId)
                .param("deviceId", deviceId)
                .query((row, index) -> CollectionCardDetail.of(row.getObject("public_id", UUID.class),
                        row.getObject("card_date", LocalDate.class), EmotionState.valueOf(row.getString("state")),
                        row.getObject("stamp_count", Long.class), row.getObject("press_count", Long.class), row.getString("card_version"),
                        CollectionAssetPaths.of(row.getString("image_path"), row.getString("round_image_path"),
                                row.getString("straight_image_path"))))
                .optional();
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
}
