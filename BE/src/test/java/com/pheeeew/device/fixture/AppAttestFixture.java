package com.pheeeew.device.fixture;

import com.pheeeew.device.infra.attestation.appattest.AppAttestCertificateChainValidator;
import com.pheeeew.device.infra.attestation.appattest.AppAttestEnvironment;
import com.pheeeew.device.infra.attestation.appattest.AppAttestObject;
import com.pheeeew.device.infra.attestation.appattest.AppAttestObjectDecoder;
import com.pheeeew.device.infra.attestation.appattest.AppAttestProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

public final class AppAttestFixture {

    public static final String 팀_ID = "1234567890";
    public static final String 번들_ID = "com.pheeeew.app";
    public static final String 증명에_묶인_challenge = "appattest-fixture-challenge-000000000000000";
    public static final String 증명에_묶이지_않은_challenge = "appattest-fixture-challenge-000000000000001";
    public static final String 키_식별자 = "sTHBS+RSPepvh/O5Bu2ZMFH0JkMw79cUePN+F/NVxAg=";
    public static final String DER_공개키_정보를_해시한_키_식별자 = "Yw16i11PpeuI/os+Y6beW0gzX+u+3AQFz87bPAH7qk4=";

    public static final String 애플_표본_키_식별자 = "zgSY9YSD+7TaDXssY6WlOPVS1K3Lmk+pFhlcSWE+ZV0=";
    public static final String 애플_표본_nonce = "h7fQbZOkKU5G8BHma2zEAPC6sgcpl2xhlYC0KuYL/24=";
    public static final String 애플_표본_rpIdHash = "9EZtaPketsEGIMt+Y8coMkRoXuHWRntUFg51MXIFfwM=";
    public static final String 애플_표본_aaguid = "61707061747465737400000000000000";
    public static final int 애플_표본_바이트_수 = 5906;
    public static final int 애플_표본_authData_바이트_수 = 226;
    public static final int 애플_표본_리프_바이트_수 = 1057;
    public static final int 애플_표본_중간_CA_바이트_수 = 583;

    private static final String 리소스_경로 = "/appattest/";
    private static final String 인증서_타입 = "X.509";
    private static final String 체인_검증_알고리즘 = "PKIX";

    private AppAttestFixture() {
    }

    public static String 애플_표본_증명() {
        return 텍스트를_읽는다("apple-sample-attestation.txt");
    }

    public static AppAttestObject 애플_표본_객체() {
        AppAttestObjectDecoder decoder = new AppAttestObjectDecoder();
        return decoder.decode(decoder.decodeToken(애플_표본_증명()));
    }

    public static String 정품_증명() {
        return 텍스트를_읽는다("production-attestation.txt");
    }

    public static String 개발_증명() {
        return 텍스트를_읽는다("development-attestation.txt");
    }

    public static String 샌드박스_증명() {
        return 텍스트를_읽는다("sandbox-attestation.txt");
    }

    public static String 다른_앱으로_만든_증명() {
        return 텍스트를_읽는다("other-app-id-attestation.txt");
    }

    public static String 카운터가_1인_증명() {
        return 텍스트를_읽는다("counter-one-attestation.txt");
    }

    public static String 키_식별자가_어긋난_증명() {
        return 텍스트를_읽는다("credential-id-mismatch-attestation.txt");
    }

    public static String 형식이_다른_증명() {
        return 텍스트를_읽는다("unsupported-format-attestation.txt");
    }

    public static String authData가_86바이트인_증명() {
        return 텍스트를_읽는다("short-auth-data-attestation.txt");
    }

    public static X509Certificate 문맥_태그가_없는_리프() {
        return 인증서를_읽는다("nonce-extension-without-context-tag.pem");
    }

    public static AppAttestCertificateChainValidator 합성_루트를_신뢰하는_체인_검증기() {
        return new SyntheticRootChainValidator();
    }

    public static AppAttestProperties 설정된_app_attest_설정() {
        return new AppAttestProperties(팀_ID, 번들_ID, AppAttestEnvironment.PRODUCTION, false);
    }

    public static AppAttestProperties 개발_환경_설정() {
        return new AppAttestProperties(팀_ID, 번들_ID, AppAttestEnvironment.DEVELOPMENT, false);
    }

    public static AppAttestProperties 설정이_없는_app_attest_설정() {
        return new AppAttestProperties(null, null, AppAttestEnvironment.PRODUCTION, false);
    }

    public static AppAttestProperties 강제가_켜진_설정() {
        return new AppAttestProperties(팀_ID, 번들_ID, AppAttestEnvironment.PRODUCTION, true);
    }

    private static String 텍스트를_읽는다(String fileName) {
        try (InputStream resource = 리소스를_연다(fileName)) {
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            throw new IllegalStateException("App Attest 픽스처를 읽지 못했습니다: " + fileName, exception);
        }
    }

    private static X509Certificate 인증서를_읽는다(String fileName) {
        try (InputStream resource = 리소스를_연다(fileName)) {
            return (X509Certificate) CertificateFactory.getInstance(인증서_타입).generateCertificate(resource);
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("App Attest 픽스처 인증서를 읽지 못했습니다: " + fileName, exception);
        }
    }

    private static InputStream 리소스를_연다(String fileName) {
        InputStream resource = AppAttestFixture.class.getResourceAsStream(리소스_경로 + fileName);
        if (resource == null) {
            throw new IllegalStateException("App Attest 픽스처가 없습니다: " + fileName);
        }
        return resource;
    }

    private static final class SyntheticRootChainValidator extends AppAttestCertificateChainValidator {

        private final TrustAnchor 합성_루트 = new TrustAnchor(인증서를_읽는다("synthetic-root-ca.pem"), null);

        @Override
        public void validate(List<X509Certificate> certificates) {
            try {
                CertPath certPath = CertificateFactory.getInstance(인증서_타입).generateCertPath(certificates);
                PKIXParameters parameters = new PKIXParameters(Set.of(합성_루트));
                parameters.setRevocationEnabled(false);

                CertPathValidator.getInstance(체인_검증_알고리즘).validate(certPath, parameters);
            } catch (GeneralSecurityException exception) {
                throw new IllegalArgumentException("합성 인증서 체인을 신뢰할 수 없습니다.");
            }
        }
    }
}
