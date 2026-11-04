package com.pheeeew.notification.application;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.notification.domain.DeviceNotificationSetting;
import com.pheeeew.notification.domain.NotificationSettings;
import com.pheeeew.notification.domain.repository.DeviceNotificationSettingRepository;
import com.pheeeew.notification.domain.repository.DevicePushTokenRepository;
import java.sql.SQLException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class NotificationSettingService {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    private static final String TOKEN_UNIQUE_CONSTRAINT = "\"uk_device_push_tokens_token\"";

    private final DeviceRepository deviceRepository;
    private final DevicePushTokenRepository devicePushTokenRepository;
    private final DeviceNotificationSettingRepository deviceNotificationSettingRepository;
    private final DevicePushTokenRegistrationService devicePushTokenRegistrationService;

    public void updatePushToken(UUID devicePublicId, String token) {
        Long deviceId = findDeviceId(devicePublicId);
        try {
            devicePushTokenRegistrationService.updatePushToken(deviceId, token);
        } catch (DataIntegrityViolationException exception) {
            if (!isTokenTakenConcurrently(exception)) {
                throw exception;
            }
            devicePushTokenRegistrationService.updatePushToken(deviceId, token);
        }
    }

    @Transactional
    public void deletePushToken(UUID devicePublicId) {
        devicePushTokenRepository.deleteByDeviceId(findDeviceId(devicePublicId));
    }

    public NotificationSettings findSettings(UUID devicePublicId) {
        return deviceNotificationSettingRepository.findByDeviceId(findDeviceId(devicePublicId))
                .map(DeviceNotificationSetting::toSettings)
                .orElseGet(NotificationSettings::defaults);
    }

    @Transactional
    public void updateSettings(UUID devicePublicId, boolean collectionClaimableEnabled, boolean rankChangedEnabled) {
        Long deviceId = findDeviceId(devicePublicId);
        NotificationSettings settings = NotificationSettings.of(collectionClaimableEnabled, rankChangedEnabled);
        DeviceNotificationSetting setting = DeviceNotificationSetting.builder()
                .deviceId(deviceId)
                .settings(settings)
                .build();
        if (deviceNotificationSettingRepository.saveIfAbsent(setting) == 1) {
            return;
        }
        deviceNotificationSettingRepository.findByDeviceIdForUpdate(deviceId)
                .ifPresent(saved -> saved.update(settings));
    }

    private Long findDeviceId(UUID devicePublicId) {
        return deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
    }

    private boolean isTokenTakenConcurrently(DataIntegrityViolationException exception) {
        Throwable cause = exception.getMostSpecificCause();

        return cause instanceof SQLException sqlException
                && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())
                && sqlException.getMessage() != null
                && sqlException.getMessage().contains(TOKEN_UNIQUE_CONSTRAINT);
    }
}
