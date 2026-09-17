package com.pheeeew.activity.domain.repository;

import com.pheeeew.activity.domain.DeviceActivityAggregationState;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DeviceActivityAggregationStateRepository extends JpaRepository<DeviceActivityAggregationState, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO device_activity_aggregation_states (id, created_at, updated_at)
            VALUES (1, :startedAt, :startedAt)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int saveIfAbsent(Instant startedAt);
}
