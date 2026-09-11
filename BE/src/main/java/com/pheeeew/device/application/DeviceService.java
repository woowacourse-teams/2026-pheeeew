package com.pheeeew.device.application;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_REGISTRATION_WINDOW_EXPIRED;
import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_SAVE_FAILED;

import com.pheeeew.device.application.dto.AccessTokenResult;
import com.pheeeew.device.application.dto.DeviceSaveResult;
import com.pheeeew.device.application.token.AccessTokenIssuer;
import com.pheeeew.device.application.token.IssuedRefreshToken;
import com.pheeeew.device.application.token.RefreshTokenIssuer;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.DevicePlatform;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.device.infra.jwt.TokenProperties;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final RefreshTokenIssuer refreshTokenIssuer;
    private final AccessTokenIssuer accessTokenIssuer;
    private final TokenProperties tokenProperties;

    public DeviceSaveResult save(UUID requestId, DevicePlatform platform) {
        Optional<Device> existingDevice = deviceRepository.findByRequestId(requestId);

        if (existingDevice.isPresent()) {
            return reissueTokens(existingDevice.get());
        }

        return saveNewDevice(requestId, platform);
    }

    private DeviceSaveResult reissueTokens(Device device) {
        if (isRetryWindowExpired(device)) {
            throw new DeviceException(DEVICE_REGISTRATION_WINDOW_EXPIRED);
        }

        return issueTokens(device, false);
    }

    private boolean isRetryWindowExpired(Device device) {
        Instant deadline = device.getCreatedAt().plus(tokenProperties.registrationRetryWindow());
        return deadline.isBefore(Instant.now());
    }

    private DeviceSaveResult issueTokens(Device device, boolean created) {
        IssuedRefreshToken issuedRefreshToken = refreshTokenIssuer.issue(device.getId());
        AccessTokenResult accessToken = accessTokenIssuer.issue(device.getPublicId());

        return DeviceSaveResult.of(accessToken, issuedRefreshToken.refreshToken(), created);
    }

    private DeviceSaveResult saveNewDevice(UUID requestId, DevicePlatform platform) {
        Device device = Device.builder()
                .requestId(requestId)
                .platform(platform)
                .build();

        try {
            return issueTokens(deviceRepository.saveAndFlush(device), true);
        } catch (DataIntegrityViolationException cause) {
            return reissueTokensForExistingDevice(requestId, cause);
        }
    }

    private DeviceSaveResult reissueTokensForExistingDevice(
            UUID requestId,
            DataIntegrityViolationException cause
    ) {
        Device device = deviceRepository.findByRequestId(requestId)
                .orElseThrow(() -> new DeviceException(DEVICE_SAVE_FAILED, cause));

        return reissueTokens(device);
    }
}
