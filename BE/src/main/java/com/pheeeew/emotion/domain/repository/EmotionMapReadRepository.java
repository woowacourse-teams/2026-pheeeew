package com.pheeeew.emotion.domain.repository;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.projection.EmotionMapBlocks;
import com.pheeeew.emotion.domain.repository.projection.EmotionMapPin;
import com.pheeeew.emotion.domain.repository.projection.RegionEmotionStateCount;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class EmotionMapReadRepository {

    private final JdbcClient jdbc;

    public List<EmotionMapPin> findPins(Instant at, int maximum) {
        return jdbc.sql("""
                SELECT id, device_id, created_at, ST_X(location) AS longitude, ST_Y(location) AS latitude,
                       state, rotation_degrees, group_stamp_id
                FROM emotions
                WHERE deleted_at IS NULL AND (memo IS NOT NULL OR audio_object_key IS NOT NULL)
                  AND created_at <= :at
                ORDER BY created_at DESC, id DESC
                LIMIT :maximum
                """).param("at", Timestamp.from(at)).param("maximum", maximum)
                .query((row, index) -> EmotionMapPin.of(row.getLong("id"), row.getObject("device_id", Long.class),
                        row.getTimestamp("created_at").toInstant(), row.getDouble("longitude"), row.getDouble("latitude"),
                        row.getString("state") == null ? null : EmotionState.valueOf(row.getString("state")),
                        row.getDouble("rotation_degrees"), row.getObject("group_stamp_id", Long.class))).list();
    }

    public Instant findNextPinActivation(Instant at) {
        return jdbc.sql("""
                SELECT MIN(created_at) FROM emotions
                WHERE deleted_at IS NULL AND (memo IS NOT NULL OR audio_object_key IS NOT NULL)
                  AND created_at > :at
                """).param("at", Timestamp.from(at)).query((row, index) -> {
                    Timestamp value = row.getTimestamp(1);
                    return value == null ? null : value.toInstant();
                }).optional().orElse(null);
    }

    public List<RegionEmotionStateCount> findRegionStateCounts(Instant snapshotAt) {
        return jdbc.sql("""
                SELECT left(region_code, 5) AS code, state,
                    COUNT(*) FILTER (WHERE created_at <= :at) AS amount,
                    MIN(created_at) FILTER (WHERE created_at > :at) AS next_activation
                FROM emotions
                WHERE deleted_at IS NULL AND (memo IS NOT NULL OR audio_object_key IS NOT NULL)
                GROUP BY left(region_code, 5), state
                """).param("at", Timestamp.from(snapshotAt)).query((row, index) -> RegionEmotionStateCount.of(
                        row.getString("code"), row.getString("state") == null ? null : EmotionState.valueOf(row.getString("state")),
                        row.getLong("amount"), row.getTimestamp("next_activation") == null ? null
                                : row.getTimestamp("next_activation").toInstant())).list();
    }

    public EmotionMapBlocks findBlockedTargets(Long deviceId) {
        Set<Long> emotions = new HashSet<>();
        Set<Long> authors = new HashSet<>();
        jdbc.sql("""
                SELECT emotion_id AS target, false AS author FROM emotion_blocks WHERE blocker_device_id = :device
                UNION ALL
                SELECT blocked_device_id AS target, true AS author FROM device_blocks WHERE blocker_device_id = :device
                """).param("device", deviceId).query((row, index) -> {
                    (row.getBoolean("author") ? authors : emotions).add(row.getLong("target"));
                    return Boolean.TRUE;
                }).list();

        return EmotionMapBlocks.of(Set.copyOf(emotions), Set.copyOf(authors));
    }

}
