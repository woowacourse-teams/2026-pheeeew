package com.pheeeew.activity.domain;

import com.pheeeew.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "device_daily_activities")
@Entity
public class DeviceDailyActivity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, updatable = false)
    private Long deviceId;

    @Column(name = "activity_date", nullable = false, updatable = false)
    private LocalDate activityDate;

    @Builder
    private DeviceDailyActivity(Long deviceId, LocalDate activityDate) {
        this.deviceId = Objects.requireNonNull(deviceId);
        this.activityDate = Objects.requireNonNull(activityDate);
    }
}
