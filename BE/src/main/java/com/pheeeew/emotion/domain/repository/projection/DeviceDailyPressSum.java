package com.pheeeew.emotion.domain.repository.projection;

import com.pheeeew.emotion.domain.EmotionState;

public record DeviceDailyPressSum(
        String nickname,
        long pressCount,
        boolean mine,
        EmotionState representativeState
) {
    public static DeviceDailyPressSum of(
            String nickname, long pressCount, boolean mine, EmotionState representativeState
    ) {
        return new DeviceDailyPressSum(nickname, pressCount, mine, representativeState);
    }
}
