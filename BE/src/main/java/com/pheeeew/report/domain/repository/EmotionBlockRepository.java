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
                        emotion_block.sigh_id AS "emotionId",
                        emotion.nickname AS nickname,
                        emotion.memo AS memo,
                        emotion_block.created_at AS "createdAt"
                    FROM sigh_blocks emotion_block
                    JOIN sighs emotion ON emotion.id = emotion_block.sigh_id
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
