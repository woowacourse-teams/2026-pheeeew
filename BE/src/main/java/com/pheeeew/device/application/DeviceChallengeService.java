package com.pheeeew.device.application;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_CHALLENGE_INVALID;

import com.pheeeew.auth.infra.jwt.TokenProperties;
import com.pheeeew.device.application.dto.DeviceChallengeResult;
import com.pheeeew.device.domain.DeviceChallenge;
import com.pheeeew.device.domain.repository.DeviceChallengeRepository;
import com.pheeeew.device.exception.DeviceException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class DeviceChallengeService {

    private static final int CHALLENGE_BYTE_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DeviceChallengeRepository deviceChallengeRepository;
    private final DeviceChallengeMetrics deviceChallengeMetrics;
    private final TokenProperties tokenProperties;

    @Transactional
    public DeviceChallengeResult save() {
        Duration challengeTtl = tokenProperties.challengeTtl();
        DeviceChallenge deviceChallenge = DeviceChallenge.builder()
                .challenge(randomChallenge())
                .expiresAt(Instant.now().plus(challengeTtl))
                .build();

        deviceChallengeRepository.save(deviceChallenge);
        deviceChallengeMetrics.recordIssued();

        return DeviceChallengeResult.of(deviceChallenge.getChallenge(), challengeTtl.toSeconds());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consumeAttempt(String challenge) {
        int attemptedCount = deviceChallengeRepository.consumeAttempt(
                challenge,
                Instant.now(),
                DeviceChallenge.MAX_ATTEMPTS
        );
        if (attemptedCount == 0) {
            throw new DeviceException(DEVICE_CHALLENGE_INVALID);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consume(String challenge) {
        if (deviceChallengeRepository.consume(challenge, Instant.now()) == 0) {
            throw new DeviceException(DEVICE_CHALLENGE_INVALID);
        }
    }

    @Transactional
    public int deleteExpired() {
        int deletedCount = deviceChallengeRepository.deleteExpired(Instant.now());
        deviceChallengeMetrics.recordDeleted(deletedCount);

        return deletedCount;
    }

    private String randomChallenge() {
        byte[] randomBytes = new byte[CHALLENGE_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
