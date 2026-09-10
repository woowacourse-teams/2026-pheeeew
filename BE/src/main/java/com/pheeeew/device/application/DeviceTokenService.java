package com.pheeeew.device.application;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_REFRESH_TOKEN_INVALID;

import com.pheeeew.device.application.dto.AccessTokenResult;
import com.pheeeew.device.application.token.AccessTokenIssuer;
import com.pheeeew.device.application.token.RefreshTokenVerifier;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.DeviceRefreshToken;
import com.pheeeew.device.domain.repository.DeviceRefreshTokenRepository;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class DeviceTokenService {

    private final DeviceRefreshTokenRepository deviceRefreshTokenRepository;
    private final DeviceRepository deviceRepository;
    private final RefreshTokenVerifier refreshTokenVerifier;
    private final AccessTokenIssuer accessTokenIssuer;

    public AccessTokenResult reissueAccessToken(String refreshToken) {
        UUID sessionId = refreshTokenVerifier.extractSessionId(refreshToken)
                .orElseThrow(() -> new DeviceException(DEVICE_REFRESH_TOKEN_INVALID));

        DeviceRefreshToken storedToken = deviceRefreshTokenRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new DeviceException(DEVICE_REFRESH_TOKEN_INVALID));
        validateUsable(refreshToken, storedToken);

        Device device = deviceRepository.findById(storedToken.getDeviceId())
                .orElseThrow(() -> new DeviceException(DEVICE_REFRESH_TOKEN_INVALID));

        return accessTokenIssuer.issue(device.getPublicId());
    }

    private void validateUsable(String refreshToken, DeviceRefreshToken storedToken) {
        if (!refreshTokenVerifier.matches(refreshToken, storedToken.getTokenHash())) {
            throw new DeviceException(DEVICE_REFRESH_TOKEN_INVALID);
        }
        if (!storedToken.isUsable(Instant.now())) {
            throw new DeviceException(DEVICE_REFRESH_TOKEN_INVALID);
        }
    }
}
