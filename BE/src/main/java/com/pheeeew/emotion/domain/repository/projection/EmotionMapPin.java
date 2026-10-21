package com.pheeeew.emotion.domain.repository.projection;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;

public record EmotionMapPin(Long id, Long deviceId, Instant createdAt, double longitude, double latitude,
                            EmotionState state, double rotationDegrees, Long stampId) {

    public static EmotionMapPin of(Long id, Long deviceId, Instant createdAt, double longitude, double latitude,
            EmotionState state, double rotationDegrees, Long stampId) {
        return new EmotionMapPin(id, deviceId, createdAt, longitude, latitude, state, rotationDegrees, stampId);
    }
}
