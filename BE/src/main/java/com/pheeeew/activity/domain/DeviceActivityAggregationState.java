package com.pheeeew.activity.domain;

import com.pheeeew.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import lombok.Builder;
import lombok.Getter;

// 수집 시작 시 한 번 생성하며, BaseEntity의 createdAt을 수집 시작 시각으로 사용한다.
@Getter
@Table(name = "device_activity_aggregation_states")
@Entity
public class DeviceActivityAggregationState extends BaseEntity {

    private static final ZoneId ACTIVITY_ZONE = ZoneId.of("Asia/Seoul");

    @Id
    @Column(nullable = false, updatable = false)
    private Long id;

    // 현재 DAU·MAU 집계가 한 번도 성공하지 않았다면 null이다.
    @Column(name = "last_aggregated_at")
    private Instant lastAggregatedAt;

    // 수집 시작일부터 연속해서 최종 저장을 마친 마지막 KST 날짜다. 완료 전에는 null이다.
    @Column(name = "last_finalized_date")
    private LocalDate lastFinalizedDate;

    @Builder
    protected DeviceActivityAggregationState() {
        this.id = 1L;
    }

    public LocalDate nextFinalizationDate() {
        if (lastFinalizedDate == null) {
            return getCreatedAt().atZone(ACTIVITY_ZONE).toLocalDate();
        }
        return lastFinalizedDate.plusDays(1);
    }

    public void completeFinalization(LocalDate activityDate) {
        if (!nextFinalizationDate().equals(activityDate)) {
            throw new IllegalArgumentException("최종 집계는 수집 시작일부터 날짜 순서대로 완료해야 합니다.");
        }
        this.lastFinalizedDate = activityDate;
    }

    public void completeAggregation(Instant aggregatedAt) {
        this.lastAggregatedAt = Objects.requireNonNull(aggregatedAt);
    }
}
