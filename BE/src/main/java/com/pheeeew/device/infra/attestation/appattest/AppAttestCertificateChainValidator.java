package com.pheeeew.device.infra.attestation.appattest;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;

public class AppAttestCertificateChainValidator {

    private static final String ROOT_CERTIFICATE_LOCATION = "certificates/apple-app-attestation-root-ca.pem";
    private static final String CERTIFICATE_TYPE = "X.509";
    private static final String CERT_PATH_ALGORITHM = "PKIX";

    private final TrustAnchor appleRootAnchor;

    public AppAttestCertificateChainValidator() {
        this.appleRootAnchor = new TrustAnchor(loadAppleRootCertificate(), null);
    }

    public void validate(List<X509Certificate> certificates) {
        try {
            CertPath certPath = CertificateFactory.getInstance(CERTIFICATE_TYPE)
                    .generateCertPath(certificates);
            PKIXParameters parameters = new PKIXParameters(Set.of(appleRootAnchor));
            parameters.setRevocationEnabled(false);

            CertPathValidator.getInstance(CERT_PATH_ALGORITHM).validate(certPath, parameters);
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("무결성 증명 인증서 체인을 신뢰할 수 없습니다.");
        }
    }

    private static X509Certificate loadAppleRootCertificate() {
        try (InputStream rootCertificate = new ClassPathResource(ROOT_CERTIFICATE_LOCATION).getInputStream()) {
            return (X509Certificate) CertificateFactory.getInstance(CERTIFICATE_TYPE)
                    .generateCertificate(rootCertificate);
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("App Attest 루트 인증서를 읽지 못했습니다.", exception);
        }
    }
}
