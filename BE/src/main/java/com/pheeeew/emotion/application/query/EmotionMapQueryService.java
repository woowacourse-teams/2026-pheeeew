package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_REGION_DATA_UNAVAILABLE;

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
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.groups.application.dto.GroupStampResult;
import com.pheeeew.groups.domain.GroupStamp;
import com.pheeeew.groups.domain.repository.GroupStampRepository;
import com.pheeeew.region.application.RegionClassifier;
import com.pheeeew.region.domain.Region;
import com.pheeeew.region.domain.RegionLevel;
import com.pheeeew.region.domain.repository.RegionRepository;
import java.time.Clock;
import java.time.Instant;
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
    private final RegionRepository regionRepository;
    private final RegionClassifier regionClassifier;

    public List<EmotionRegionMapItemView> findRegionMap(EmotionSearchBounds bounds, RegionLevel level, UUID groupId) {
        List<Region> regions = regionClassifier.findIntersectingRegions(bounds, level);
        List<String> regionCodes = regions.stream()
                .map(Region::code)
                .toList();

        Map<String, RegionEmotionSummary> summaries = findSummariesByRegionCodes(regionCodes, groupId);

        return regions.stream()
                .filter(region -> summaries.containsKey(region.code()))
                .map(region -> EmotionRegionMapItemView.of(region, summaries.get(region.code())))
                .toList();
    }

    public EmotionMapPageView findMapWithinBounds(EmotionSearchBounds bounds, UUID devicePublicId, UUID groupId, String encodedCursor) {
        return findMapWithinBounds(bounds, devicePublicId, groupId, encodedCursor, false);
    }

    public EmotionMapPageView findContentMapWithinBounds(EmotionSearchBounds bounds, UUID devicePublicId, UUID groupId, String encodedCursor) {
        return findMapWithinBounds(bounds, devicePublicId, groupId, encodedCursor, true);
    }

    private Map<String, RegionEmotionSummary> findSummariesByRegionCodes(List<String> regionCodes, UUID groupId) {
        if (!regionRepository.isAggregationReady()) {
            throw new EmotionException(EMOTION_REGION_DATA_UNAVAILABLE);
        }

        if (regionCodes.isEmpty()) {
            return Map.of();
        }

        Instant snapshotAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

        return emotionRepository.findSummariesByRegionCodes(regionCodes, groupId, snapshotAt).stream()
                .collect(Collectors.toMap(RegionEmotionSummaryProjection::getRegionCode, RegionEmotionSummary::from));
    }

    private EmotionMapPageView findMapWithinBounds(EmotionSearchBounds bounds, UUID devicePublicId, UUID groupId, String encodedCursor, boolean contentOnly) {
        EmotionCursor cursor = encodedCursor == null
                ? EmotionCursor.initialWithinBounds(bounds, currentSnapshotAt(), groupId)
                : EmotionCursorCodec.decodeWithinBounds(encodedCursor);

        cursor.validateSnapshotAt(Instant.now(clock));

        return findMap(cursor, devicePublicId, contentOnly);
    }

    private Instant currentSnapshotAt() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private EmotionMapPageView findMap(EmotionCursor cursor, UUID devicePublicId, boolean contentOnly) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId).orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));

        List<Emotion> found = emotionRepository.findVisiblePageWithinBounds(cursor.bounds(), cursor.snapshotAt(),
                cursor.lastItemCreatedAt(), cursor.lastId(), deviceId, cursor.groupId(), contentOnly, MAP_PAGE_SIZE + 1);

        boolean hasNext = found.size() > MAP_PAGE_SIZE;
        List<Emotion> page = hasNext ? found.subList(0, MAP_PAGE_SIZE) : found;

        Map<Long, GroupStampResult> stamps = findStamps(page);
        List<EmotionMapItemView> items = page.stream().map(emotion -> EmotionMapItemView.of(emotion,
                emotion.getGroupStamp() == null ? null : stamps.get(emotion.getGroupStamp().getId()))).toList();
        String nextCursor = hasNext ? EmotionCursorCodec.encode(cursor.next(
                page.getLast().getCreatedAt(), page.getLast().getId())) : null;

        return EmotionMapPageView.of(items, hasNext, nextCursor);
    }

    private Map<Long, GroupStampResult> findStamps(List<Emotion> page) {
        List<Long> stampIds = page.stream().map(Emotion::getGroupStamp).filter(Objects::nonNull)
                .map(GroupStamp::getId).distinct().toList();

        return stampIds.isEmpty() ? Map.of()
                : groupStampRepository.findAllWithGroupByIdIn(stampIds).stream()
                        .collect(Collectors.toMap(GroupStamp::getId, GroupStampResult::from));
    }
}
