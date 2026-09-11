package com.pheeeew.device.application.token;

import java.util.UUID;

public record IssuedRefreshToken(String refreshToken, UUID sessionId, String tokenHash) {

    public static IssuedRefreshToken of(String refreshToken, UUID sessionId, String tokenHash) {
        return new IssuedRefreshToken(refreshToken, sessionId, tokenHash);
    }
}
