package com.pheeeew.collection.domain.repository;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class DeviceDailyStampRepository {

    private final JdbcClient jdbc;

    public void increase(LocalDate stampDate, EmotionState state, Long deviceId, Instant now) {
        jdbc.sql("""
                INSERT INTO device_daily_stamps
                     (stamp_date, state, device_id, stamp_count, created_at, updated_at)
                     VALUES (:stampDate, :state, :deviceId, 1, :now, :now)
                ON CONFLICT (stamp_date, state, device_id) DO UPDATE
                   SET stamp_count = device_daily_stamps.stamp_count + 1,
                       updated_at = :now
                """).param("stampDate", stampDate).param("state", state.name())
                .param("deviceId", deviceId).param("now", now.atOffset(ZoneOffset.UTC))
                .update();
    }
}
