package com.pheeeew.collection.domain.repository;

import com.pheeeew.collection.domain.repository.projection.CollectionActivityCounts;
import com.pheeeew.emotion.domain.EmotionState;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class CollectionProgressRepository {

    private final JdbcClient jdbc;

    public List<CollectionActivityCounts> findByDateAndDeviceId(LocalDate date, Long deviceId) {
        return jdbc.sql("""
                SELECT state, SUM(stamp_count) AS stamp_count, SUM(press_count) AS press_count
                FROM (
                    SELECT state, stamp_count, 0::bigint AS press_count
                    FROM device_daily_stamps
                    WHERE stamp_date = :date AND device_id = :deviceId
                    UNION ALL
                    SELECT state, 0::bigint AS stamp_count, press_count
                    FROM device_daily_presses
                    WHERE press_date = :date AND device_id = :deviceId
                ) activity
                GROUP BY state
                """)
                .param("date", date)
                .param("deviceId", deviceId)
                .query((row, index) -> CollectionActivityCounts.of(EmotionState.valueOf(row.getString("state")),
                        row.getLong("stamp_count"), row.getLong("press_count")))
                .list();
    }
}
