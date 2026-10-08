package com.pheeeew.emotion.domain.repository.projection;

import com.pheeeew.emotion.domain.EmotionState;

public record DevicePressSum(EmotionState state, long pressCount) {

    public static DevicePressSum of(EmotionState state, long pressCount) {
        return new DevicePressSum(state, pressCount);
    }
}
