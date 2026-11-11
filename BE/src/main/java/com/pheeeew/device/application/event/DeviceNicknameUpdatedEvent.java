package com.pheeeew.device.application.event;

import java.util.UUID;

public record DeviceNicknameUpdatedEvent(UUID devicePublicId, String nickname) {
    public static DeviceNicknameUpdatedEvent of(UUID devicePublicId, String nickname) {
        return new DeviceNicknameUpdatedEvent(devicePublicId, nickname);
    }
}
