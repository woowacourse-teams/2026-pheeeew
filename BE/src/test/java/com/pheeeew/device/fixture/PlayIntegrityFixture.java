package com.pheeeew.device.fixture;

import com.pheeeew.device.infra.attestation.PlayIntegrityProperties;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

public final class PlayIntegrityFixture {

    public static final String 우리_패키지명 = "com.pheeeew";
    public static final String JWE_무결성_토큰 =
            "eyJhbGciOiJBMjU2S1ciLCJlbmMiOiJBMjU2R0NNIn0.encryptedKey.initializationVector.cipherText.authTag";
    public static final String JWS_무결성_토큰 = "eyJhbGciOiJSUzI1NiJ9.eyJub25jZSI6IngifQ.signature";
    public static final String 키_조각이_빈_JWE_무결성_토큰 =
            "eyJhbGciOiJkaXIiLCJlbmMiOiJBMjU2R0NNIn0..initializationVector.cipherText.authTag";
    public static final String 서비스_계정_이메일 = "play-integrity@pheeeew-test.iam.gserviceaccount.com";
    public static final String 클라우드_프로젝트_번호 = "123456789012";

    private static final int RSA_KEY_SIZE = 2048;
    private static final KeyPair 서비스_계정_키쌍 = RSA_키쌍을_만든다();

    private PlayIntegrityFixture() {
    }

    public static RSAPublicKey 서비스_계정_공개키() {
        return (RSAPublicKey) 서비스_계정_키쌍.getPublic();
    }

    public static String 서비스_계정_설정(String tokenUri) {
        String serviceAccountJson = """
                {
                  "type": "service_account",
                  "project_id": "pheeeew-test",
                  "client_email": "%s",
                  "token_uri": "%s",
                  "private_key": "%s"
                }
                """.formatted(서비스_계정_이메일, tokenUri, 개인키_PEM());

        return Base64.getEncoder().encodeToString(serviceAccountJson.getBytes());
    }

    public static PlayIntegrityProperties 자격증명이_있는_설정(String tokenUri) {
        return new PlayIntegrityProperties(우리_패키지명, 서비스_계정_설정(tokenUri), 클라우드_프로젝트_번호, false, false, null);
    }

    public static PlayIntegrityProperties 강제가_켜진_설정(String tokenUri) {
        return new PlayIntegrityProperties(우리_패키지명, 서비스_계정_설정(tokenUri), 클라우드_프로젝트_번호, true, false, null);
    }

    public static PlayIntegrityProperties 검증을_건너뛰는_설정(String tokenUri) {
        return new PlayIntegrityProperties(우리_패키지명, 서비스_계정_설정(tokenUri), 클라우드_프로젝트_번호, false, true, null);
    }

    public static PlayIntegrityProperties 자격증명이_없는_설정() {
        return new PlayIntegrityProperties(우리_패키지명, null, null, false, false, null);
    }

    public static String 토큰_응답(String accessToken, long expiresIn) {
        return """
                {"access_token":"%s","expires_in":%d,"token_type":"Bearer"}
                """.formatted(accessToken, expiresIn);
    }

    public static String 복호화_응답(
            String requestPackageName,
            String nonce,
            String appRecognitionVerdict,
            String deviceRecognitionVerdicts
    ) {
        return """
                {
                  "tokenPayloadExternal": {
                    "requestDetails": {
                      "requestPackageName": "%s",
                      "nonce": "%s",
                      "timestampMillis": "1757000000000"
                    },
                    "appIntegrity": {
                      "appRecognitionVerdict": "%s",
                      "packageName": "%s",
                      "certificateSha256Digest": ["6a6a1474b5cbbb2b1aa57e0bc3"],
                      "versionCode": "42"
                    },
                    "deviceIntegrity": {
                      "deviceRecognitionVerdict": [%s]
                    },
                    "accountDetails": {
                      "appLicensingVerdict": "LICENSED"
                    }
                  }
                }
                """.formatted(requestPackageName, nonce, appRecognitionVerdict, requestPackageName,
                deviceRecognitionVerdicts);
    }

    public static String 정품_복호화_응답(String nonce) {
        return 복호화_응답(우리_패키지명, nonce, "PLAY_RECOGNIZED", "\"MEETS_DEVICE_INTEGRITY\", \"MEETS_BASIC_INTEGRITY\"");
    }

    private static String 개인키_PEM() {
        String base64Key = Base64.getEncoder().encodeToString(서비스_계정_키쌍.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\\n" + base64Key + "\\n-----END PRIVATE KEY-----\\n";
    }

    private static KeyPair RSA_키쌍을_만든다() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(RSA_KEY_SIZE);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("테스트용 RSA 키 쌍을 만들 수 없습니다.", exception);
        }
    }
}
