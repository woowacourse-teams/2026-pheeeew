package com.pheeeew.emotion.application.dto;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.projection.DeviceDailyPressSum;

public record EmotionDailyPressRankingItem(
        int rank,
        String nickname,
        long pressCount,
        boolean mine,
        EmotionState representativeState
) {
    public static EmotionDailyPressRankingItem of(int rank, DeviceDailyPressSum sum) {
        return new EmotionDailyPressRankingItem(
                rank, sum.nickname(), sum.pressCount(), sum.mine(), sum.representativeState());
    }
}
