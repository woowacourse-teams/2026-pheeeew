package com.pheeeew.report.domain.repository;

import com.pheeeew.report.domain.DeviceBlock;
import com.pheeeew.report.domain.repository.projection.BlockProjection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceBlockRepository extends JpaRepository<DeviceBlock, Long> {

    Optional<DeviceBlock> findByBlockerDeviceIdAndBlockedDeviceId(Long blockerDeviceId, Long blockedDeviceId);

    void deleteByIdAndBlockerDeviceId(Long id, Long blockerDeviceId);

    @Query(
            value = """
                    SELECT
                        device_block.id AS "blockId",
                        device_block.origin_emotion_id AS "emotionId",
                        CASE WHEN emotion.anonymous THEN '익명'
                             ELSE COALESCE(author.nickname, '익명') END AS nickname,
                        emotion.memo AS memo,
                        device_block.created_at AS "createdAt"
                    FROM device_blocks device_block
                    JOIN emotions emotion ON emotion.id = device_block.origin_emotion_id
                    LEFT JOIN devices author ON author.id = emotion.device_id AND NOT emotion.anonymous
                    WHERE device_block.blocker_device_id = :blockerDeviceId
                      AND device_block.id < :lastId
                    ORDER BY device_block.id DESC
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
