package com.pheeeew.notification.domain;

import com.pheeeew.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notification_rank_rounds")
@Entity
public class NotificationRankRound extends BaseEntity {

    @Id
    @Column(name = "round_at")
    private Instant roundAt;

    @Column(name = "target_date", nullable = false, updatable = false)
    private LocalDate targetDate;

    @Builder
    private NotificationRankRound(Instant roundAt, LocalDate targetDate) {
        if (roundAt == null || targetDate == null) {
            throw new IllegalArgumentException("회차 시각과 날짜는 필수입니다.");
        }
        this.roundAt = roundAt;
        this.targetDate = targetDate;
    }
}
