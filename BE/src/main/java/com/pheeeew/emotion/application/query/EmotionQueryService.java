package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_NOT_VISIBLE;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_INVALID_CURSOR;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_PLAYBACK_UNAVAILABLE;

import com.pheeeew.device.domain.Device;
import com.pheeeew.emotion.application.AudioPlaybackUrlIssuer;
import com.pheeeew.emotion.application.AudioPlaybackUrlIssuer.PlaybackUrl;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.dto.EmotionEmojiResult;
import com.pheeeew.emotion.application.EmotionListCursorCodec;
import com.pheeeew.emotion.application.dto.EmotionListCursor;
import com.pheeeew.emotion.application.dto.EmotionPageView;
import com.pheeeew.emotion.application.dto.EmotionDetailView;
import com.pheeeew.emotion.application.dto.EmotionListItemView;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.domain.repository.EmotionEmojiRepository;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.projection.EmotionEmojiCountProjection;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.groups.application.dto.GroupStampResult;
import com.pheeeew.groups.domain.GroupStamp;
import com.pheeeew.groups.domain.repository.GroupStampRepository;
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

    private static final int PAGE_SIZE = 20;

    private final ObjectProvider<AudioPlaybackUrlIssuer> playbackUrlIssuer;
    private final Clock clock;
    private final EmotionRepository emotionRepository;
    private final DeviceRepository deviceRepository;
    private final EmotionEmojiRepository emotionEmojiRepository;
    private final GroupStampRepository groupStampRepository;

    public EmotionDetailView findById(Long emotionId, UUID devicePublicId) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
        Emotion emotion = emotionRepository.findVisibleById(emotionId, deviceId)
                .orElseThrow(() -> new EmotionException(EMOTION_NOT_VISIBLE));

        return EmotionDetailView.of(emotion, findEmojis(emotionId, deviceId), issuePlaybackUrl(emotion),
                emotion.getGroupStamp() == null ? null : GroupStampResult.from(emotion.getGroupStamp()));
    }

    List<EmotionListItemView> findVisiblePageWithinBounds(
            EmotionSearchBounds bounds, Instant snapshotAt, Instant lastCreatedAt,
            long lastId, int limit, UUID devicePublicId
    ) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));

        return emotionRepository.findVisiblePageWithinBounds(
                bounds, snapshotAt, lastCreatedAt, lastId, deviceId, null, limit
        ).stream().map(EmotionListItemView::from).toList();
    }

    public EmotionPageView findFirstListPage(EmotionSearchBounds bounds, UUID devicePublicId) {
        return findFirstListPage(bounds, devicePublicId, null);
    }

    public EmotionPageView findFirstListPage(EmotionSearchBounds bounds, UUID devicePublicId, UUID groupId) {
        Instant snapshotAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        return findList(EmotionListCursor.initial(bounds, snapshotAt, groupId), devicePublicId);
    }

    public EmotionPageView findNextListPage(String encodedCursor, UUID devicePublicId) {
        EmotionListCursor cursor = EmotionListCursorCodec.decode(encodedCursor);
        if (cursor.snapshotAt().isAfter(Instant.now(clock))) {
            throw new EmotionException(EMOTION_INVALID_CURSOR);
        }
        return findList(cursor, devicePublicId);
    }

    private PlaybackUrl issuePlaybackUrl(Emotion emotion) {
        if (emotion.getContent().getAudio() == null) {
            return null;
        }
        AudioPlaybackUrlIssuer issuer = playbackUrlIssuer.getIfAvailable();
        if (issuer == null) {
            throw new EmotionException(EMOTION_AUDIO_PLAYBACK_UNAVAILABLE);
        }
        try {
            PlaybackUrl result = issuer.issue(emotion.getContent().getAudio().getObjectKey());
            if (result == null || result.playbackUrl() == null || result.playbackUrl().isBlank()
                    || result.expiresAt() == null || !result.expiresAt().isAfter(Instant.now(clock))) {
                throw new IllegalStateException("유효한 재생 URL과 만료 시각이 필요합니다.");
            }
            return result;
        } catch (RuntimeException exception) {
            throw new EmotionException(EMOTION_AUDIO_PLAYBACK_UNAVAILABLE, exception);
        }
    }

    private EmotionPageView findList(EmotionListCursor cursor, UUID devicePublicId) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId).orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
        List<Emotion> found = emotionRepository.findVisiblePageWithinBounds(cursor.bounds(), cursor.snapshotAt(),
                cursor.lastItemCreatedAt(), cursor.lastId(), deviceId, cursor.groupId(), PAGE_SIZE + 1);
        boolean hasNext = found.size() > PAGE_SIZE;
        List<Emotion> page = hasNext ? found.subList(0, PAGE_SIZE) : found;
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
        List<Long> stampIds = page.stream().map(Emotion::getGroupStamp).filter(Objects::nonNull)
                .map(GroupStamp::getId).distinct().toList();
        Map<Long, GroupStampResult> stamps = stampIds.isEmpty() ? Map.of()
                : groupStampRepository.findAllWithGroupByIdIn(stampIds).stream()
                        .collect(Collectors.toMap(GroupStamp::getId, GroupStampResult::from));
        List<EmotionDetailView> items = page.stream().map(emotion -> EmotionDetailView.of(
                emotion, List.copyOf(counts.get(emotion.getId()).values()), null,
                emotion.getGroupStamp() == null ? null : stamps.get(emotion.getGroupStamp().getId()))).toList();
        String nextCursor = hasNext ? EmotionListCursorCodec.encode(cursor.next(
                page.getLast().getCreatedAt(), page.getLast().getId())) : null;
        return EmotionPageView.of(items, hasNext, nextCursor);
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
