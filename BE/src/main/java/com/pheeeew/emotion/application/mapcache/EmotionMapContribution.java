package com.pheeeew.emotion.application.mapcache;

import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;

public record EmotionMapContribution(Long id, String regionCode, EmotionState state, Instant createdAt,
                                     boolean hasContent, boolean deleted, Long deviceId, double longitude, double latitude,
                                     double rotationDegrees, Long stampId) {

    public static EmotionMapContribution from(Emotion emotion) {
        return new EmotionMapContribution(emotion.getId(), emotion.getRegionCode(), emotion.getState(),
                emotion.getCreatedAt(), emotion.getMemo() != null || emotion.getContent().hasAudio(),
                emotion.getDeletedAt() != null, emotion.getDeviceId(), emotion.getLongitude(), emotion.getLatitude(),
                emotion.getRotationDegrees(), emotion.getGroupStamp() == null ? null : emotion.getGroupStamp().getId());
    }

    public boolean contributesAt(Instant at) {
        return !deleted && hasContent && !createdAt.isAfter(at);
    }
}
