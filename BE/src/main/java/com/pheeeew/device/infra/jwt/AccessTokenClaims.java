package com.pheeeew.device.infra.jwt;

import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

public record AccessTokenClaims(UUID devicePublicId, Instant issuedAt, Instant expiresAt) {

    public static AccessTokenClaims from(Jwt jwt) {
        return new AccessTokenClaims(
                parseDevicePublicId(jwt.getSubject()),
                jwt.getIssuedAt(),
                jwt.getExpiresAt()
        );
    }

    private static UUID parseDevicePublicId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BadJwtException("액세스 토큰의 대상 식별자 형식이 올바르지 않습니다.");
        }
    }
}
