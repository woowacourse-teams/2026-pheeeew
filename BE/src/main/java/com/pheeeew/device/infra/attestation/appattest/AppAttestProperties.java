package com.pheeeew.device.infra.attestation.appattest;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pheeeew.app-attest")
public record AppAttestProperties(
        List<String> teamIds,
        String bundleId,
        AppAttestEnvironment environment,
        boolean requireAttestation
) {

    private static final boolean ENFORCEMENT_LOCKED = false;
    private static final String APP_ID_SEPARATOR = ".";
    private static final Pattern TEAM_ID_PATTERN = Pattern.compile("^[A-Z0-9]{10}$");
    private static final Pattern BUNDLE_ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$");

    public AppAttestProperties {
        environment = environment == null ? AppAttestEnvironment.PRODUCTION : environment;
        teamIds = teamIds == null ? List.of() : List.copyOf(teamIds);
        if (teamIds.stream().anyMatch(teamId -> !hasValue(teamId) || !TEAM_ID_PATTERN.matcher(teamId).matches())) {
            throw new IllegalArgumentException("App Attest Team ID 설정이 올바르지 않습니다.");
        }
        if (teamIds.size() != teamIds.stream().distinct().count()) {
            throw new IllegalArgumentException("App Attest Team ID 설정에 같은 값이 둘 이상 있습니다.");
        }
        if (hasValue(bundleId) && !BUNDLE_ID_PATTERN.matcher(bundleId).matches()) {
            throw new IllegalArgumentException("App Attest Bundle ID 설정이 올바르지 않습니다.");
        }
        if (ENFORCEMENT_LOCKED && !requireAttestation) {
            throw new IllegalArgumentException("무결성 증명 강제는 설정으로 끌 수 없습니다.");
        }
        if (requireAttestation && !hasAppId(teamIds, bundleId)) {
            throw new IllegalArgumentException("무결성 증명을 강제하려면 App Attest App ID 설정이 필요합니다.");
        }
    }

    public boolean isConfigured() {
        return hasAppId(teamIds, bundleId);
    }

    public List<String> appIds() {
        return teamIds.stream()
                .map(teamId -> teamId + APP_ID_SEPARATOR + bundleId)
                .toList();
    }

    private static boolean hasAppId(List<String> teamIds, String bundleId) {
        return !teamIds.isEmpty() && hasValue(bundleId);
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }
}
