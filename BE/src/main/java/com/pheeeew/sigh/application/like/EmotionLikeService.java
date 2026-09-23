package com.pheeeew.sigh.application.like;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.sigh.exception.SighErrorCode.SIGH_NOT_FOUND;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.sigh.application.like.dto.EmotionLikeResult;
import com.pheeeew.sigh.domain.Emotion;
import com.pheeeew.sigh.domain.EmotionLike;
import com.pheeeew.sigh.domain.repository.EmotionLikeRepository;
import com.pheeeew.sigh.domain.repository.EmotionRepository;
import com.pheeeew.sigh.exception.SighException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class EmotionLikeService {

    private final EmotionLikeRepository emotionLikeRepository;
    private final EmotionRepository emotionRepository;
    private final DeviceRepository deviceRepository;

    @Transactional
    public EmotionLikeResult update(Long emotionId, UUID devicePublicId, boolean liked) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
        Emotion emotion = emotionRepository.findByIdAndDeletedAtIsNull(emotionId)
                .orElseThrow(() -> new SighException(SIGH_NOT_FOUND));
        EmotionLike like = emotionLikeRepository.findByEmotionIdAndDeviceId(emotionId, deviceId).orElse(null);

        if (liked && like == null) {
            emotionLikeRepository.save(
                    EmotionLike.builder()
                            .emotionId(emotionId)
                            .deviceId(deviceId)
                            .build()
            );
            emotion.increaseLikeCount();
        } else if (!liked && like != null) {
            emotionLikeRepository.delete(like);
            emotion.decreaseLikeCount();
        }

        return EmotionLikeResult.of(liked, emotion.getLikeCount());
    }
}
