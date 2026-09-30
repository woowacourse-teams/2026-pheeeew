package com.pheeeew.auth.presentation;

import com.pheeeew.auth.infra.jwt.AccessTokenClaims;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class AuthenticatedDevices {

    private AuthenticatedDevices() {
    }

    public static Optional<UUID> findDevicePublicId() {
        return findAuthentication()
                .map(authentication -> AccessTokenClaims.from(authentication.getToken()).devicePublicId());
    }

    private static Optional<JwtAuthenticationToken> findAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication && jwtAuthentication.isAuthenticated()) {
            return Optional.of(jwtAuthentication);
        }
        return Optional.empty();
    }
}
