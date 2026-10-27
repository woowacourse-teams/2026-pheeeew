package com.pheeeew.notification.domain;

import com.pheeeew.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(NotificationRankSnapshotId.class)
@Table(name = "notification_rank_snapshots")
@Entity
public class NotificationRankSnapshot extends BaseEntity {

    @Id
    @Column(name = "round_at")
    private Instant roundAt;

    @Id
    @Column(name = "device_id")
    private Long deviceId;

    @Column(nullable = false, updatable = false)
    private int rank;

    @Builder
    private NotificationRankSnapshot(Instant roundAt, Long deviceId, int rank) {
        if (roundAt == null || deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("회차 시각과 기기는 필수입니다.");
        }
        if (rank < 1) {
            throw new IllegalArgumentException("순위는 1 이상입니다.");
        }
        this.roundAt = roundAt;
        this.deviceId = deviceId;
        this.rank = rank;
    }
}
