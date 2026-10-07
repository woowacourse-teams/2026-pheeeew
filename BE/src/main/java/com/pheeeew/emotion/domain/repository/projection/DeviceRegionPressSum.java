package com.pheeeew.emotion.domain.repository.projection;

import com.pheeeew.emotion.domain.EmotionState;

public record DeviceRegionPressSum(EmotionState state, long pressCount) {

    public static DeviceRegionPressSum of(EmotionState state, long pressCount) {
        return new DeviceRegionPressSum(state, pressCount);
    }
}
