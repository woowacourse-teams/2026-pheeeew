package com.pheeeew.device.application.dto;

import com.pheeeew.device.domain.DevicePlatform;

public record DeviceAttestation(DevicePlatform platform, String token, String challenge, String keyId) {

    public static DeviceAttestation of(DevicePlatform platform, String token, String challenge, String keyId) {
        return new DeviceAttestation(platform, token, challenge, keyId);
    }

    @Override
    public String toString() {
        return "DeviceAttestation[platform=" + platform
                + ", token=<redacted>, challenge=<redacted>, keyId=<redacted>]";
    }
}
