package com.pheeeew.notification.domain;

import java.util.Map;

public record PushMessage(String title, String body, Map<String, String> data) {

    public static PushMessage of(String title, String body, Map<String, String> data) {
        return new PushMessage(title, body, Map.copyOf(data));
    }
}
