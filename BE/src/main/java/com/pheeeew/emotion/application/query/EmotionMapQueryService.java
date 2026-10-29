package com.pheeeew.emotion.application.query;

import com.pheeeew.common.exception.CommonErrorCode;
import com.pheeeew.common.exception.PheeeewException;
import com.pheeeew.emotion.application.EmotionCursorCodec;
import com.pheeeew.emotion.application.mapcache.RegionSummaryCache;
import com.pheeeew.emotion.application.mapcache.PinCandidateCache;
import com.pheeeew.emotion.application.dto.EmotionCursor;
import com.pheeeew.emotion.application.dto.EmotionMapPageView;
import com.pheeeew.emotion.application.dto.EmotionRegionMapItemView;
import com.pheeeew.emotion.application.dto.RegionEmotionSummary;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import com.pheeeew.region.domain.Region;
import com.pheeeew.region.domain.RegionLevel;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class EmotionMapQueryService {

    private static final int DEFAULT_PAGE_SIZE = 200;
    private static final int EXPERIMENT_MAX_PAGE_SIZE = 1000;

    private final Clock clock;
    private final EmotionMapDbQueryService dbQueries;
    private final RegionSummaryCache regionSummaryCache;
    private final PinCandidateCache pinCandidateCache;
    private final int maximumPageSize;

    public EmotionMapQueryService(
            Clock clock,
            EmotionMapDbQueryService dbQueries,
            RegionSummaryCache regionSummaryCache,
            PinCandidateCache pinCandidateCache,
            @Value("${pheeeew.emotion-map.max-page-size:200}") int maximumPageSize
    ) {
        if (maximumPageSize < DEFAULT_PAGE_SIZE || maximumPageSize > EXPERIMENT_MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("지도 핀 조회 상한은 200 이상 1000 이하여야 합니다.");
        }
        this.clock = clock;
        this.dbQueries = dbQueries;
        this.regionSummaryCache = regionSummaryCache;
        this.pinCandidateCache = pinCandidateCache;
        this.maximumPageSize = maximumPageSize;
    }

    int resolveMapPageSize(Integer requestedSize) {
        int pageSize = requestedSize == null ? DEFAULT_PAGE_SIZE : requestedSize;
        if (pageSize < 1 || pageSize > maximumPageSize) {
            throw new PheeeewException(CommonErrorCode.INVALID_REQUEST, null);
        }
        return pageSize;
    }

    public List<EmotionRegionMapItemView> findRegionMap(
            EmotionSearchBounds bounds, RegionLevel level, UUID devicePublicId, EmotionSearchFilter filter
    ) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || !cacheable(level, filter)) {
            return dbQueries.findRegionMapSql(bounds, level, devicePublicId, filter);
        }

        EmotionMapDbQueryService.RegionRequest request = dbQueries.findRegionRequest(level, devicePublicId, filter);

        Long deviceId = request.deviceId();
        List<Region> regions = request.regions();
        if (regions.isEmpty()) {
            return List.of();
        }

        Map<String, RegionEmotionSummary> summaries = regionSummaryCache.summaries(level);
        if (summaries == null) {
            summaries = dbQueries.findSummariesByRegionCodes(
                    regions.stream().map(Region::code).toList(), level, filter, deviceId);
        }

        Map<String, RegionEmotionSummary> selected = summaries;
        return regions.stream().filter(region -> selected.containsKey(region.code()))
                .map(region -> EmotionRegionMapItemView.of(region, selected.get(region.code()))).toList();
    }

    @Transactional(readOnly = true)
    public EmotionMapPageView findMapWithinBounds(EmotionSearchBounds bounds, UUID devicePublicId, UUID groupId, String encodedCursor) {
        EmotionCursor cursor = encodedCursor == null
                ? EmotionCursor.initialWithinBounds(bounds, currentSnapshotAt(),
                        EmotionSearchFilter.from(groupId == null ? null : List.of(groupId)))
                : EmotionCursorCodec.decodeWithinBounds(encodedCursor);

        cursor.validateSnapshotAt(Instant.now(clock));

        return dbQueries.findMap(cursor, devicePublicId, false);
    }

    public EmotionMapPageView findContentMapWithinBounds(
            EmotionSearchBounds bounds,
            UUID devicePublicId,
            List<UUID> groupIds,
            LocalDate startDate,
            LocalDate endDate,
            EmotionContentFilter contentType,
            EmotionSearchScope scope,
            String encodedCursor
    ) {
        EmotionCursor cursor = encodedCursor == null
                ? EmotionCursor.initialWithinBounds(bounds, currentSnapshotAt(),
                        EmotionSearchFilter.of(startDate, endDate, contentType, scope,
                                groupIds))
                : EmotionCursorCodec.decodeFilteredWithinBounds(encodedCursor);

        cursor.validateSnapshotAt(Instant.now(clock));
        cursor.validateRequestedFilter(startDate, endDate, contentType, scope, groupIds);

        return findContentMap(cursor, devicePublicId, encodedCursor == null, DEFAULT_PAGE_SIZE, EmotionCursorCodec::encode);
    }

    public EmotionMapPageView findContentMapWithinBoundsV4(
            EmotionSearchBounds bounds,
            UUID devicePublicId,
            LocalDate startDate,
            LocalDate endDate,
            EmotionContentFilter contentType,
            EmotionSearchScope scope,
            String encodedCursor
    ) {
        return findContentMapWithinBoundsV4(bounds, devicePublicId, startDate, endDate, contentType, scope, encodedCursor, null);
    }

    public EmotionMapPageView findContentMapWithinBoundsV4(
            EmotionSearchBounds bounds,
            UUID devicePublicId,
            LocalDate startDate,
            LocalDate endDate,
            EmotionContentFilter contentType,
            EmotionSearchScope scope,
            String encodedCursor,
            Integer requestedSize
    ) {
        int pageSize = resolveMapPageSize(requestedSize);
        EmotionCursor cursor = encodedCursor == null
                ? EmotionCursor.initialWithinBounds(bounds, currentSnapshotAt(),
                        EmotionSearchFilter.of(startDate, endDate, contentType, scope, null))
                : EmotionCursorCodec.decodeV4WithinBounds(encodedCursor);
        cursor.validateSnapshotAt(Instant.now(clock));
        cursor.validateRequestedFilter(startDate, endDate, contentType, scope, null);
        return findContentMap(cursor, devicePublicId, encodedCursor == null, pageSize, EmotionCursorCodec::encodeV4);
    }

    private EmotionMapPageView findContentMap(
            EmotionCursor cursor, UUID devicePublicId, boolean firstPage, int pageSize, Function<EmotionCursor, String> encoder
    ) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() && cacheable(RegionLevel.SIDO, cursor.filter())) {
            Long deviceId = dbQueries.findValidatedDeviceId(devicePublicId, cursor.groupIds());

            PinCandidateCache.Page cached = pinCandidateCache.find(cursor, firstPage, deviceId, pageSize);
            if (cached != null) {
                return dbQueries.findPinPage(cached, pageSize, encoder);
            }
        }

        return dbQueries.findMap(cursor, devicePublicId, true, pageSize, encoder);
    }

    private boolean cacheable(RegionLevel level, EmotionSearchFilter filter) {
        return level != RegionLevel.EMD && filter.scope() == EmotionSearchScope.ALL
                && filter.contentType() == EmotionContentFilter.ALL && filter.startDate() == null;
    }

    private Instant currentSnapshotAt() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
