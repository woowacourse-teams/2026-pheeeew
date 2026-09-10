package com.pheeeew.device.application.token;

import com.pheeeew.device.domain.DeviceRefreshToken;
import com.pheeeew.device.domain.repository.DeviceRefreshTokenRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class RefreshTokenIssuer {

    private static final String TOKEN_DELIMITER = ".";
    private static final int SECRET_BYTE_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DeviceRefreshTokenRepository deviceRefreshTokenRepository;

    public IssuedRefreshToken issue(Long deviceId) {
        UUID sessionId = UUID.randomUUID();
        String refreshToken = sessionId + TOKEN_DELIMITER + randomSecret();
        String tokenHash = RefreshTokenHasher.hash(refreshToken);

        deviceRefreshTokenRepository.save(DeviceRefreshToken.builder()
                .deviceId(deviceId)
                .sessionId(sessionId)
                .tokenHash(tokenHash)
                .build());

        return IssuedRefreshToken.of(refreshToken, sessionId, tokenHash);
    }

    private String randomSecret() {
        byte[] randomBytes = new byte[SECRET_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
