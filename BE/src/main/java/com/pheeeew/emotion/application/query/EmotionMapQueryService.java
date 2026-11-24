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
import com.pheeeew.emotion.infra.metrics.MapReadObservation;
import com.pheeeew.emotion.infra.metrics.MapReadObservation.Decision;
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
    /** 줌 16부터는 화면이 해상도 9 셀 두세 개 크기라 묶어도 줄지 않는다. 그때는 핀을 페이지로 준다. */
    private static final double CLUSTER_MAX_ZOOM = 16;
    private static final double CLUSTER_FINE_MIN_ZOOM = 14;
    private static final double CLUSTER_MIDDLE_MIN_ZOOM = 12;
    private static final int CLUSTER_FINE_RESOLUTION = 9;
    private static final int CLUSTER_MIDDLE_RESOLUTION = 8;
    private static final int CLUSTER_COARSE_RESOLUTION = 7;

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
        MapReadObservation.query(filter.startDate(), filter.endDate(), true, defaultFilter(filter), clock);
        MapReadObservation.filters(filter.scope().name(), filter.contentType().name());
        Decision bypass = bypassReason(level, filter);
        if (bypass != null) {
            MapReadObservation.decision(bypass);
            return dbQueries.findRegionMapSql(bounds, level, devicePublicId, filter);
        }

        MapReadObservation.decision(Decision.ELIGIBLE);
        EmotionMapDbQueryService.RegionRequest request = dbQueries.findRegionRequest(level, devicePublicId, filter);

        Long deviceId = request.deviceId();
        List<Region> regions = request.regions();
        if (regions.isEmpty()) {
            MapReadObservation.decision(Decision.EMPTY);
            return List.of();
        }

        MapReadObservation.decision(Decision.CACHE_ATTEMPT);
        Map<String, RegionEmotionSummary> summaries = regionSummaryCache.summaries(level, filter, deviceId);
        if (summaries == null) {
            MapReadObservation.decision(Decision.CACHE_FALLBACK);
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
        return findContentMapWithinBoundsV4(bounds, devicePublicId, startDate, endDate, contentType, scope, encodedCursor,
                requestedSize, null);
    }

    /** zoom을 전달한 첫 페이지가 한 페이지에 담기지 않으면 핀 대신 H3 셀 묶음을 반환한다. */
    public EmotionMapPageView findContentMapWithinBoundsV4(
            EmotionSearchBounds bounds,
            UUID devicePublicId,
            LocalDate startDate,
            LocalDate endDate,
            EmotionContentFilter contentType,
            EmotionSearchScope scope,
            String encodedCursor,
            Integer requestedSize,
            Double zoom
    ) {
        int pageSize = resolveMapPageSize(requestedSize);
        EmotionCursor cursor = encodedCursor == null
                ? EmotionCursor.initialWithinBounds(bounds, currentSnapshotAt(),
                        EmotionSearchFilter.of(startDate, endDate, contentType, scope, null))
                : EmotionCursorCodec.decodeV4WithinBounds(encodedCursor);
        cursor.validateSnapshotAt(Instant.now(clock));
        cursor.validateRequestedFilter(startDate, endDate, contentType, scope, null);
        PinCandidateCache.Page cached = findCachedPins(cursor, devicePublicId, encodedCursor == null, pageSize,
                clusterResolution(zoom));
        if (cached != null) {
            return cached.clusters().isEmpty() ? dbQueries.findPinPageV4(cached, pageSize)
                    : EmotionMapPageView.clustered(cached.clusters());
        }

        return dbQueries.findMapV4(cursor, devicePublicId, pageSize);
    }

    /** 화면 폭에 셀이 5~10개쯤 들어오는 해상도를 고른다. null이면 묶지 않는다. */
    private Integer clusterResolution(Double zoom) {
        if (zoom == null || zoom >= CLUSTER_MAX_ZOOM) {
            return null;
        }
        if (zoom < CLUSTER_MIDDLE_MIN_ZOOM) {
            return CLUSTER_COARSE_RESOLUTION;
        }

        return zoom < CLUSTER_FINE_MIN_ZOOM ? CLUSTER_MIDDLE_RESOLUTION : CLUSTER_FINE_RESOLUTION;
    }

    private EmotionMapPageView findContentMap(
            EmotionCursor cursor, UUID devicePublicId, boolean firstPage, int pageSize, Function<EmotionCursor, String> encoder
    ) {
        PinCandidateCache.Page cached = findCachedPins(cursor, devicePublicId, firstPage, pageSize, null);
        if (cached != null) {
            return dbQueries.findPinPage(cached, pageSize, encoder);
        }

        return dbQueries.findMap(cursor, devicePublicId, true, pageSize, encoder);
    }

    private PinCandidateCache.Page findCachedPins(
            EmotionCursor cursor, UUID devicePublicId, boolean firstPage, int pageSize, Integer clusterResolution
    ) {
        EmotionSearchFilter filter = cursor.filter();
        MapReadObservation.query(filter.startDate(), filter.endDate(), firstPage, defaultFilter(filter), clock);
        MapReadObservation.filters(filter.scope().name(), filter.contentType().name());
        Decision bypass = bypassReason(RegionLevel.SIDO, filter);
        if (bypass == null) {
            MapReadObservation.decision(Decision.ELIGIBLE);
            Long deviceId = dbQueries.findValidatedDeviceId(devicePublicId, cursor.groupIds());

            MapReadObservation.decision(Decision.CACHE_ATTEMPT);
            PinCandidateCache.Page cached = pinCandidateCache.find(cursor, firstPage, deviceId, pageSize,
                    clusterResolution);
            if (cached != null) {
                return cached;
            }
            MapReadObservation.decision(Decision.CACHE_FALLBACK);
        } else {
            MapReadObservation.decision(bypass);
        }

        return null;
    }

    private Decision bypassReason(RegionLevel level, EmotionSearchFilter filter) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return Decision.BYPASS_TRANSACTION;
        }
        if (level == RegionLevel.EMD) {
            return Decision.BYPASS_LEVEL;
        }
        return filter.scope() == EmotionSearchScope.GROUP ? Decision.BYPASS_FILTER : null;
    }

    private boolean defaultFilter(EmotionSearchFilter filter) {
        return filter.scope() == EmotionSearchScope.ALL && filter.contentType() == EmotionContentFilter.ALL;
    }

    private Instant currentSnapshotAt() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
