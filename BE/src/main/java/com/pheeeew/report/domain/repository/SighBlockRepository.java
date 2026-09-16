package com.pheeeew.report.domain.repository;

import com.pheeeew.report.domain.SighBlock;
import com.pheeeew.report.domain.repository.projection.BlockProjection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SighBlockRepository extends JpaRepository<SighBlock, Long> {

    Optional<SighBlock> findByBlockerDeviceIdAndSighId(Long blockerDeviceId, Long sighId);

    void deleteByBlockerDeviceIdAndSighId(Long blockerDeviceId, Long sighId);

    @Query(
            value = """
                    SELECT
                        sigh_block.id AS "blockId",
                        sigh_block.sigh_id AS "sighId",
                        sigh.nickname AS nickname,
                        sigh.memo AS memo,
                        sigh_block.created_at AS "createdAt"
                    FROM sigh_blocks sigh_block
                    JOIN sighs sigh ON sigh.id = sigh_block.sigh_id
                    WHERE sigh_block.blocker_device_id = :blockerDeviceId
                      AND sigh_block.id < :lastId
                    ORDER BY sigh_block.id DESC
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
