package com.pheeeew.notification.application;

import com.pheeeew.notification.domain.repository.DevicePushTokenRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class DevicePushTokenRegistrationService {

    private final DevicePushTokenRepository devicePushTokenRepository;
    private final Clock clock;

    @Transactional
    public void updatePushToken(Long deviceId, String token) {
        devicePushTokenRepository.deleteByTokenAndDeviceIdNot(token, deviceId);
        devicePushTokenRepository.save(deviceId, token, clock.instant());
    }
}
