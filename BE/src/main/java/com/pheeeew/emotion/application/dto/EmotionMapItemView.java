package com.pheeeew.emotion.application.dto;

import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.groups.application.dto.GroupStampResult;
import java.time.Instant;
import java.util.UUID;

public record EmotionMapItemView(
        Long id,
        double longitude,
        double latitude,
        Instant createdAt,
        EmotionState state,
        double rotationDegrees,
        GroupStampResult groupStamp,
        UUID groupId
) {

    public static EmotionMapItemView of(Emotion emotion, GroupStampResult stamp) {
        return new EmotionMapItemView(emotion.getId(), emotion.getLongitude(), emotion.getLatitude(),
                emotion.getCreatedAt(), emotion.getState(), emotion.getRotationDegrees(), stamp,
                emotion.getGroupStamp() == null ? null : emotion.getGroupStamp().getGroup().getPublicId());
    }
}
