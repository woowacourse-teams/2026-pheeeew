package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_NOT_VISIBLE;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.emoji.dto.EmotionEmojiResult;
import com.pheeeew.emotion.application.query.dto.EmotionDetailView;
import com.pheeeew.emotion.application.query.dto.EmotionListItemView;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.domain.repository.EmotionEmojiRepository;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.projection.EmotionEmojiCountProjection;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.exception.EmotionException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class EmotionQueryService {

    private final EmotionRepository emotionRepository;
    private final DeviceRepository deviceRepository;
    private final EmotionEmojiRepository emotionEmojiRepository;

    public EmotionDetailView findById(Long emotionId, UUID devicePublicId) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
        Emotion emotion = emotionRepository.findVisibleById(emotionId, deviceId)
                .orElseThrow(() -> new EmotionException(EMOTION_NOT_VISIBLE));

        return EmotionDetailView.of(emotion, findEmojis(emotionId, deviceId));
    }

    List<EmotionListItemView> findVisiblePageWithinBounds(
            EmotionSearchBounds bounds, Instant snapshotAt, Instant lastCreatedAt,
            long lastId, int limit, UUID devicePublicId
    ) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));

        return emotionRepository.findVisiblePageWithinBounds(
                bounds, snapshotAt, lastCreatedAt, lastId, deviceId, limit
        ).stream().map(EmotionListItemView::from).toList();
    }

    private List<EmotionEmojiResult> findEmojis(Long emotionId, Long deviceId) {
        EnumMap<EmojiType, EmotionEmojiResult> results = new EnumMap<>(EmojiType.class);
        for (EmojiType type : EmojiType.values()) {
            results.put(type, EmotionEmojiResult.of(type, 0, false));
        }
        for (EmotionEmojiCountProjection count : emotionEmojiRepository.findCounts(emotionId, deviceId)) {
            EmojiType type = EmojiType.valueOf(count.getEmojiType());
            results.put(type, EmotionEmojiResult.of(type, count.getSelectionCount(), count.getSelected()));
        }

        return List.copyOf(results.values());
    }
}
