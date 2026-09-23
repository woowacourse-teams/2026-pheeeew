package com.pheeeew.sigh.domain.repository.projection;

import com.pheeeew.sigh.domain.Sigh;

public interface EmotionDetailProjection {

    Sigh getEmotion();

    boolean getLiked();
}
