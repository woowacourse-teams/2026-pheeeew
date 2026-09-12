package com.pheeeew.device.application.dto;

public record DeviceChallengeResult(String challenge, long expiresIn) {

    public static DeviceChallengeResult of(String challenge, long expiresIn) {
        return new DeviceChallengeResult(challenge, expiresIn);
    }
}
