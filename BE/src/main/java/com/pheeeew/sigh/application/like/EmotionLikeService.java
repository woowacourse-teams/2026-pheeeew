package com.pheeeew.sigh.application.like;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.sigh.exception.SighErrorCode.SIGH_NOT_FOUND;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.sigh.application.like.dto.SighLikeResult;
import com.pheeeew.sigh.domain.Sigh;
import com.pheeeew.sigh.domain.SighLike;
import com.pheeeew.sigh.domain.repository.SighLikeRepository;
import com.pheeeew.sigh.domain.repository.SighRepository;
import com.pheeeew.sigh.exception.SighException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class EmotionLikeService {

    private final SighLikeRepository emotionLikeRepository;
    private final SighRepository emotionRepository;
    private final DeviceRepository deviceRepository;

    @Transactional
    public SighLikeResult update(Long emotionId, UUID devicePublicId, boolean liked) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
        Sigh emotion = emotionRepository.findByIdAndDeletedAtIsNull(emotionId)
                .orElseThrow(() -> new SighException(SIGH_NOT_FOUND));
        SighLike like = emotionLikeRepository.findBySighIdAndDeviceId(emotionId, deviceId).orElse(null);

        if (liked && like == null) {
            emotionLikeRepository.save(
                    SighLike.builder()
                            .sighId(emotionId)
                            .deviceId(deviceId)
                            .build()
            );
            emotion.increaseLikeCount();
        } else if (!liked && like != null) {
            emotionLikeRepository.delete(like);
            emotion.decreaseLikeCount();
        }

        return SighLikeResult.of(liked, emotion.getLikeCount());
    }
}
