package com.pheeeew.activity.domain.repository;

import com.pheeeew.activity.domain.DeviceActivitySummary;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DeviceActivitySummaryRepository extends JpaRepository<DeviceActivitySummary, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO device_activity_summaries
                (activity_date, platform, dau, mau, aggregated_at, created_at, updated_at)
            VALUES (:activityDate, :platform, :dau, :mau, :aggregatedAt, :aggregatedAt, :aggregatedAt)
            ON CONFLICT (activity_date, platform) DO NOTHING
            """, nativeQuery = true)
    int saveIfAbsent(LocalDate activityDate, String platform, long dau, long mau, Instant aggregatedAt);
}
