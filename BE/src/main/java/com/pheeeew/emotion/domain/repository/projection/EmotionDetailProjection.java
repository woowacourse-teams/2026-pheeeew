package com.pheeeew.emotion.domain.repository.projection;

import com.pheeeew.emotion.domain.Emotion;

public interface EmotionDetailProjection {

    Emotion getEmotion();

    boolean getLiked();
}
