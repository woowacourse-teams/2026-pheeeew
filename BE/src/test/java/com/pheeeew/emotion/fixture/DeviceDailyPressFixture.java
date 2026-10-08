package com.pheeeew.emotion.fixture;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class DeviceDailyPressFixture {

    private DeviceDailyPressFixture() {
    }

    public static void 개인_프레스를_저장한다(
            JdbcClient jdbc,
            Long deviceId,
            LocalDate 날짜,
            EmotionState 감정,
            long 횟수
    ) {
        jdbc.sql("""
                        INSERT INTO device_daily_presses
                            (press_date, state, device_id, press_count, created_at, updated_at)
                        VALUES (:pressDate, :state, :deviceId, :pressCount, NOW(), NOW())
                        ON CONFLICT (press_date, state, device_id) DO UPDATE
                           SET press_count = device_daily_presses.press_count + EXCLUDED.press_count,
                               updated_at = NOW()
                        """)
                .param("pressDate", 날짜)
                .param("state", 감정.name())
                .param("deviceId", deviceId)
                .param("pressCount", 횟수)
                .update();
    }
}
