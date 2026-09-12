package com.pheeeew.device.infra.attestation;

import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pheeeew.play-integrity")
public record PlayIntegrityProperties(
        String androidPackageName,
        String serviceAccountBase64,
        String cloudProjectNumber,
        boolean requireAttestation,
        boolean skipVerification
) {

    private static final boolean ENFORCEMENT_LOCKED = false;
    private static final Pattern PACKAGE_NAME_PATTERN =
            Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$");
    private static final Pattern CLOUD_PROJECT_NUMBER_PATTERN = Pattern.compile("^[0-9]{1,20}$");

    public PlayIntegrityProperties {
        if (androidPackageName == null || !PACKAGE_NAME_PATTERN.matcher(androidPackageName).matches()) {
            throw new IllegalArgumentException("Play Integrity 안드로이드 패키지명 설정이 올바르지 않습니다.");
        }
        if (hasValue(cloudProjectNumber) && !CLOUD_PROJECT_NUMBER_PATTERN.matcher(cloudProjectNumber).matches()) {
            throw new IllegalArgumentException("Play Integrity Cloud 프로젝트 번호 설정이 올바르지 않습니다.");
        }
        if (ENFORCEMENT_LOCKED && !requireAttestation) {
            throw new IllegalArgumentException("무결성 증명 강제는 설정으로 끌 수 없습니다.");
        }
        if (requireAttestation && !hasCredentials(serviceAccountBase64, cloudProjectNumber)) {
            throw new IllegalArgumentException("무결성 증명을 강제하려면 Play Integrity 자격증명 설정이 필요합니다.");
        }
        if (skipVerification && requireAttestation) {
            throw new IllegalArgumentException("무결성 증명을 강제하면서 검증을 건너뛸 수는 없습니다.");
        }
    }

    public boolean isConfigured() {
        return hasCredentials(serviceAccountBase64, cloudProjectNumber);
    }

    private static boolean hasCredentials(String serviceAccountBase64, String cloudProjectNumber) {
        return hasValue(serviceAccountBase64) && hasValue(cloudProjectNumber);
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public String toString() {
        return "PlayIntegrityProperties[androidPackageName=" + androidPackageName
                + ", serviceAccountBase64=<redacted>"
                + ", cloudProjectNumber=<redacted>"
                + ", requireAttestation=" + requireAttestation
                + ", skipVerification=" + skipVerification + "]";
    }
}
