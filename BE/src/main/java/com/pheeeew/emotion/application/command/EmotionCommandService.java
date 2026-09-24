package com.pheeeew.emotion.application.command;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_NOT_VISIBLE;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.domain.repository.EmotionEmojiRepository;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.exception.EmotionException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class EmotionCommandService {

    private final EmotionEmojiRepository emotionEmojiRepository;
    private final EmotionRepository emotionRepository;
    private final DeviceRepository deviceRepository;

    @Transactional
    public void updateEmoji(Long emotionId, UUID devicePublicId, EmojiType emojiType, boolean selected) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
        emotionRepository.findVisibleById(emotionId, deviceId)
                .orElseThrow(() -> new EmotionException(EMOTION_NOT_VISIBLE));

        if (selected) {
            emotionEmojiRepository.saveIfAbsent(emotionId, deviceId, emojiType.name(), Instant.now());
        } else {
            emotionEmojiRepository.deleteSelection(emotionId, deviceId, emojiType.name());
        }
    }
}
