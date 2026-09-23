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

    private final SighLikeRepository sighLikeRepository;
    private final SighRepository sighRepository;
    private final DeviceRepository deviceRepository;

    @Transactional
    public SighLikeResult update(Long sighId, UUID devicePublicId, boolean liked) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
        Sigh sigh = sighRepository.findByIdAndDeletedAtIsNull(sighId)
                .orElseThrow(() -> new SighException(SIGH_NOT_FOUND));
        SighLike like = sighLikeRepository.findBySighIdAndDeviceId(sighId, deviceId).orElse(null);

        if (liked && like == null) {
            sighLikeRepository.save(
                    SighLike.builder()
                            .sighId(sighId)
                            .deviceId(deviceId)
                            .build()
            );
            sigh.increaseLikeCount();
        } else if (!liked && like != null) {
            sighLikeRepository.delete(like);
            sigh.decreaseLikeCount();
        }

        return SighLikeResult.of(liked, sigh.getLikeCount());
    }
}
