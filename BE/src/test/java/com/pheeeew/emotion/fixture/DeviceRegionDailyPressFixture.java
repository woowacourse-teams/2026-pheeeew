package com.pheeeew.emotion.fixture;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class DeviceRegionDailyPressFixture {

    public static final String 검증용_읍면동 = "11010530";

    private DeviceRegionDailyPressFixture() {
    }

    public static void 개인_프레스를_저장한다(
            JdbcClient jdbc,
            Long deviceId,
            LocalDate 날짜,
            EmotionState 감정,
            long 횟수
    ) {
        개인_프레스를_저장한다(jdbc, deviceId, 검증용_읍면동, 날짜, 감정, 횟수);
    }

    public static void 개인_프레스를_저장한다(
            JdbcClient jdbc,
            Long deviceId,
            String regionCode,
            LocalDate 날짜,
            EmotionState 감정,
            long 횟수
    ) {
        jdbc.sql("""
                        INSERT INTO device_region_daily_presses
                            (press_date, region_code, state, device_id, press_count, created_at, updated_at)
                        VALUES (:pressDate, :regionCode, :state, :deviceId, :pressCount, NOW(), NOW())
                        ON CONFLICT (press_date, region_code, state, device_id) DO UPDATE
                           SET press_count = device_region_daily_presses.press_count + EXCLUDED.press_count,
                               updated_at = NOW()
                        """)
                .param("pressDate", 날짜)
                .param("regionCode", regionCode)
                .param("state", 감정.name())
                .param("deviceId", deviceId)
                .param("pressCount", 횟수)
                .update();
    }
}
