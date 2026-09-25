package com.pheeeew.emotion.application.dto;

import com.pheeeew.emotion.application.AudioPlaybackUrlIssuer.PlaybackUrl;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.groups.application.dto.GroupStampResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EmotionDetailView(
        Long id,
        double longitude,
        double latitude,
        Instant createdAt,
        EmotionState state,
        double rotationDegrees,
        String memo,
        String nickname,
        List<EmotionEmojiResult> emojis,
        boolean hasAudio,
        PlaybackUrl audio,
        GroupStampResult groupStamp,
        UUID groupId,
        boolean isMine
) {

    public static EmotionDetailView of(Emotion emotion, List<EmotionEmojiResult> emojis) {
        return of(emotion, emojis, null);
    }

    public static EmotionDetailView of(Emotion emotion, List<EmotionEmojiResult> emojis, PlaybackUrl audio) {
        return of(emotion, emojis, audio, null);
    }

    public static EmotionDetailView of(Emotion emotion, List<EmotionEmojiResult> emojis, PlaybackUrl audio,
            GroupStampResult groupStamp) {
        return of(emotion, emojis, audio, groupStamp, null);
    }

    public static EmotionDetailView of(Emotion emotion, List<EmotionEmojiResult> emojis, PlaybackUrl audio,
            GroupStampResult groupStamp, Long viewerDeviceId) {
        return new EmotionDetailView(
                emotion.getId(), emotion.getLongitude(), emotion.getLatitude(), emotion.getCreatedAt(),
                emotion.getState(), emotion.getRotationDegrees(), emotion.getMemo(), emotion.getNickname(),
                List.copyOf(emojis), emotion.getContent().getAudio() != null, audio, groupStamp,
                emotion.getGroupStamp() == null ? null : emotion.getGroupStamp().getGroup().getPublicId(),
                emotion.getDeviceId() != null && emotion.getDeviceId().equals(viewerDeviceId)
        );
    }
}
