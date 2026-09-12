package com.pheeeew.device.infra.attestation;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_ATTESTATION_INVALID;
import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_ATTESTATION_UNAVAILABLE;
import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_CHALLENGE_INVALID;

import com.pheeeew.device.application.DeviceAttestationBudgetService;
import com.pheeeew.device.application.DeviceAttestationVerifier;
import com.pheeeew.device.application.DeviceChallengeService;
import com.pheeeew.device.application.dto.DeviceAttestation;
import com.pheeeew.device.domain.DevicePlatform;
import com.pheeeew.device.exception.DeviceException;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlayIntegrityDeviceAttestationVerifier implements DeviceAttestationVerifier {

    private static final DevicePlatform VERIFIABLE_PLATFORM = DevicePlatform.ANDROID;
    private static final String RECOGNIZED_APP_VERDICT = "PLAY_RECOGNIZED";
    private static final String DEVICE_INTEGRITY_VERDICT = "MEETS_DEVICE_INTEGRITY";
    private static final Pattern JOSE_COMPACT_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]*){2}(?:(?:\\.[A-Za-z0-9_-]*){2})?$");

    private final PlayIntegrityTokenDecoder playIntegrityTokenDecoder;
    private final DeviceChallengeService deviceChallengeService;
    private final DeviceAttestationBudgetService deviceAttestationBudgetService;
    private final PlayIntegrityProperties playIntegrityProperties;
    private final PlayIntegrityMetrics playIntegrityMetrics;

    public PlayIntegrityDeviceAttestationVerifier(
            PlayIntegrityTokenDecoder playIntegrityTokenDecoder,
            DeviceChallengeService deviceChallengeService,
            DeviceAttestationBudgetService deviceAttestationBudgetService,
            PlayIntegrityProperties playIntegrityProperties,
            PlayIntegrityMetrics playIntegrityMetrics
    ) {
        this.playIntegrityTokenDecoder = playIntegrityTokenDecoder;
        this.deviceChallengeService = deviceChallengeService;
        this.deviceAttestationBudgetService = deviceAttestationBudgetService;
        this.playIntegrityProperties = playIntegrityProperties;
        this.playIntegrityMetrics = playIntegrityMetrics;
        warnWhenVerificationSkipped();
        warnWhenCredentialsMissing();
    }

    @Override
    public void verify(DeviceAttestation attestation) {
        if (playIntegrityProperties.skipVerification()) {
            playIntegrityMetrics.recordSkipped();
            return;
        }

        String integrityToken = attestation.token();
        if (integrityToken == null || integrityToken.isBlank()) {
            rejectWhenAttestationRequired();
            return;
        }

        requireVerifiablePlatform(attestation.platform());
        requireConfiguredCredentials();
        requireCompactJoseForm(integrityToken);
        String challenge = requireChallenge(attestation.challenge());
        consumeChallengeAttempt(challenge);
        consumeCallBudget();

        PlayIntegrityPayload payload = decode(integrityToken);
        playIntegrityMetrics.recordVerdict(payload.appRecognitionVerdict(), meetsDeviceIntegrity(payload));
        requireMatchingPackageName(payload);
        requireRecognizedApp(payload);
        requireDeviceIntegrity(payload);
        requireMatchingChallenge(payload, challenge);

        consumeChallenge(payload);
        playIntegrityMetrics.recordAccepted();
    }

    private void warnWhenVerificationSkipped() {
        if (playIntegrityProperties.skipVerification()) {
            log.warn("Play Integrity verification is skipped by configuration. "
                    + "Every registration request is accepted without an integrity verdict.");
        }
    }

    private void warnWhenCredentialsMissing() {
        if (!playIntegrityProperties.isConfigured()) {
            log.warn("Play Integrity credentials are not configured. "
                    + "Registration requests that carry an attestation token are rejected.");
        }
    }

    private void rejectWhenAttestationRequired() {
        if (playIntegrityProperties.requireAttestation()) {
            throw rejected(PlayIntegrityRejection.ATTESTATION_REQUIRED);
        }
    }

    private void requireVerifiablePlatform(DevicePlatform platform) {
        if (platform != VERIFIABLE_PLATFORM) {
            throw rejected(PlayIntegrityRejection.UNSUPPORTED_PLATFORM);
        }
    }

    private void requireConfiguredCredentials() {
        if (!playIntegrityProperties.isConfigured()) {
            throw rejected(PlayIntegrityRejection.CREDENTIALS_MISSING);
        }
    }

    private void requireCompactJoseForm(String integrityToken) {
        if (!JOSE_COMPACT_PATTERN.matcher(integrityToken).matches()) {
            throw rejected(PlayIntegrityRejection.MALFORMED_TOKEN);
        }
    }

    private String requireChallenge(String challenge) {
        if (challenge == null || challenge.isBlank()) {
            playIntegrityMetrics.recordRejected(PlayIntegrityRejection.CHALLENGE_MISSING);
            throw new DeviceException(DEVICE_CHALLENGE_INVALID);
        }
        return challenge;
    }

    private void consumeChallengeAttempt(String challenge) {
        try {
            deviceChallengeService.consumeAttempt(challenge);
        } catch (DeviceException exception) {
            playIntegrityMetrics.recordRejected(PlayIntegrityRejection.CHALLENGE_UNUSABLE);
            throw exception;
        }
    }

    private void consumeCallBudget() {
        try {
            deviceAttestationBudgetService.consumeCall();
        } catch (DeviceException exception) {
            playIntegrityMetrics.recordRejected(PlayIntegrityRejection.CALL_BUDGET_EXHAUSTED);
            throw exception;
        }
    }

    private PlayIntegrityPayload decode(String integrityToken) {
        try {
            return playIntegrityTokenDecoder.decode(integrityToken);
        } catch (DeviceException exception) {
            playIntegrityMetrics.recordRejected(decodeRejectionOf(exception));
            throw exception;
        } catch (PlayIntegrityUnavailableException exception) {
            playIntegrityMetrics.recordRejected(PlayIntegrityRejection.GOOGLE_UNAVAILABLE);
            throw exception;
        }
    }

    private PlayIntegrityRejection decodeRejectionOf(DeviceException exception) {
        if (exception.getErrorCode() == DEVICE_ATTESTATION_UNAVAILABLE) {
            return PlayIntegrityRejection.GOOGLE_QUOTA_EXHAUSTED;
        }
        return PlayIntegrityRejection.MALFORMED_PAYLOAD;
    }

    private boolean meetsDeviceIntegrity(PlayIntegrityPayload payload) {
        return payload.deviceRecognitionVerdicts().contains(DEVICE_INTEGRITY_VERDICT);
    }

    private void requireMatchingPackageName(PlayIntegrityPayload payload) {
        if (!playIntegrityProperties.androidPackageName().equals(payload.requestPackageName())) {
            throw rejected(PlayIntegrityRejection.PACKAGE_MISMATCH);
        }
    }

    private void requireRecognizedApp(PlayIntegrityPayload payload) {
        if (!RECOGNIZED_APP_VERDICT.equals(payload.appRecognitionVerdict())) {
            throw rejected(PlayIntegrityRejection.APP_UNRECOGNIZED);
        }
    }

    private void requireDeviceIntegrity(PlayIntegrityPayload payload) {
        if (!meetsDeviceIntegrity(payload)) {
            throw rejected(PlayIntegrityRejection.DEVICE_INTEGRITY_MISSING);
        }
    }

    private void requireMatchingChallenge(PlayIntegrityPayload payload, String challenge) {
        if (!challenge.equals(payload.challenge())) {
            throw rejected(PlayIntegrityRejection.CHALLENGE_MISMATCH);
        }
    }

    private void consumeChallenge(PlayIntegrityPayload payload) {
        try {
            deviceChallengeService.consume(payload.challenge());
        } catch (DeviceException exception) {
            playIntegrityMetrics.recordRejected(PlayIntegrityRejection.CHALLENGE_INVALID);
            throw exception;
        }
    }

    private DeviceException rejected(PlayIntegrityRejection rejection) {
        playIntegrityMetrics.recordRejected(rejection);
        return new DeviceException(DEVICE_ATTESTATION_INVALID);
    }
}
