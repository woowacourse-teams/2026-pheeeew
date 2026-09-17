package com.pheeeew.activity.domain;

import com.pheeeew.common.domain.BaseEntity;
import com.pheeeew.device.domain.DevicePlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "device_activity_summaries")
@Entity
public class DeviceActivitySummary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_date", nullable = false, updatable = false)
    private LocalDate activityDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private DevicePlatform platform;

    @Column(nullable = false, updatable = false)
    private long dau;

    @Column(nullable = false, updatable = false)
    private long mau;

    @Column(name = "aggregated_at", nullable = false, updatable = false)
    private Instant aggregatedAt;

    @Builder
    private DeviceActivitySummary(LocalDate activityDate, DevicePlatform platform, long dau, long mau, Instant aggregatedAt) {
        this.activityDate = Objects.requireNonNull(activityDate);
        this.platform = Objects.requireNonNull(platform);
        this.aggregatedAt = Objects.requireNonNull(aggregatedAt);

        if (dau < 0 || mau < dau) {
            throw new IllegalArgumentException("활성 기기 수는 음수일 수 없고 MAU는 DAU 이상이어야 합니다.");
        }
        this.dau = dau;
        this.mau = mau;
    }
}
