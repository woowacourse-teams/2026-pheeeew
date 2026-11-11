package com.pheeeew.emotion.domain.repository.projection;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;

/** audio는 녹음 기록 여부다. 메모와 녹음은 함께 있을 수 없어 false면 메모 기록이다. */
public record EmotionMapPin(Long id, Long deviceId, Instant createdAt, double longitude, double latitude,
                            EmotionState state, double rotationDegrees, Long stampId, boolean audio, String regionCode) {

    public static EmotionMapPin of(Long id, Long deviceId, Instant createdAt, double longitude, double latitude,
            EmotionState state, double rotationDegrees, Long stampId, boolean audio, String regionCode) {
        return new EmotionMapPin(id, deviceId, createdAt, longitude, latitude, state, rotationDegrees, stampId, audio,
                regionCode);
    }
}
