package com.pheeeew.emotion.domain.repository;

import com.pheeeew.emotion.domain.EmotionEmoji;
import com.pheeeew.emotion.domain.repository.projection.EmotionEmojiCountProjection;
import com.pheeeew.emotion.domain.repository.projection.EmotionEmojiPageCountProjection;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EmotionEmojiRepository extends JpaRepository<EmotionEmoji, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO emotion_emojis (emotion_id, device_id, emoji_type, created_at, updated_at)
            VALUES (:emotionId, :deviceId, :emojiType, :now, :now)
            ON CONFLICT (emotion_id, device_id, emoji_type) DO NOTHING
            """, nativeQuery = true)
    int saveIfAbsent(Long emotionId, Long deviceId, String emojiType, Instant now);

    @Modifying
    @Query(value = """
            DELETE FROM emotion_emojis
             WHERE emotion_id = :emotionId
               AND device_id = :deviceId
               AND emoji_type = :emojiType
            """, nativeQuery = true)
    int deleteSelection(Long emotionId, Long deviceId, String emojiType);

    @Query(value = """
            SELECT emoji_type AS "emojiType",
                   COUNT(*) AS "selectionCount",
                   BOOL_OR(device_id = :deviceId) AS "selected"
              FROM emotion_emojis
             WHERE emotion_id = :emotionId
             GROUP BY emoji_type
            """, nativeQuery = true)
    List<EmotionEmojiCountProjection> findCounts(Long emotionId, Long deviceId);
    @Query(value = """
            SELECT emotion_id AS "emotionId", emoji_type AS "emojiType",
                   COUNT(*) AS "selectionCount", BOOL_OR(device_id = :deviceId) AS "selected"
              FROM emotion_emojis
             WHERE emotion_id IN (:emotionIds)
             GROUP BY emotion_id, emoji_type
            """, nativeQuery = true)
    List<EmotionEmojiPageCountProjection> findCountsForPage(List<Long> emotionIds, Long deviceId);
}
