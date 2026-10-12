package com.pheeeew.emotion.domain.repository;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.projection.DevicePressSum;
import com.pheeeew.emotion.domain.repository.projection.DeviceWeeklyPressSum;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class DeviceDailyPressRepository {

    private final JdbcClient jdbc;

    public void increase(LocalDate pressDate, String state, Long deviceId, int delta, Instant now) {
        jdbc.sql("""
                INSERT INTO device_daily_presses
                     (press_date, state, device_id, press_count, created_at, updated_at)
                     VALUES (:pressDate, :state, :deviceId, :delta, :now, :now)
                ON CONFLICT (press_date, state, device_id) DO UPDATE
                   SET press_count = device_daily_presses.press_count + :delta,
                       updated_at = :now
                """).param("pressDate", pressDate).param("state", state)
                .param("deviceId", deviceId).param("delta", delta).param("now", now.atOffset(ZoneOffset.UTC))
                .update();
    }

    public List<DevicePressSum> findByPressDateAndDeviceId(LocalDate pressDate, Long deviceId) {
        return jdbc.sql("""
                SELECT state, SUM(press_count) AS press_count
                FROM device_daily_presses
                WHERE press_date = :pressDate
                  AND device_id = :deviceId
                GROUP BY state
                """).param("pressDate", pressDate).param("deviceId", deviceId)
                .query((row, index) -> DevicePressSum.of(
                        EmotionState.valueOf(row.getString("state")), row.getLong("press_count")))
                .list();
    }

    public long sumByPressDate(LocalDate pressDate) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(press_count), 0)
                FROM device_daily_presses
                WHERE press_date = :pressDate
                """).param("pressDate", pressDate)
                .query(Long.class)
                .single();
    }

    public List<DeviceWeeklyPressSum> findDevicePressSums(
            LocalDate startDate,
            LocalDate endDate,
            UUID devicePublicId
    ) {
        return jdbc.sql("""
                SELECT COALESCE(device.nickname, '익명') AS nickname,
                       SUM(press.press_count) AS press_count,
                       device.public_id = :devicePublicId AS mine
                FROM device_daily_presses press
                JOIN devices device ON device.id = press.device_id
                WHERE press.press_date >= :startDate
                  AND press.press_date < :endDate
                GROUP BY press.device_id, device.nickname, device.public_id
                ORDER BY SUM(press.press_count) DESC, COALESCE(device.nickname, '익명'), press.device_id
                """).param("startDate", startDate).param("endDate", endDate)
                .param("devicePublicId", devicePublicId)
                .query((row, index) -> DeviceWeeklyPressSum.of(
                        row.getString("nickname"), row.getLong("press_count"), row.getBoolean("mine")))
                .list();
    }

    public List<DeviceWeeklyPressSum> findDevicePressSumsByState(
            LocalDate startDate,
            LocalDate endDate,
            EmotionState state,
            UUID devicePublicId
    ) {
        return jdbc.sql("""
                SELECT COALESCE(device.nickname, '익명') AS nickname,
                       SUM(press.press_count) AS press_count,
                       device.public_id = :devicePublicId AS mine
                FROM device_daily_presses press
                JOIN devices device ON device.id = press.device_id
                WHERE press.press_date >= :startDate
                  AND press.press_date < :endDate
                  AND press.state = :state
                GROUP BY press.device_id, device.nickname, device.public_id
                ORDER BY SUM(press.press_count) DESC, COALESCE(device.nickname, '익명'), press.device_id
                """).param("startDate", startDate).param("endDate", endDate)
                .param("state", state.name())
                .param("devicePublicId", devicePublicId)
                .query((row, index) -> DeviceWeeklyPressSum.of(
                        row.getString("nickname"), row.getLong("press_count"), row.getBoolean("mine")))
                .list();
    }
}
