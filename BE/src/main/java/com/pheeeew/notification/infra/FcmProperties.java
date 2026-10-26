package com.pheeeew.notification.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pheeeew.fcm")
public record FcmProperties(String projectId, String serviceAccountBase64) {

    public boolean isConfigured() {
        return hasValue(projectId) && hasValue(serviceAccountBase64);
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public String toString() {
        return "FcmProperties[projectId=" + projectId + ", serviceAccountBase64=<redacted>]";
    }
}
