package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_REGION_DATA_UNAVAILABLE;
import static com.pheeeew.groups.exception.GroupErrorCode.GROUP_NOT_FOUND;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.EmotionCursorCodec;
import com.pheeeew.emotion.application.mapcache.PinCandidateCache;
import com.pheeeew.emotion.application.dto.EmotionCursor;
import com.pheeeew.emotion.application.dto.EmotionMapItemView;
import com.pheeeew.emotion.application.dto.EmotionMapPageView;
import com.pheeeew.emotion.application.dto.EmotionRegionMapItemView;
import com.pheeeew.emotion.application.dto.RegionEmotionSummary;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.repository.projection.EmotionMapPin;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.projection.RegionEmotionSummaryProjection;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class EmotionMapDbQueryService {

    private static final int MAP_PAGE_SIZE = 200;

    private final Clock clock;
    private final EmotionRepository emotionRepository;
    private final DeviceRepository deviceRepository;
    private final GroupStampRepository groupStampRepository;
    private final GroupRepository groupRepository;
    private final RegionRepository regionRepository;
    private final RegionClassifier regionClassifier;

    public RegionRequest findRegionRequest(RegionLevel level, UUID devicePublicId, EmotionSearchFilter filter) {
        Long deviceId = findValidatedDeviceId(devicePublicId, filter.groupIds());

        List<Region> regions = regionRepository.findByLevel(level);
        if (!regionRepository.isAggregationReady()) {
            throw new EmotionException(EMOTION_REGION_DATA_UNAVAILABLE);
        }

        return RegionRequest.of(deviceId, regions);
    }

    public Long findValidatedDeviceId(UUID devicePublicId, List<UUID> groupIds) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId).orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));

        validateGroups(groupIds);
        return deviceId;
    }

    public List<EmotionRegionMapItemView> findRegionMapSql(
            EmotionSearchBounds bounds, RegionLevel level, UUID devicePublicId, EmotionSearchFilter filter
    ) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId).orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
        validateGroups(filter.groupIds());

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

    public Map<String, RegionEmotionSummary> findSummariesByRegionCodes(
            List<String> regionCodes, RegionLevel level, EmotionSearchFilter filter, Long deviceId
    ) {
        if (!regionRepository.isAggregationReady()) {
            throw new EmotionException(EMOTION_REGION_DATA_UNAVAILABLE);
        }

        if (regionCodes.isEmpty()) {
            return Map.of();
        }

        Instant snapshotAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

        List<RegionEmotionSummaryProjection> summaries = switch (level) {
            case SIDO, SIGUNGU -> emotionRepository.findNationalSummaries(level, filter, deviceId, snapshotAt);
            case EMD -> emotionRepository.findSummariesByRegionCodes(regionCodes, level, filter, deviceId, snapshotAt);
        };

        return summaries.stream()
                .collect(Collectors.toMap(RegionEmotionSummaryProjection::regionCode, RegionEmotionSummary::from));
    }

    public EmotionMapPageView findMap(EmotionCursor cursor, UUID devicePublicId, boolean contentOnly) {
        return findMap(cursor, devicePublicId, contentOnly, MAP_PAGE_SIZE, EmotionCursorCodec::encode);
    }

    public EmotionMapPageView findMap(
            EmotionCursor cursor, UUID devicePublicId, boolean contentOnly, int pageSize,
            Function<EmotionCursor, String> encoder
    ) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId).orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));

        if (contentOnly) {
            validateGroups(cursor.groupIds());
        }

        List<Emotion> found = findMapEmotions(cursor, deviceId, contentOnly, pageSize);

        boolean hasNext = found.size() > pageSize;
        List<Emotion> page = hasNext ? found.subList(0, pageSize) : found;

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

        String nextCursor = encoder.apply(cursor.next(
                page.getLast().getCreatedAt(), page.getLast().getId()));

        return EmotionMapPageView.of(items, true, nextCursor);
    }

    public EmotionMapPageView findPinPage(
            PinCandidateCache.Page cached, int pageSize, Function<EmotionCursor, String> encoder
    ) {
        boolean hasNext = cached.pins().size() > pageSize;
        List<EmotionMapPin> page = hasNext ? cached.pins().subList(0, pageSize) : cached.pins();

        List<Long> stampIds = page.stream().map(EmotionMapPin::stampId).filter(Objects::nonNull).distinct().toList();
        Map<Long, GroupStamp> stamps = stampIds.isEmpty() ? Map.of()
                : groupStampRepository.findAllWithGroupByIdIn(stampIds).stream()
                        .collect(Collectors.toMap(GroupStamp::getId, stamp -> stamp));

        List<EmotionMapItemView> items = page.stream().map(pin -> {
            GroupStamp stamp = pin.stampId() == null ? null : stamps.get(pin.stampId());
            return EmotionMapItemView.of(pin.id(), pin.longitude(), pin.latitude(), pin.createdAt(), pin.state(),
                    pin.rotationDegrees(), stamp == null ? null : GroupStampResult.from(stamp),
                    stamp == null ? null : stamp.getGroup().getPublicId());
        }).toList();

        if (!hasNext) {
            return EmotionMapPageView.of(items, false, null);
        }

        EmotionMapPin last = page.getLast();
        return EmotionMapPageView.of(items, true,
                encoder.apply(cached.cursor().next(last.createdAt(), last.id())));
    }

    private void validateGroups(List<UUID> groupIds) {
        if (!groupIds.isEmpty() && groupRepository.countByPublicIdInAndDeletedAtIsNull(groupIds) != groupIds.size()) {
            throw new GroupException(GROUP_NOT_FOUND);
        }
    }

    private List<Emotion> findMapEmotions(EmotionCursor cursor, Long deviceId, boolean contentOnly, int pageSize) {
        if (contentOnly) {
            return emotionRepository.findVisiblePageWithinBounds(cursor.bounds(), cursor.snapshotAt(),
                    cursor.lastItemCreatedAt(), cursor.lastId(), deviceId, cursor.filter(), pageSize + 1);
        }

        return emotionRepository.findVisiblePageWithinBounds(cursor.bounds(), cursor.snapshotAt(),
                cursor.lastItemCreatedAt(), cursor.lastId(), deviceId, cursor.filter(), false, pageSize + 1);
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

    public record RegionRequest(Long deviceId, List<Region> regions) {

        private static RegionRequest of(Long deviceId, List<Region> regions) {
            return new RegionRequest(deviceId, List.copyOf(regions));
        }
    }
}
