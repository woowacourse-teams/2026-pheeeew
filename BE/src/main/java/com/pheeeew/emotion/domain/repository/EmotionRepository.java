package com.pheeeew.emotion.domain.repository;

import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.repository.projection.GeneratedLocation;
import com.pheeeew.emotion.domain.repository.projection.EmotionDetailProjection;
import com.pheeeew.emotion.domain.repository.projection.EmotionListProjection;
import com.pheeeew.emotion.domain.repository.projection.EmotionMapProjection;
import com.pheeeew.emotion.domain.repository.query.EmotionQueryPeriod;
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
     * <p>{@code request_id}에는 삭제 여부와 무관하게 유니크 제약이 걸려 있다. 삭제된 한숨을 걸러내면
     * 같은 {@code requestId}로 다시 등록할 때 선조회가 비어 삽입을 시도하고, 유니크 위반 뒤의 재조회도
     * 비어 멱등 복구가 실패한다(ADR-0004, ADR-0005).
     */
    @Query("""
            SELECT s AS emotion, CASE WHEN emotionLike.id IS NOT NULL THEN true ELSE false END AS liked
            FROM Emotion s
            LEFT JOIN EmotionLike emotionLike ON emotionLike.emotionId = s.id AND emotionLike.deviceId = :deviceId
            WHERE s.requestId = :requestId
            """)
    Optional<EmotionDetailProjection> findByRequestId(
            @Param("requestId") UUID requestId,
            @Param("deviceId") Long deviceId
    );

    Optional<Emotion> findByIdAndDeletedAtIsNull(Long id);

    @Query("""
            SELECT s AS emotion, CASE WHEN emotionLike.id IS NOT NULL THEN true ELSE false END AS liked
            FROM Emotion s
            LEFT JOIN EmotionLike emotionLike ON emotionLike.emotionId = s.id AND emotionLike.deviceId = :deviceId
            WHERE s.id = :id AND s.deletedAt IS NULL
            """)
    Optional<EmotionDetailProjection> findById(@Param("id") Long id, @Param("deviceId") Long deviceId);

    @Query(
            value = """
                    WITH bounds AS (
                        SELECT ST_MakeEnvelope(
                            :#{#bounds.minLongitude()},
                            :#{#bounds.minLatitude()},
                            CASE
                                WHEN :#{#bounds.minLongitude()} < :#{#bounds.maxLongitude()}
                                    THEN :#{#bounds.maxLongitude()}
                                ELSE 180.0
                            END,
                            :#{#bounds.maxLatitude()},
                            4326
                        ) AS area
                        UNION ALL
                        SELECT ST_MakeEnvelope(
                            -180.0,
                            :#{#bounds.minLatitude()},
                            :#{#bounds.maxLongitude()},
                            :#{#bounds.maxLatitude()},
                            4326
                        ) AS area
                        WHERE :#{#bounds.minLongitude()} > :#{#bounds.maxLongitude()}
                    )
                    SELECT
                        emotion.id AS id,
                        ST_X(emotion.location) AS longitude,
                        ST_Y(emotion.location) AS latitude,
                        emotion.created_at AS "createdAt"
                    FROM sighs emotion
                    CROSS JOIN bounds
                    WHERE emotion.deleted_at IS NULL
                      AND emotion.created_at >= :#{#period.startAt()}
                      AND emotion.created_at <= :#{#period.endAt()}
                      AND emotion.location && bounds.area
                      AND ST_Intersects(emotion.location, bounds.area)
                      AND (
                          CAST(:blockerDeviceId AS BIGINT) IS NULL
                          OR NOT EXISTS (
                              SELECT 1
                              FROM sigh_blocks emotion_block
                              WHERE emotion_block.blocker_device_id = :blockerDeviceId
                                AND emotion_block.sigh_id = emotion.id
                          )
                      )
                      AND (
                          CAST(:blockerDeviceId AS BIGINT) IS NULL
                          OR NOT EXISTS (
                              SELECT 1
                              FROM device_blocks device_block
                              WHERE device_block.blocker_device_id = :blockerDeviceId
                                AND device_block.blocked_device_id = emotion.device_id
                          )
                      )
                    ORDER BY emotion.created_at DESC, emotion.id DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<EmotionMapProjection> findAllWithinBounds(
            @Param("bounds") EmotionSearchBounds bounds,
            @Param("period") EmotionQueryPeriod period,
            @Param("blockerDeviceId") Long blockerDeviceId,
            @Param("limit") int limit
    );

    @Query(
            value = """
                    WITH bounds AS (
                        SELECT ST_MakeEnvelope(
                            :#{#bounds.minLongitude()},
                            :#{#bounds.minLatitude()},
                            CASE
                                WHEN :#{#bounds.minLongitude()} < :#{#bounds.maxLongitude()}
                                    THEN :#{#bounds.maxLongitude()}
                                ELSE 180.0
                            END,
                            :#{#bounds.maxLatitude()},
                            4326
                        ) AS area
                        UNION ALL
                        SELECT ST_MakeEnvelope(
                            -180.0,
                            :#{#bounds.minLatitude()},
                            :#{#bounds.maxLongitude()},
                            :#{#bounds.maxLatitude()},
                            4326
                        ) AS area
                        WHERE :#{#bounds.minLongitude()} > :#{#bounds.maxLongitude()}
                    ), latest_emotions AS (
                        SELECT
                            emotion.id,
                            emotion.location,
                            emotion.created_at,
                            emotion.nickname,
                            emotion.memo,
                            emotion.like_count
                        FROM sighs emotion
                        CROSS JOIN bounds
                        WHERE emotion.deleted_at IS NULL
                          AND emotion.created_at >= :#{#period.startAt()}
                          AND emotion.created_at < :#{#period.endAt()}
                          AND emotion.location && bounds.area
                          AND ST_Intersects(emotion.location, bounds.area)
                          AND (
                              CAST(:blockerDeviceId AS BIGINT) IS NULL
                              OR NOT EXISTS (
                                  SELECT 1
                                  FROM sigh_blocks emotion_block
                                  WHERE emotion_block.blocker_device_id = :blockerDeviceId
                                    AND emotion_block.sigh_id = emotion.id
                              )
                          )
                          AND (
                              CAST(:blockerDeviceId AS BIGINT) IS NULL
                              OR NOT EXISTS (
                                  SELECT 1
                                  FROM device_blocks device_block
                                  WHERE device_block.blocker_device_id = :blockerDeviceId
                                    AND device_block.blocked_device_id = emotion.device_id
                              )
                          )
                        ORDER BY emotion.created_at DESC, emotion.id DESC
                        LIMIT :maxCount
                    )
                    SELECT
                        latest_emotions.id AS id,
                        ST_X(latest_emotions.location) AS longitude,
                        ST_Y(latest_emotions.location) AS latitude,
                        latest_emotions.created_at AS "createdAt",
                        latest_emotions.nickname AS nickname,
                        latest_emotions.memo AS memo,
                        latest_emotions.like_count AS "likeCount",
                        emotion_like.id IS NOT NULL AS liked
                    FROM latest_emotions
                    LEFT JOIN sigh_likes emotion_like
                      ON emotion_like.sigh_id = latest_emotions.id AND emotion_like.device_id = :deviceId
                    WHERE (latest_emotions.created_at, latest_emotions.id) < (:lastItemCreatedAt, :lastId)
                    ORDER BY latest_emotions.created_at DESC, latest_emotions.id DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<EmotionListProjection> findListWithinBounds(
            @Param("bounds") EmotionSearchBounds bounds,
            @Param("period") EmotionQueryPeriod period,
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
