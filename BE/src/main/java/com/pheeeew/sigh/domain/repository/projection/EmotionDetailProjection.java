package com.pheeeew.sigh.domain.repository.projection;

import com.pheeeew.sigh.domain.Emotion;

public interface EmotionDetailProjection {

    Emotion getEmotion();

    boolean getLiked();
}
