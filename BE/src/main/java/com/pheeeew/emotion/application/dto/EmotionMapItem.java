package com.pheeeew.emotion.application.dto;

import java.time.Instant;

public record EmotionMapItem(Long id, double longitude, double latitude, Instant createdAt) {

    public static EmotionMapItem of(Long id, double longitude, double latitude, Instant createdAt) {
        return new EmotionMapItem(id, longitude, latitude, createdAt);
    }
}
