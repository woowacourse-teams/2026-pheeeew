package com.pheeeew.notification.domain.repository;

import com.pheeeew.notification.domain.DevicePressCount;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class DailyPressCountRepository {

    private final JdbcClient jdbc;

    public List<DevicePressCount> findByDate(LocalDate date) {
        return jdbc.sql("""
                SELECT device_id, SUM(press_count) AS press_count
                FROM device_daily_presses
                WHERE press_date = :date
                GROUP BY device_id
                HAVING SUM(press_count) > 0
                ORDER BY SUM(press_count) DESC, device_id
                """)
                .param("date", date)
                .query((row, index) -> DevicePressCount.of(row.getLong("device_id"), row.getLong("press_count")))
                .list();
    }
}
