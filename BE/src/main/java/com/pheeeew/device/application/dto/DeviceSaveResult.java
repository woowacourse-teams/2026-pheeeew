package com.pheeeew.device.application.dto;

public record DeviceSaveResult(String accessToken, String refreshToken, long expiresIn, boolean created) {

    public static DeviceSaveResult of(AccessTokenResult accessToken, String refreshToken, boolean created) {
        return new DeviceSaveResult(
                accessToken.accessToken(),
                refreshToken,
                accessToken.expiresIn(),
                created
        );
    }
}
