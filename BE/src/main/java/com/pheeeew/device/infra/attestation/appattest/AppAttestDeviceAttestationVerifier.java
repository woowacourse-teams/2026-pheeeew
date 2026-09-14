package com.pheeeew.device.infra.attestation.appattest;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_ATTESTATION_INVALID;
import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_CHALLENGE_INVALID;

import com.pheeeew.device.application.DeviceAttestationVerifier;
import com.pheeeew.device.application.DeviceChallengeService;
import com.pheeeew.device.application.dto.DeviceAttestation;
import com.pheeeew.device.exception.DeviceException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AppAttestDeviceAttestationVerifier implements DeviceAttestationVerifier {

    private static final String ATTESTATION_FORMAT = "apple-appattest";
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int KEY_ID_BYTE_LENGTH = 32;
    private static final byte UNCOMPRESSED_POINT_PREFIX = 0x04;
    private static final int COORDINATE_BYTE_LENGTH = 32;
    private static final long ATTESTATION_COUNTER = 0L;

    private final AppAttestObjectDecoder appAttestObjectDecoder;
    private final AppAttestCertificateChainValidator appAttestCertificateChainValidator;
    private final DeviceChallengeService deviceChallengeService;
    private final AppAttestProperties appAttestProperties;
    private final AppAttestMetrics appAttestMetrics;

    public AppAttestDeviceAttestationVerifier(
            AppAttestObjectDecoder appAttestObjectDecoder,
            AppAttestCertificateChainValidator appAttestCertificateChainValidator,
            DeviceChallengeService deviceChallengeService,
            AppAttestProperties appAttestProperties,
            AppAttestMetrics appAttestMetrics
    ) {
        this.appAttestObjectDecoder = appAttestObjectDecoder;
        this.appAttestCertificateChainValidator = appAttestCertificateChainValidator;
        this.deviceChallengeService = deviceChallengeService;
        this.appAttestProperties = appAttestProperties;
        this.appAttestMetrics = appAttestMetrics;
        warnWhenPropertiesMissing();
    }

    @Override
    public void verify(DeviceAttestation attestation) {
        String attestationToken = attestation.token();
        if (attestationToken == null || attestationToken.isBlank()) {
            rejectWhenAttestationRequired();
            return;
        }

        requireConfiguredProperties();
        byte[] attestationObject = decodeToken(attestationToken);
        byte[] keyId = decodeKeyId(attestation.keyId());
        String challenge = requireChallenge(attestation.challenge());

        AppAttestObject appAttestObject = decodeAttestationObject(attestationObject);
        requireSupportedFormat(appAttestObject);
        AppAttestAuthenticatorData authenticatorData = parseAuthenticatorData(appAttestObject);

        consumeChallenge(challenge);

        validateCertificateChain(appAttestObject);
        requireMatchingNonce(appAttestObject, challenge);
        requireMatchingKeyId(appAttestObject, keyId);
        requireMatchingAppId(authenticatorData);
        requireAttestationCounter(authenticatorData);
        requireConfiguredEnvironment(authenticatorData);
        requireMatchingCredentialId(authenticatorData, keyId);

        appAttestMetrics.recordAccepted();
    }

    private void warnWhenPropertiesMissing() {
        if (!appAttestProperties.isConfigured()) {
            log.warn("App Attest team id and bundle id are not configured. "
                    + "Registration requests that carry an attestation object are rejected.");
        }
    }

    private void rejectWhenAttestationRequired() {
        if (appAttestProperties.requireAttestation()) {
            throw rejected(AppAttestRejection.ATTESTATION_REQUIRED);
        }
    }

    private void requireConfiguredProperties() {
        if (!appAttestProperties.isConfigured()) {
            throw rejected(AppAttestRejection.PROPERTIES_MISSING);
        }
    }

    private byte[] decodeToken(String attestationToken) {
        try {
            return appAttestObjectDecoder.decodeToken(attestationToken);
        } catch (IllegalArgumentException exception) {
            throw rejected(AppAttestRejection.MALFORMED_TOKEN);
        }
    }

    private byte[] decodeKeyId(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            throw rejected(AppAttestRejection.KEY_ID_MISSING);
        }
        byte[] decodedKeyId = decodeBase64KeyId(keyId);
        if (decodedKeyId.length != KEY_ID_BYTE_LENGTH) {
            throw rejected(AppAttestRejection.KEY_ID_MISSING);
        }
        return decodedKeyId;
    }

    private byte[] decodeBase64KeyId(String keyId) {
        try {
            return Base64.getDecoder().decode(keyId);
        } catch (IllegalArgumentException exception) {
            throw rejected(AppAttestRejection.KEY_ID_MISSING);
        }
    }

    private String requireChallenge(String challenge) {
        if (challenge == null || challenge.isBlank()) {
            appAttestMetrics.recordRejected(AppAttestRejection.CHALLENGE_INVALID);
            throw new DeviceException(DEVICE_CHALLENGE_INVALID);
        }
        return challenge;
    }

    private AppAttestObject decodeAttestationObject(byte[] attestationObject) {
        try {
            return appAttestObjectDecoder.decode(attestationObject);
        } catch (IllegalArgumentException exception) {
            throw rejected(AppAttestRejection.MALFORMED_ATTESTATION);
        }
    }

    private void requireSupportedFormat(AppAttestObject appAttestObject) {
        if (!ATTESTATION_FORMAT.equals(appAttestObject.format())) {
            throw rejected(AppAttestRejection.UNSUPPORTED_FORMAT);
        }
    }

    private AppAttestAuthenticatorData parseAuthenticatorData(AppAttestObject appAttestObject) {
        try {
            return AppAttestAuthenticatorData.from(appAttestObject.authenticatorData());
        } catch (IllegalArgumentException exception) {
            throw rejected(AppAttestRejection.MALFORMED_AUTH_DATA);
        }
    }

    private void consumeChallenge(String challenge) {
        try {
            deviceChallengeService.consume(challenge);
        } catch (DeviceException exception) {
            appAttestMetrics.recordRejected(AppAttestRejection.CHALLENGE_INVALID);
            throw exception;
        }
    }

    private void validateCertificateChain(AppAttestObject appAttestObject) {
        try {
            appAttestCertificateChainValidator.validate(appAttestObject.certificates());
        } catch (IllegalArgumentException exception) {
            throw rejected(AppAttestRejection.CERTIFICATE_CHAIN_INVALID);
        }
    }

    private void requireMatchingNonce(AppAttestObject appAttestObject, String challenge) {
        byte[] expectedNonce = hash(
                appAttestObject.authenticatorData(),
                clientDataHash(challenge)
        );
        if (!nonceExtensionOf(appAttestObject.credentialCertificate()).matches(expectedNonce)) {
            throw rejected(AppAttestRejection.NONCE_MISMATCH);
        }
    }

    private byte[] clientDataHash(String challenge) {
        return hash(challenge.getBytes(StandardCharsets.US_ASCII));
    }

    private AppAttestNonceExtension nonceExtensionOf(X509Certificate credentialCertificate) {
        try {
            return AppAttestNonceExtension.from(credentialCertificate);
        } catch (IllegalArgumentException exception) {
            throw rejected(AppAttestRejection.NONCE_MISMATCH);
        }
    }

    private void requireMatchingKeyId(AppAttestObject appAttestObject, byte[] keyId) {
        byte[] publicKeyHash = hash(uncompressedPoint(appAttestObject.credentialCertificate()));
        if (!MessageDigest.isEqual(publicKeyHash, keyId)) {
            throw rejected(AppAttestRejection.KEY_ID_MISMATCH);
        }
    }

    private byte[] uncompressedPoint(X509Certificate credentialCertificate) {
        PublicKey publicKey = credentialCertificate.getPublicKey();
        if (!(publicKey instanceof ECPublicKey ecPublicKey)) {
            throw rejected(AppAttestRejection.KEY_ID_MISMATCH);
        }

        ECPoint point = ecPublicKey.getW();
        byte[] encoded = new byte[1 + COORDINATE_BYTE_LENGTH * 2];
        encoded[0] = UNCOMPRESSED_POINT_PREFIX;
        writeCoordinate(point.getAffineX(), encoded, 1);
        writeCoordinate(point.getAffineY(), encoded, 1 + COORDINATE_BYTE_LENGTH);

        return encoded;
    }

    private void writeCoordinate(BigInteger coordinate, byte[] destination, int offset) {
        byte[] value = coordinate.toByteArray();
        if (value.length > COORDINATE_BYTE_LENGTH + 1) {
            throw rejected(AppAttestRejection.KEY_ID_MISMATCH);
        }
        int length = Math.min(value.length, COORDINATE_BYTE_LENGTH);
        System.arraycopy(
                value,
                value.length - length,
                destination,
                offset + COORDINATE_BYTE_LENGTH - length,
                length
        );
    }

    private void requireMatchingAppId(AppAttestAuthenticatorData authenticatorData) {
        byte[] expectedRpIdHash = hash(appAttestProperties.appId().getBytes(StandardCharsets.US_ASCII));
        if (!MessageDigest.isEqual(expectedRpIdHash, authenticatorData.rpIdHash())) {
            throw rejected(AppAttestRejection.APP_ID_MISMATCH);
        }
    }

    private void requireAttestationCounter(AppAttestAuthenticatorData authenticatorData) {
        if (authenticatorData.counter() != ATTESTATION_COUNTER) {
            throw rejected(AppAttestRejection.COUNTER_NOT_ZERO);
        }
    }

    private void requireConfiguredEnvironment(AppAttestAuthenticatorData authenticatorData) {
        if (!appAttestProperties.environment().allows(authenticatorData.aaguid())) {
            throw rejected(AppAttestRejection.ENVIRONMENT_MISMATCH);
        }
    }

    private void requireMatchingCredentialId(AppAttestAuthenticatorData authenticatorData, byte[] keyId) {
        if (!MessageDigest.isEqual(authenticatorData.credentialId(), keyId)) {
            throw rejected(AppAttestRejection.CREDENTIAL_ID_MISMATCH);
        }
    }

    private byte[] hash(byte[]... parts) {
        MessageDigest digest = messageDigest();
        for (byte[] part : parts) {
            digest.update(part);
        }
        return digest.digest();
    }

    private MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 해시를 만들지 못했습니다.", exception);
        }
    }

    private DeviceException rejected(AppAttestRejection rejection) {
        appAttestMetrics.recordRejected(rejection);
        return new DeviceException(DEVICE_ATTESTATION_INVALID);
    }
}
