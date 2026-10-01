package com.pheeeew.groups.domain.repository.projection;

import com.pheeeew.emotion.domain.EmotionState;

public interface GroupStatePressScoreProjection extends GroupScoreProjection {

    EmotionState getState();
}
