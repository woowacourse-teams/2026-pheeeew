package com.pheeeew.activity.fixture;

import com.pheeeew.activity.domain.DeviceActivitySummary;
import com.pheeeew.device.domain.DevicePlatform;
import java.time.Instant;
import java.time.LocalDate;

public final class DeviceActivitySummaryFixture {

    private DeviceActivitySummaryFixture() {
    }

    public static DeviceActivitySummary.DeviceActivitySummaryBuilder 기본_활동_집계_빌더() {
        return DeviceActivitySummary.builder()
                .activityDate(LocalDate.of(2026, 9, 16))
                .platform(DevicePlatform.ANDROID)
                .dau(3)
                .mau(10)
                .aggregatedAt(Instant.parse("2026-09-16T15:05:00Z"));
    }
}
