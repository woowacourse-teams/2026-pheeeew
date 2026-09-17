package com.pheeeew.activity.application.dto;

import com.pheeeew.device.domain.DevicePlatform;

public record DeviceActivityCount(DevicePlatform platform, long dau, long mau) {

    public static DeviceActivityCount of(DevicePlatform platform, long dau, long mau) {
        return new DeviceActivityCount(platform, dau, mau);
    }
}
