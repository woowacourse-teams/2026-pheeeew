package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_NOT_VISIBLE;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_PLAYBACK_UNAVAILABLE;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_REGION_DATA_UNAVAILABLE;

import com.pheeeew.device.domain.Device;
import com.pheeeew.emotion.application.AudioUrlIssuer;
import com.pheeeew.emotion.application.AudioUrlIssuer.PlaybackUrl;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.dto.EmotionEmojiResult;
import com.pheeeew.emotion.application.EmotionCursorCodec;
import com.pheeeew.emotion.application.dto.EmotionCursor;
import com.pheeeew.emotion.application.dto.EmotionPageView;
import com.pheeeew.emotion.application.dto.EmotionMapItemView;
import com.pheeeew.emotion.application.dto.EmotionMapPageView;
import com.pheeeew.emotion.application.dto.EmotionDetailView;
import com.pheeeew.emotion.application.dto.RegionEmotionSummary;
import com.pheeeew.emotion.application.dto.EmotionRegionMapItemView;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.domain.repository.EmotionEmojiRepository;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.projection.EmotionEmojiCountProjection;
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
import java.util.Objects;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class EmotionQueryService {

    private static final int WITHIN_BOUNDS_PAGE_SIZE = 20;
    private static final int WITHOUT_BOUNDS_PAGE_SIZE = 50;
    private static final int MAP_PAGE_SIZE = 200;

    private final ObjectProvider<AudioUrlIssuer> audioUrlIssuer;
    private final Clock clock;
    private final EmotionRepository emotionRepository;
    private final DeviceRepository deviceRepository;
    private final EmotionEmojiRepository emotionEmojiRepository;
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

    public Map<String, RegionEmotionSummary> findSummariesByRegionCodes(List<String> regionCodes, UUID groupId) {
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

    public EmotionDetailView findById(Long emotionId, UUID devicePublicId) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));

        Emotion emotion = emotionRepository.findVisibleById(emotionId, deviceId)
                .orElseThrow(() -> new EmotionException(EMOTION_NOT_VISIBLE));

        Map<Long, String> nicknames = findNicknames(List.of(emotion));
        GroupStampResult stamp = null;
        if (emotion.getGroupStamp() != null) {
            stamp = GroupStampResult.from(emotion.getGroupStamp());
        }

        return EmotionDetailView.of(emotion, findEmojis(emotionId, deviceId), issuePlaybackUrl(emotion),
                stamp, deviceId, findAuthorNickname(emotion, nicknames));
    }

    public EmotionPageView findListWithinBounds(EmotionSearchBounds bounds, UUID devicePublicId, UUID groupId, String encodedCursor) {
        EmotionCursor cursor = encodedCursor == null
                ? EmotionCursor.initialWithinBounds(bounds, currentSnapshotAt(), groupId)
                : EmotionCursorCodec.decodeWithinBounds(encodedCursor);

        cursor.validateSnapshotAt(Instant.now(clock));

        return findList(cursor, devicePublicId);
    }

    public EmotionPageView findListWithoutBounds(UUID devicePublicId, UUID groupId, String encodedCursor) {
        EmotionCursor cursor = encodedCursor == null
                ? EmotionCursor.initialWithoutBounds(currentSnapshotAt(), groupId)
                : EmotionCursorCodec.decodeWithoutBounds(encodedCursor);

        cursor.validateSnapshotAt(Instant.now(clock));
        cursor.validateRequestedGroup(groupId);

        return findList(cursor, devicePublicId);
    }

    public EmotionMapPageView findMapWithinBounds(EmotionSearchBounds bounds, UUID devicePublicId, UUID groupId, String encodedCursor) {
        EmotionCursor cursor = encodedCursor == null
                ? EmotionCursor.initialWithinBounds(bounds, currentSnapshotAt(), groupId)
                : EmotionCursorCodec.decodeWithinBounds(encodedCursor);

        cursor.validateSnapshotAt(Instant.now(clock));

        return findMap(cursor, devicePublicId);
    }

    private Instant currentSnapshotAt() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private Map<Long, String> findNicknames(List<Emotion> page) {
        List<Long> authorIds = page.stream().filter(emotion -> !emotion.isAnonymous())
                .map(Emotion::getDeviceId).filter(Objects::nonNull).distinct().toList();

        if (authorIds.isEmpty()) {
            return Map.of();
        }

        return deviceRepository.findAllById(authorIds).stream().filter(device -> device.getNickname() != null)
                .collect(Collectors.toMap(Device::getId, Device::getNickname));
    }

    private String findAuthorNickname(Emotion emotion, Map<Long, String> nicknames) {
        if (emotion.isAnonymous()) {
            return null;
        }

        return nicknames.get(emotion.getDeviceId());
    }

    private PlaybackUrl issuePlaybackUrl(Emotion emotion) {
        if (!emotion.getContent().hasAudio()) {
            return null;
        }

        AudioUrlIssuer issuer = audioUrlIssuer.getIfAvailable();
        if (issuer == null) {
            throw new EmotionException(EMOTION_AUDIO_PLAYBACK_UNAVAILABLE);
        }

        try {
            PlaybackUrl result = issuer.issuePlayback(emotion.getContent().getAudio().getObjectKey());

            if (result == null || result.playbackUrl() == null || result.playbackUrl().isBlank()
                    || result.expiresAt() == null || !result.expiresAt().isAfter(Instant.now(clock))) {
                throw new IllegalStateException("유효한 재생 URL과 만료 시각이 필요합니다.");
            }

            return result;
        } catch (RuntimeException exception) {
            throw new EmotionException(EMOTION_AUDIO_PLAYBACK_UNAVAILABLE, exception);
        }
    }

    private EmotionPageView findList(EmotionCursor cursor, UUID devicePublicId) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId).orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));

        int pageSize = cursor.bounds() == null ? WITHOUT_BOUNDS_PAGE_SIZE : WITHIN_BOUNDS_PAGE_SIZE;
        List<Emotion> found = findListEmotions(cursor, deviceId, pageSize + 1);

        boolean hasNext = found.size() > pageSize;
        List<Emotion> page = hasNext ? found.subList(0, pageSize) : found;

        List<EmotionDetailView> items = toListItems(page, deviceId);
        String nextCursor = hasNext ? EmotionCursorCodec.encode(cursor.next(
                page.getLast().getCreatedAt(), page.getLast().getId())) : null;

        return EmotionPageView.of(items, hasNext, nextCursor);
    }

    private List<Emotion> findListEmotions(EmotionCursor cursor, Long deviceId, int limit) {
        if (cursor.bounds() == null) {
            return emotionRepository.findVisiblePageWithoutBounds(cursor.snapshotAt(),
                    cursor.lastItemCreatedAt(), cursor.lastId(), deviceId, cursor.groupId(), limit);
        }

        return emotionRepository.findVisiblePageWithinBounds(cursor.bounds(), cursor.snapshotAt(),
                cursor.lastItemCreatedAt(), cursor.lastId(), deviceId, cursor.groupId(), true, limit);
    }

    private List<EmotionDetailView> toListItems(List<Emotion> page, Long deviceId) {
        Map<Long, EnumMap<EmojiType, EmotionEmojiResult>> emojis = findEmojisForPage(page, deviceId);
        Map<Long, GroupStampResult> stamps = findStamps(page);
        Map<Long, String> nicknames = findNicknames(page);

        return page.stream().map(emotion -> {
            GroupStampResult stamp = null;
            if (emotion.getGroupStamp() != null) {
                stamp = stamps.get(emotion.getGroupStamp().getId());
            }

            return EmotionDetailView.of(emotion, List.copyOf(emojis.get(emotion.getId()).values()),
                    issuePlaybackUrl(emotion), stamp, deviceId, findAuthorNickname(emotion, nicknames));
        }).toList();
    }

    private Map<Long, EnumMap<EmojiType, EmotionEmojiResult>> findEmojisForPage(List<Emotion> page, Long deviceId) {
        Map<Long, EnumMap<EmojiType, EmotionEmojiResult>> counts = new HashMap<>();
        for (Emotion emotion : page) {
            counts.put(emotion.getId(), emptyEmojis());
        }

        if (!page.isEmpty()) {
            for (var count : emotionEmojiRepository.findCountsForPage(page.stream().map(Emotion::getId).toList(), deviceId)) {
                EmojiType type = EmojiType.valueOf(count.getEmojiType());
                counts.get(count.getEmotionId()).put(type,
                        EmotionEmojiResult.of(type, count.getSelectionCount(), count.getSelected()));
            }
        }

        return counts;
    }

    private EmotionMapPageView findMap(EmotionCursor cursor, UUID devicePublicId) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId).orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));

        List<Emotion> found = emotionRepository.findVisiblePageWithinBounds(cursor.bounds(), cursor.snapshotAt(),
                cursor.lastItemCreatedAt(), cursor.lastId(), deviceId, cursor.groupId(), false, MAP_PAGE_SIZE + 1);

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

    private EnumMap<EmojiType, EmotionEmojiResult> emptyEmojis() {
        EnumMap<EmojiType, EmotionEmojiResult> results = new EnumMap<>(EmojiType.class);
        for (EmojiType type : EmojiType.values()) {
            results.put(type, EmotionEmojiResult.of(type, 0, false));
        }

        return results;
    }

    private List<EmotionEmojiResult> findEmojis(Long emotionId, Long deviceId) {
        EnumMap<EmojiType, EmotionEmojiResult> results = emptyEmojis();
        for (EmotionEmojiCountProjection count : emotionEmojiRepository.findCounts(emotionId, deviceId)) {
            EmojiType type = EmojiType.valueOf(count.getEmojiType());
            results.put(type, EmotionEmojiResult.of(type, count.getSelectionCount(), count.getSelected()));
        }

        return List.copyOf(results.values());
    }
}
