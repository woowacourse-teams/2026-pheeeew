package com.pheeeew.device.application.dto;

public record AccessTokenResult(String accessToken, long expiresIn) {

    public static AccessTokenResult of(String accessToken, long expiresIn) {
        return new AccessTokenResult(accessToken, expiresIn);
    }
}
