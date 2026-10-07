package com.pheeeew.device.application;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_REGISTRATION_WINDOW_EXPIRED;
import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NICKNAME_DUPLICATED;
import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_SAVE_FAILED;

import com.pheeeew.auth.infra.jwt.TokenProperties;
import com.pheeeew.device.application.dto.AccessTokenResult;
import com.pheeeew.device.application.dto.DeviceAttestation;
import com.pheeeew.device.application.dto.DeviceSaveResult;
import com.pheeeew.device.application.token.AccessTokenIssuer;
import com.pheeeew.device.application.token.IssuedRefreshToken;
import com.pheeeew.device.application.token.RefreshTokenIssuer;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.DeviceNickname;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final RefreshTokenIssuer refreshTokenIssuer;
    private final AccessTokenIssuer accessTokenIssuer;
    private final DeviceAttestationVerifier deviceAttestationVerifier;
    private final TokenProperties tokenProperties;

    // 구버전 v2 등록은 닉네임을 비워 두고, 앱 업데이트 후 인증된 수정 요청으로 설정한다.
    public DeviceSaveResult save(UUID requestId, DeviceAttestation attestation) {
        Optional<Device> existingDevice = deviceRepository.findByRequestId(requestId);

        if (existingDevice.isPresent()) {
            return reissueTokens(existingDevice.get());
        }

        return saveNewDevice(requestId, attestation, null);
    }

    public DeviceSaveResult save(UUID requestId, String nickname, DeviceAttestation attestation) {
        Optional<Device> existingDevice = deviceRepository.findByRequestId(requestId);

        if (existingDevice.isPresent()) {
            return reissueTokens(existingDevice.get());
        }

        String normalized = DeviceNickname.from(nickname).value();
        return saveNewDevice(requestId, attestation, normalized);
    }

    @Transactional(readOnly = true)
    public boolean findNicknameAvailability(String nickname) {
        String normalized = DeviceNickname.from(nickname).value().toLowerCase(Locale.ROOT);
        return !deviceRepository.existsByNickname(normalized);
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

    private DeviceSaveResult saveNewDevice(UUID requestId, DeviceAttestation attestation, String nickname) {
        deviceAttestationVerifier.verify(attestation);

        Device device = Device.builder()
                .requestId(requestId)
                .platform(attestation.platform())
                .nickname(nickname)
                .build();

        Device saved;
        try {
            saved = deviceRepository.saveAndFlush(device);
        } catch (DataIntegrityViolationException cause) {
            return reissueTokensForExistingDevice(requestId, cause);
        }

        return issueTokens(saved, true);
    }

    private DeviceSaveResult reissueTokensForExistingDevice(
            UUID requestId,
            DataIntegrityViolationException cause
    ) {
        Device device = deviceRepository.findByRequestId(requestId)
                .orElseThrow(() -> saveFailure(cause));

        return reissueTokens(device);
    }

    private DeviceException saveFailure(DataIntegrityViolationException cause) {
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException violation
                    && "uk_devices_nickname".equals(violation.getConstraintName())) {
                return new DeviceException(DEVICE_NICKNAME_DUPLICATED, cause);
            }
        }
        return new DeviceException(DEVICE_SAVE_FAILED, cause);
    }
}
