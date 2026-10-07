package com.pheeeew.report.domain.repository;

import com.pheeeew.report.domain.EmotionBlock;
import com.pheeeew.report.domain.repository.projection.BlockProjection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmotionBlockRepository extends JpaRepository<EmotionBlock, Long> {

    Optional<EmotionBlock> findByBlockerDeviceIdAndEmotionId(Long blockerDeviceId, Long emotionId);

    void deleteByBlockerDeviceIdAndEmotionId(Long blockerDeviceId, Long emotionId);

    @Query(
            value = """
                    SELECT
                        emotion_block.id AS "blockId",
                        emotion_block.emotion_id AS "emotionId",
                        CASE WHEN emotion.anonymous THEN '익명'
                             ELSE COALESCE(author.nickname, '익명') END AS nickname,
                        emotion.memo AS memo,
                        emotion_block.created_at AS "createdAt"
                    FROM emotion_blocks emotion_block
                    JOIN emotions emotion ON emotion.id = emotion_block.emotion_id
                    LEFT JOIN devices author ON author.id = emotion.device_id AND NOT emotion.anonymous
                    WHERE emotion_block.blocker_device_id = :blockerDeviceId
                      AND emotion_block.id < :lastId
                    ORDER BY emotion_block.id DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<BlockProjection> findAllByBlockerDeviceId(
            @Param("blockerDeviceId") Long blockerDeviceId,
            @Param("lastId") long lastId,
            @Param("limit") int limit
    );
}
