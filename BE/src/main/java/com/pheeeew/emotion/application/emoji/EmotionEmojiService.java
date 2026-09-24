package com.pheeeew.emotion.application.emoji;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_NOT_FOUND;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.emoji.dto.EmotionEmojiResult;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.domain.repository.EmotionEmojiRepository;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.projection.EmotionEmojiCountProjection;
import com.pheeeew.emotion.exception.EmotionException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class EmotionEmojiService {

    private final EmotionEmojiRepository emotionEmojiRepository;
    private final EmotionRepository emotionRepository;
    private final DeviceRepository deviceRepository;

    @Transactional
    public void update(Long emotionId, UUID devicePublicId, EmojiType emojiType, boolean selected) {
        Long deviceId = findDeviceId(devicePublicId);
        validateEmotion(emotionId);

        if (selected) {
            emotionEmojiRepository.saveIfAbsent(emotionId, deviceId, emojiType.name(), Instant.now());
        } else {
            emotionEmojiRepository.deleteSelection(emotionId, deviceId, emojiType.name());
        }
    }

    @Transactional(readOnly = true)
    public List<EmotionEmojiResult> findAll(Long emotionId, UUID devicePublicId) {
        Long deviceId = findDeviceId(devicePublicId);
        validateEmotion(emotionId);

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

    private Long findDeviceId(UUID devicePublicId) {
        return deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
    }

    private void validateEmotion(Long emotionId) {
        emotionRepository.findByIdAndDeletedAtIsNull(emotionId)
                .orElseThrow(() -> new EmotionException(EMOTION_NOT_FOUND));
    }
}
