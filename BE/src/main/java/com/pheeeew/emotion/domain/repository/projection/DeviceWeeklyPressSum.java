package com.pheeeew.emotion.domain.repository.projection;

public record DeviceWeeklyPressSum(String nickname, long pressCount, boolean mine) {

    public static DeviceWeeklyPressSum of(String nickname, long pressCount, boolean mine) {
        return new DeviceWeeklyPressSum(nickname, pressCount, mine);
    }
}
