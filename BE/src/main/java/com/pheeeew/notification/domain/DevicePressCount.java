package com.pheeeew.notification.domain;

public record DevicePressCount(Long deviceId, long pressCount) {

    public static DevicePressCount of(Long deviceId, long pressCount) {
        return new DevicePressCount(deviceId, pressCount);
    }
}
