package com.pheeeew.notification.domain;

public record PushTarget(Long deviceId, String token) {

    public static PushTarget of(Long deviceId, String token) {
        return new PushTarget(deviceId, token);
    }
}
