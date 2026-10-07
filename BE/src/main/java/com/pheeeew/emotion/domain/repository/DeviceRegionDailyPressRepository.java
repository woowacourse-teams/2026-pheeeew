package com.pheeeew.emotion.domain.repository;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.projection.DeviceRegionPressSum;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class DeviceRegionDailyPressRepository {

    private final JdbcClient jdbc;

    public void increase(
            LocalDate pressDate, String regionCode, String state, Long deviceId, int delta, Instant now
    ) {
        jdbc.sql("""
                INSERT INTO device_region_daily_presses
                     (press_date, region_code, state, device_id, press_count, created_at, updated_at)
                     VALUES (:pressDate, :regionCode, :state, :deviceId, :delta, :now, :now)
                ON CONFLICT (press_date, region_code, state, device_id) DO UPDATE
                   SET press_count = device_region_daily_presses.press_count + :delta,
                       updated_at = :now
                """).param("pressDate", pressDate).param("regionCode", regionCode).param("state", state)
                .param("deviceId", deviceId).param("delta", delta).param("now", now.atOffset(ZoneOffset.UTC))
                .update();
    }

    public List<DeviceRegionPressSum> findByPressDateAndRegionCodeAndDeviceId(
            LocalDate pressDate, String regionCode, Long deviceId
    ) {
        return jdbc.sql("""
                SELECT state, press_count
                FROM device_region_daily_presses
                WHERE press_date = :pressDate
                  AND region_code = :regionCode
                  AND device_id = :deviceId
                """).param("pressDate", pressDate).param("regionCode", regionCode).param("deviceId", deviceId)
                .query((row, index) -> DeviceRegionPressSum.of(
                        EmotionState.valueOf(row.getString("state")), row.getLong("press_count")))
                .list();
    }
}
