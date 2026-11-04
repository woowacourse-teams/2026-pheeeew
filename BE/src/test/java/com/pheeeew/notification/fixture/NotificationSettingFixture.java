package com.pheeeew.notification.fixture;

import com.pheeeew.notification.domain.DeviceNotificationSetting;
import com.pheeeew.notification.domain.NotificationSettings;

public final class NotificationSettingFixture {

    private NotificationSettingFixture() {
    }

    public static DeviceNotificationSetting 기기_수신_설정(Long deviceId, NotificationSettings settings) {
        return DeviceNotificationSetting.builder()
                .deviceId(deviceId)
                .settings(settings)
                .build();
    }
}
