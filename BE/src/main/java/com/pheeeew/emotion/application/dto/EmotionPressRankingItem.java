package com.pheeeew.emotion.application.dto;

import com.pheeeew.emotion.domain.repository.projection.DeviceWeeklyPressSum;

public record EmotionPressRankingItem(int rank, String nickname, long pressCount, boolean mine) {

    public static EmotionPressRankingItem of(int rank, DeviceWeeklyPressSum sum) {
        return new EmotionPressRankingItem(rank, sum.nickname(), sum.pressCount(), sum.mine());
    }
}
