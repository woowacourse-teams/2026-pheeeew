package com.pheeeew.collection.fixture;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class DeviceDailyStampFixture {

    private DeviceDailyStampFixture() {
    }

    public static void 일별_스탬프를_저장한다(JdbcClient jdbc, Long deviceId, LocalDate date, EmotionState state, long count) {
        jdbc.sql("""
                INSERT INTO device_daily_stamps (stamp_date, state, device_id, stamp_count, created_at, updated_at)
                VALUES (:date, :state, :deviceId, :count, NOW(), NOW())
                """)
                .param("date", date)
                .param("state", state.name())
                .param("deviceId", deviceId)
                .param("count", count)
                .update();
    }
}
