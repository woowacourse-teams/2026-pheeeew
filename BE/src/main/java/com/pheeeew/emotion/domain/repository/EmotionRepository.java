package com.pheeeew.emotion.domain.repository;

import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.repository.projection.RegionEmotionSummaryProjection;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmotionRepository extends JpaRepository<Emotion, Long> {

    /**
     * 삭제 여부로 거르지 않는다.
     *
     * <p>{@code request_id}에는 삭제 여부와 무관하게 유니크 제약이 걸려 있다. 삭제된 감정을 걸러내면
     * 같은 {@code requestId}로 다시 등록할 때 선조회가 비어 삽입을 시도하고, 유니크 위반 뒤의 재조회도
     * 비어 멱등 복구가 실패한다(ADR-0004, ADR-0005).
     */
    Optional<Emotion> findByRequestId(UUID requestId);

    Optional<Emotion> findByIdAndDeletedAtIsNull(Long id);

    Optional<Emotion> findByIdAndDeviceId(Long id, Long deviceId);

    @Query("""
            SELECT emotion
            FROM Emotion emotion
            WHERE emotion.id = :id
              AND emotion.deletedAt IS NULL
              AND NOT EXISTS (
                  SELECT emotionBlock.id FROM EmotionBlock emotionBlock
                  WHERE emotionBlock.blockerDeviceId = :deviceId
                    AND emotionBlock.emotionId = emotion.id
              )
              AND NOT EXISTS (
                  SELECT deviceBlock.id FROM DeviceBlock deviceBlock
                  WHERE deviceBlock.blockerDeviceId = :deviceId
                    AND deviceBlock.blockedDeviceId = emotion.deviceId
              )
            """)
    Optional<Emotion> findVisibleById(@Param("id") Long id, @Param("deviceId") Long deviceId);

    @Query(value = """
            WITH RECURSIVE region_members(root_code, member_code) AS (
                SELECT code, code FROM regions WHERE code IN (:regionCodes)
                UNION ALL
                SELECT parent.root_code, child.code
                FROM region_members parent JOIN regions child ON child.parent_code = parent.member_code
            ), eligible AS (
                SELECT member.root_code AS region_code, emotion.state
                FROM emotions emotion
                JOIN region_members member ON member.member_code = emotion.region_code
                WHERE emotion.deleted_at IS NULL
                  AND (:contentOnly = FALSE OR emotion.memo IS NOT NULL OR emotion.audio_object_key IS NOT NULL)
                  AND emotion.created_at <= :snapshotAt
                  AND (CAST(:groupId AS UUID) IS NULL OR EXISTS (
                      SELECT 1 FROM group_stamps stamp JOIN groups stamp_group ON stamp_group.id = stamp.group_id
                      WHERE stamp.id = emotion.group_stamp_id AND stamp_group.public_id = CAST(:groupId AS UUID)
                  ))
            ), state_counts AS (
                SELECT region_code, state, count(*) AS state_count
                FROM eligible GROUP BY region_code, state
            ), ranked AS (
                SELECT region_code, state,
                    CAST(sum(state_count) OVER (PARTITION BY region_code) AS BIGINT) AS total_count,
                    row_number() OVER (
                        PARTITION BY region_code
                        ORDER BY state IS NULL, state_count DESC,
                            CASE state
                                WHEN 'ANGRY' THEN 0
                                WHEN 'DISCOURAGED' THEN 1
                                WHEN 'EXHAUSTED' THEN 2
                                WHEN 'FRUSTRATED' THEN 3
                                WHEN 'IRRITATED' THEN 4
                            END
                    ) AS state_rank
                FROM state_counts
            )
            SELECT region_code AS "regionCode", total_count AS "totalCount", state AS "representativeState"
            FROM ranked WHERE state_rank = 1
            """, nativeQuery = true)
    List<RegionEmotionSummaryProjection> findSummariesByRegionCodes(
            @Param("regionCodes") List<String> regionCodes,
            @Param("groupId") UUID groupId,
            @Param("snapshotAt") Instant snapshotAt,
            @Param("contentOnly") boolean contentOnly
    );

    @Query(value = """
            WITH bounds AS (
                SELECT ST_MakeEnvelope(
                    :#{#bounds.minLongitude()}, :#{#bounds.minLatitude()},
                    CASE WHEN :#{#bounds.minLongitude()} < :#{#bounds.maxLongitude()}
                         THEN :#{#bounds.maxLongitude()} ELSE 180.0 END,
                    :#{#bounds.maxLatitude()}, 4326
                ) AS area
                UNION ALL
                SELECT ST_MakeEnvelope(
                    -180.0, :#{#bounds.minLatitude()},
                    :#{#bounds.maxLongitude()}, :#{#bounds.maxLatitude()}, 4326
                ) AS area
                WHERE :#{#bounds.minLongitude()} > :#{#bounds.maxLongitude()}
            )
            SELECT emotion.*
            FROM emotions emotion
            CROSS JOIN bounds
            WHERE emotion.deleted_at IS NULL
              AND (:contentOnly = FALSE OR emotion.memo IS NOT NULL OR emotion.audio_object_key IS NOT NULL)
              AND emotion.created_at <= :snapshotAt
              AND (emotion.created_at, emotion.id) < (:lastCreatedAt, :lastId)
              AND (CAST(:groupId AS UUID) IS NULL OR EXISTS (
                  SELECT 1 FROM group_stamps stamp JOIN groups stamp_group ON stamp_group.id = stamp.group_id
                  WHERE stamp.id = emotion.group_stamp_id AND stamp_group.public_id = CAST(:groupId AS UUID)
              ))
              AND emotion.location && bounds.area
              AND ST_Intersects(emotion.location, bounds.area)
              AND NOT EXISTS (
                  SELECT 1 FROM emotion_blocks emotion_block
                  WHERE emotion_block.blocker_device_id = :deviceId
                    AND emotion_block.emotion_id = emotion.id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM device_blocks device_block
                  WHERE device_block.blocker_device_id = :deviceId
                    AND device_block.blocked_device_id = emotion.device_id
              )
            ORDER BY emotion.created_at DESC, emotion.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Emotion> findVisiblePageWithinBounds(
            @Param("bounds") EmotionSearchBounds bounds,
            @Param("snapshotAt") Instant snapshotAt,
            @Param("lastCreatedAt") Instant lastCreatedAt,
            @Param("lastId") long lastId,
            @Param("deviceId") Long deviceId,
            @Param("groupId") UUID groupId,
            @Param("contentOnly") boolean contentOnly,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT emotion.*
            FROM emotions emotion
            WHERE emotion.deleted_at IS NULL
              AND (emotion.memo IS NOT NULL OR emotion.audio_object_key IS NOT NULL)
              AND emotion.created_at <= :snapshotAt
              AND (emotion.created_at, emotion.id) < (:lastCreatedAt, :lastId)
              AND (CAST(:groupId AS UUID) IS NULL OR EXISTS (
                  SELECT 1 FROM group_stamps stamp JOIN groups stamp_group ON stamp_group.id = stamp.group_id
                  WHERE stamp.id = emotion.group_stamp_id AND stamp_group.public_id = CAST(:groupId AS UUID)
              ))
              AND NOT EXISTS (
                  SELECT 1 FROM emotion_blocks emotion_block
                  WHERE emotion_block.blocker_device_id = :deviceId
                    AND emotion_block.emotion_id = emotion.id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM device_blocks device_block
                  WHERE device_block.blocker_device_id = :deviceId
                    AND device_block.blocked_device_id = emotion.device_id
              )
            ORDER BY emotion.created_at DESC, emotion.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Emotion> findVisiblePageWithoutBounds(
            @Param("snapshotAt") Instant snapshotAt,
            @Param("lastCreatedAt") Instant lastCreatedAt,
            @Param("lastId") long lastId,
            @Param("deviceId") Long deviceId,
            @Param("groupId") UUID groupId,
            @Param("limit") int limit
    );

}
