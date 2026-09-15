package com.pheeeew.sigh.domain.repository;

import com.pheeeew.sigh.domain.Sigh;
import com.pheeeew.sigh.domain.repository.projection.GeneratedLocation;
import com.pheeeew.sigh.domain.repository.projection.SighDetailProjection;
import com.pheeeew.sigh.domain.repository.projection.SighListProjection;
import com.pheeeew.sigh.domain.repository.projection.SighMapProjection;
import com.pheeeew.sigh.domain.repository.query.SighQueryPeriod;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SighRepository extends JpaRepository<Sigh, Long> {

    /**
     * 삭제 여부로 거르지 않는다.
     *
     * <p>{@code request_id}에는 삭제 여부와 무관하게 유니크 제약이 걸려 있다. 삭제된 한숨을 걸러내면
     * 같은 {@code requestId}로 다시 등록할 때 선조회가 비어 삽입을 시도하고, 유니크 위반 뒤의 재조회도
     * 비어 멱등 복구가 실패한다(ADR-0004, ADR-0005).
     */
    @Query("""
            SELECT s AS sigh, CASE WHEN sighLike.id IS NOT NULL THEN true ELSE false END AS liked
            FROM Sigh s
            LEFT JOIN SighLike sighLike ON sighLike.sighId = s.id AND sighLike.deviceId = :deviceId
            WHERE s.requestId = :requestId
            """)
    Optional<SighDetailProjection> findByRequestId(
            @Param("requestId") UUID requestId,
            @Param("deviceId") Long deviceId
    );

    Optional<Sigh> findByIdAndDeletedAtIsNull(Long id);

    @Query("""
            SELECT s AS sigh, CASE WHEN sighLike.id IS NOT NULL THEN true ELSE false END AS liked
            FROM Sigh s
            LEFT JOIN SighLike sighLike ON sighLike.sighId = s.id AND sighLike.deviceId = :deviceId
            WHERE s.id = :id AND s.deletedAt IS NULL
            """)
    Optional<SighDetailProjection> findById(@Param("id") Long id, @Param("deviceId") Long deviceId);

    @Query(
            value = """
                    WITH bounds AS (
                        SELECT ST_MakeEnvelope(
                            :minLongitude,
                            :minLatitude,
                            CASE
                                WHEN :minLongitude < :maxLongitude THEN :maxLongitude
                                ELSE 180.0
                            END,
                            :maxLatitude,
                            4326
                        ) AS area
                        UNION ALL
                        SELECT ST_MakeEnvelope(
                            -180.0,
                            :minLatitude,
                            :maxLongitude,
                            :maxLatitude,
                            4326
                        ) AS area
                        WHERE :minLongitude > :maxLongitude
                    )
                    SELECT
                        sigh.id AS id,
                        ST_X(sigh.location) AS longitude,
                        ST_Y(sigh.location) AS latitude,
                        sigh.created_at AS "createdAt"
                    FROM sighs sigh
                    CROSS JOIN bounds
                    WHERE sigh.deleted_at IS NULL
                      AND sigh.created_at >= :#{#period.startAt()}
                      AND sigh.created_at <= :#{#period.endAt()}
                      AND sigh.location && bounds.area
                      AND ST_Intersects(sigh.location, bounds.area)
                      AND (
                          CAST(:blockerDeviceId AS BIGINT) IS NULL
                          OR NOT EXISTS (
                              SELECT 1
                              FROM sigh_blocks sigh_block
                              WHERE sigh_block.blocker_device_id = :blockerDeviceId
                                AND sigh_block.sigh_id = sigh.id
                          )
                      )
                      AND (
                          CAST(:blockerDeviceId AS BIGINT) IS NULL
                          OR NOT EXISTS (
                              SELECT 1
                              FROM device_blocks device_block
                              WHERE device_block.blocker_device_id = :blockerDeviceId
                                AND device_block.blocked_device_id = sigh.device_id
                          )
                      )
                    ORDER BY sigh.created_at DESC, sigh.id DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<SighMapProjection> findAllWithinBounds(
            @Param("minLongitude") double minLongitude,
            @Param("minLatitude") double minLatitude,
            @Param("maxLongitude") double maxLongitude,
            @Param("maxLatitude") double maxLatitude,
            @Param("period") SighQueryPeriod period,
            @Param("blockerDeviceId") Long blockerDeviceId,
            @Param("limit") int limit
    );

    @Query(
            value = """
                    WITH bounds AS (
                        SELECT ST_MakeEnvelope(
                            :minLongitude,
                            :minLatitude,
                            CASE
                                WHEN :minLongitude < :maxLongitude THEN :maxLongitude
                                ELSE 180.0
                            END,
                            :maxLatitude,
                            4326
                        ) AS area
                        UNION ALL
                        SELECT ST_MakeEnvelope(
                            -180.0,
                            :minLatitude,
                            :maxLongitude,
                            :maxLatitude,
                            4326
                        ) AS area
                        WHERE :minLongitude > :maxLongitude
                    ), latest_sighs AS (
                        SELECT
                            sigh.id,
                            sigh.location,
                            sigh.created_at,
                            sigh.nickname,
                            sigh.memo,
                            sigh.like_count
                        FROM sighs sigh
                        CROSS JOIN bounds
                        WHERE sigh.deleted_at IS NULL
                          AND sigh.created_at < :snapshotAt
                          AND sigh.location && bounds.area
                          AND ST_Intersects(sigh.location, bounds.area)
                          AND (
                              CAST(:blockerDeviceId AS BIGINT) IS NULL
                              OR NOT EXISTS (
                                  SELECT 1
                                  FROM sigh_blocks sigh_block
                                  WHERE sigh_block.blocker_device_id = :blockerDeviceId
                                    AND sigh_block.sigh_id = sigh.id
                              )
                          )
                          AND (
                              CAST(:blockerDeviceId AS BIGINT) IS NULL
                              OR NOT EXISTS (
                                  SELECT 1
                                  FROM device_blocks device_block
                                  WHERE device_block.blocker_device_id = :blockerDeviceId
                                    AND device_block.blocked_device_id = sigh.device_id
                              )
                          )
                        ORDER BY sigh.created_at DESC, sigh.id DESC
                        LIMIT :maxCount
                    )
                    SELECT
                        latest_sighs.id AS id,
                        ST_X(latest_sighs.location) AS longitude,
                        ST_Y(latest_sighs.location) AS latitude,
                        latest_sighs.created_at AS "createdAt",
                        latest_sighs.nickname AS nickname,
                        latest_sighs.memo AS memo,
                        latest_sighs.like_count AS "likeCount",
                        sigh_like.id IS NOT NULL AS liked
                    FROM latest_sighs
                    LEFT JOIN sigh_likes sigh_like
                      ON sigh_like.sigh_id = latest_sighs.id AND sigh_like.device_id = :deviceId
                    WHERE (latest_sighs.created_at, latest_sighs.id) < (:lastItemCreatedAt, :lastId)
                    ORDER BY latest_sighs.created_at DESC, latest_sighs.id DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<SighListProjection> findListWithinBounds(
            @Param("minLongitude") double minLongitude,
            @Param("minLatitude") double minLatitude,
            @Param("maxLongitude") double maxLongitude,
            @Param("maxLatitude") double maxLatitude,
            @Param("snapshotAt") Instant snapshotAt,
            @Param("lastItemCreatedAt") Instant lastItemCreatedAt,
            @Param("lastId") long lastId,
            @Param("blockerDeviceId") Long blockerDeviceId,
            @Param("maxCount") int maxCount,
            @Param("limit") int limit,
            @Param("deviceId") Long deviceId
    );

    @Query(
            value = """
                    WITH projected_center AS (
                        SELECT ST_Transform(
                            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326),
                            5179
                        ) AS center
                    ), shifted_location AS (
                        SELECT ST_SetSRID(
                            ST_MakePoint(
                                ST_X(center) + :eastingOffset,
                                ST_Y(center) + :northingOffset
                            ),
                            5179
                        ) AS location
                        FROM projected_center
                    ), display_location AS (
                        SELECT ST_Transform(location, 4326) AS location
                        FROM shifted_location
                    )
                    SELECT
                        ST_X(location) AS longitude,
                        ST_Y(location) AS latitude
                    FROM display_location
                    """,
            nativeQuery = true
    )
    GeneratedLocation findGeneratedLocation(
            @Param("longitude") double longitude,
            @Param("latitude") double latitude,
            @Param("eastingOffset") double eastingOffset,
            @Param("northingOffset") double northingOffset
    );
}
