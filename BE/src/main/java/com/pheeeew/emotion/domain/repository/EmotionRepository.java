package com.pheeeew.emotion.domain.repository;

import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.repository.projection.RegionEmotionSummaryProjection;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmotionRepository extends JpaRepository<Emotion, Long>, EmotionListQueryRepository, EmotionRegionQueryRepository {

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

    @Override
    List<RegionEmotionSummaryProjection> findSummariesByRegionCodes(
            List<String> regionCodes,
            EmotionSearchFilter filter,
            Long deviceId,
            Instant snapshotAt
    );

    @Override
    List<Emotion> findVisiblePageWithinBounds(
            EmotionSearchBounds bounds,
            Instant snapshotAt,
            Instant lastCreatedAt,
            long lastId,
            Long deviceId,
            UUID groupId,
            boolean contentOnly,
            int limit
    );

    @Override
    List<Emotion> findVisiblePageWithinBounds(
            EmotionSearchBounds bounds,
            Instant snapshotAt,
            Instant lastCreatedAt,
            long lastId,
            Long deviceId,
            EmotionSearchFilter filter,
            int limit
    );

    @Override
    List<Emotion> findVisiblePageWithoutBounds(
            Instant snapshotAt,
            Instant lastCreatedAt,
            long lastId,
            Long deviceId,
            EmotionSearchFilter filter,
            int limit
    );
}
