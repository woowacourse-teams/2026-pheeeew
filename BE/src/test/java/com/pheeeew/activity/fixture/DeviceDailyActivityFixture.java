package com.pheeeew.activity.fixture;

import com.pheeeew.activity.domain.DeviceDailyActivity;
import java.time.LocalDate;

public final class DeviceDailyActivityFixture {

    private DeviceDailyActivityFixture() {
    }

    public static DeviceDailyActivity.DeviceDailyActivityBuilder 기본_일별_활동_빌더() {
        return DeviceDailyActivity.builder()
                .deviceId(1L)
                .activityDate(LocalDate.of(2026, 9, 16));
    }
}
