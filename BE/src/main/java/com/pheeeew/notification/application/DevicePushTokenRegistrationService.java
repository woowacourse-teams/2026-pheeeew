package com.pheeeew.notification.application;

import com.pheeeew.notification.domain.DevicePushToken;
import com.pheeeew.notification.domain.repository.DevicePushTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class DevicePushTokenRegistrationService {

    private final DevicePushTokenRepository devicePushTokenRepository;

    @Transactional
    public void updatePushToken(Long deviceId, String token) {
        devicePushTokenRepository.deleteByTokenAndDeviceIdNot(token, deviceId);
        DevicePushToken pushToken = DevicePushToken.builder()
                .deviceId(deviceId)
                .token(token)
                .build();
        if (devicePushTokenRepository.saveIfAbsent(pushToken) == 1) {
            return;
        }
        devicePushTokenRepository.findByDeviceIdForUpdate(deviceId)
                .ifPresent(registered -> registered.changeToken(token));
    }

    @Transactional
    public void deleteByDeviceIdAndToken(Long deviceId, String token) {
        devicePushTokenRepository.deleteByDeviceIdAndToken(deviceId, token);
    }
}
