package com.pheeeew.emotion.application.query;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@RequiredArgsConstructor
@Service
public class EmotionMapQueryService {

    private final Clock clock;
    private final EmotionMapDbQueryService dbQueries;
    private final RegionSummaryCache regionSummaryCache;
    private final PinCandidateCache pinCandidateCache;

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

        if (!TransactionSynchronizationManager.isActualTransactionActive() && cacheable(RegionLevel.SIDO, cursor.filter())) {
            Long deviceId = dbQueries.findValidatedDeviceId(devicePublicId, cursor.groupIds());

            PinCandidateCache.Page cached = pinCandidateCache.find(cursor, encodedCursor == null, deviceId);
            if (cached != null) {
                return dbQueries.findPinPage(cached);
            }
        }

        return dbQueries.findMap(cursor, devicePublicId, true);
    }

    private boolean cacheable(RegionLevel level, EmotionSearchFilter filter) {
        return level != RegionLevel.EMD && filter.scope() == EmotionSearchScope.ALL
                && filter.contentType() == EmotionContentFilter.ALL && filter.startDate() == null;
    }

    private Instant currentSnapshotAt() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
