package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_REGION_DATA_UNAVAILABLE;
import static com.pheeeew.groups.exception.GroupErrorCode.GROUP_NOT_FOUND;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.EmotionCursorCodec;
import com.pheeeew.emotion.application.dto.EmotionCursor;
import com.pheeeew.emotion.application.dto.EmotionMapItemView;
import com.pheeeew.emotion.application.dto.EmotionMapPageView;
import com.pheeeew.emotion.application.dto.EmotionRegionMapItemView;
import com.pheeeew.emotion.application.dto.RegionEmotionSummary;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.projection.RegionEmotionSummaryProjection;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.groups.application.dto.GroupStampResult;
import com.pheeeew.groups.domain.GroupStamp;
import com.pheeeew.groups.domain.repository.GroupStampRepository;
import com.pheeeew.groups.domain.repository.GroupRepository;
import com.pheeeew.groups.exception.GroupException;
import com.pheeeew.region.application.RegionClassifier;
import com.pheeeew.region.domain.Region;
import com.pheeeew.region.domain.RegionLevel;
import com.pheeeew.region.domain.repository.RegionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class EmotionMapQueryService {

    private static final int MAP_PAGE_SIZE = 200;

    private final Clock clock;
    private final EmotionRepository emotionRepository;
    private final DeviceRepository deviceRepository;
    private final GroupStampRepository groupStampRepository;
    private final GroupRepository groupRepository;
    private final RegionRepository regionRepository;
    private final RegionClassifier regionClassifier;

    public List<EmotionRegionMapItemView> findRegionMap(
            EmotionSearchBounds bounds, RegionLevel level, UUID devicePublicId, EmotionSearchFilter filter
    ) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId).orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
        validateGroup(filter.groupId());

        List<Region> regions = switch (level) {
            case SIDO, SIGUNGU -> regionRepository.findByLevel(level);
            case EMD -> regionClassifier.findIntersectingRegions(bounds, level);
        };
        List<String> regionCodes = regions.stream()
                .map(Region::code)
                .toList();

        Map<String, RegionEmotionSummary> summaries = findSummariesByRegionCodes(regionCodes, level, filter, deviceId);

        return regions.stream()
                .filter(region -> summaries.containsKey(region.code()))
                .map(region -> EmotionRegionMapItemView.of(region, summaries.get(region.code())))
                .toList();
    }

    public EmotionMapPageView findMapWithinBounds(EmotionSearchBounds bounds, UUID devicePublicId, UUID groupId, String encodedCursor) {
        EmotionCursor cursor = encodedCursor == null
                ? EmotionCursor.initialWithinBounds(bounds, currentSnapshotAt(), groupId)
                : EmotionCursorCodec.decodeWithinBounds(encodedCursor);

        cursor.validateSnapshotAt(Instant.now(clock));

        return findMap(cursor, devicePublicId, false);
    }

    public EmotionMapPageView findContentMapWithinBounds(
            EmotionSearchBounds bounds,
            UUID devicePublicId,
            UUID groupId,
            LocalDate startDate,
            LocalDate endDate,
            EmotionContentFilter contentType,
            EmotionSearchScope scope,
            String encodedCursor
    ) {
        EmotionCursor cursor = encodedCursor == null
                ? EmotionCursor.initialWithinBounds(bounds, currentSnapshotAt(), groupId).withFilter(
                        EmotionSearchFilter.of(startDate, endDate, contentType, scope, groupId))
                : EmotionCursorCodec.decodeFilteredWithinBounds(encodedCursor);

        cursor.validateSnapshotAt(Instant.now(clock));
        cursor.validateRequestedFilter(startDate, endDate, contentType, scope, groupId);

        return findMap(cursor, devicePublicId, true);
    }

    private void validateGroup(UUID groupId) {
        if (groupId != null && groupRepository.findByPublicIdAndDeletedAtIsNull(groupId).isEmpty()) {
            throw new GroupException(GROUP_NOT_FOUND);
        }
    }

    private Map<String, RegionEmotionSummary> findSummariesByRegionCodes(
            List<String> regionCodes, RegionLevel level, EmotionSearchFilter filter, Long deviceId
    ) {
        if (!regionRepository.isAggregationReady()) {
            throw new EmotionException(EMOTION_REGION_DATA_UNAVAILABLE);
        }

        if (regionCodes.isEmpty()) {
            return Map.of();
        }

        Instant snapshotAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

        return emotionRepository.findSummariesByRegionCodes(regionCodes, level, filter, deviceId, snapshotAt).stream()
                .collect(Collectors.toMap(RegionEmotionSummaryProjection::regionCode, RegionEmotionSummary::from));
    }

    private Instant currentSnapshotAt() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private EmotionMapPageView findMap(EmotionCursor cursor, UUID devicePublicId, boolean contentOnly) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId).orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));

        if (contentOnly) {
            validateGroup(cursor.groupId());
        }

        List<Emotion> found = findMapEmotions(cursor, deviceId, contentOnly);

        boolean hasNext = found.size() > MAP_PAGE_SIZE;
        List<Emotion> page = hasNext ? found.subList(0, MAP_PAGE_SIZE) : found;

        Map<Long, GroupStampResult> stamps = findStamps(page);
        List<EmotionMapItemView> items = page.stream().map(emotion -> {
            GroupStampResult stamp = emotion.getGroupStamp() == null
                    ? null
                    : stamps.get(emotion.getGroupStamp().getId());

            return EmotionMapItemView.of(emotion, stamp);
        }).toList();

        if (!hasNext) {
            return EmotionMapPageView.of(items, false, null);
        }

        String nextCursor = EmotionCursorCodec.encode(cursor.next(
                page.getLast().getCreatedAt(), page.getLast().getId()));

        return EmotionMapPageView.of(items, true, nextCursor);
    }

    private List<Emotion> findMapEmotions(EmotionCursor cursor, Long deviceId, boolean contentOnly) {
        if (contentOnly) {
            return emotionRepository.findVisiblePageWithinBounds(cursor.bounds(), cursor.snapshotAt(),
                    cursor.lastItemCreatedAt(), cursor.lastId(), deviceId, cursor.filter(), MAP_PAGE_SIZE + 1);
        }

        return emotionRepository.findVisiblePageWithinBounds(cursor.bounds(), cursor.snapshotAt(),
                cursor.lastItemCreatedAt(), cursor.lastId(), deviceId, cursor.groupId(), false, MAP_PAGE_SIZE + 1);
    }

    private Map<Long, GroupStampResult> findStamps(List<Emotion> page) {
        List<Long> stampIds = page.stream().map(Emotion::getGroupStamp).filter(Objects::nonNull)
                .map(GroupStamp::getId).distinct().toList();

        if (stampIds.isEmpty()) {
            return Map.of();
        }

        return groupStampRepository.findAllWithGroupByIdIn(stampIds).stream()
                .collect(Collectors.toMap(GroupStamp::getId, GroupStampResult::from));
    }
}
