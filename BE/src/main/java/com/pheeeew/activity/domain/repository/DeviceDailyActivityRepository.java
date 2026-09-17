package com.pheeeew.activity.domain.repository;

import com.pheeeew.activity.domain.DeviceDailyActivity;
import com.pheeeew.activity.domain.repository.projection.DeviceActivityCountProjection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DeviceDailyActivityRepository extends JpaRepository<DeviceDailyActivity, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO device_daily_activities (device_id, activity_date, created_at, updated_at)
                 SELECT id, :activityDate, :recordedAt, :recordedAt
                   FROM devices
                  WHERE public_id = :devicePublicId
            ON CONFLICT (activity_date, device_id) DO NOTHING
            """, nativeQuery = true)
    int saveIfAbsent(UUID devicePublicId, LocalDate activityDate, Instant recordedAt);

    @Query(value = """
            SELECT d.platform AS platform,
                   COUNT(*) FILTER (WHERE a.activity_date = :activityDate) AS dau,
                   COUNT(DISTINCT a.device_id) AS mau
              FROM device_daily_activities a
              JOIN devices d ON d.id = a.device_id
             WHERE a.activity_date BETWEEN :startDate AND :activityDate
             GROUP BY d.platform
            """, nativeQuery = true)
    List<DeviceActivityCountProjection> findCounts(LocalDate startDate, LocalDate activityDate);
}
