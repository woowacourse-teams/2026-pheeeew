package com.pheeeew.notification.domain.outbox;

import com.pheeeew.notification.domain.PushMessage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "rank_dropped_outbox")
@Entity
public class RankDroppedOutbox extends NotificationOutbox {

    @Column(name = "round_at", nullable = false, updatable = false)
    private Instant roundAt;

    @Column(name = "previous_rank", nullable = false, updatable = false)
    private int previousRank;

    @Column(name = "current_rank", nullable = false, updatable = false)
    private int currentRank;

    @Builder
    private RankDroppedOutbox(
            Long deviceId,
            LocalDate targetDate,
            Instant roundAt,
            int previousRank,
            int currentRank,
            Instant discoveredAt,
            Instant availableAt,
            Instant expiresAt
    ) {
        super(deviceId, targetDate, discoveredAt, availableAt, expiresAt);
        if (roundAt == null) {
            throw new IllegalArgumentException("순위 비교 회차는 필수입니다.");
        }
        if (previousRank < 1 || currentRank <= previousRank) {
            throw new IllegalArgumentException("순위 하락 알림은 이전 순위보다 낮아진 순위만 담습니다.");
        }
        this.roundAt = roundAt;
        this.previousRank = previousRank;
        this.currentRank = currentRank;
    }

    @Override
    public NotificationOutboxType type() {
        return NotificationOutboxType.RANK_DROPPED;
    }

    @Override
    public PushMessage message() {
        NotificationOutboxType type = type();
        return PushMessage.of(type.getTitle(), type.getBody().formatted(previousRank, currentRank), Map.of(
                "type", type.name(),
                "date", getTargetDate().toString(),
                "previousRank", String.valueOf(previousRank),
                "currentRank", String.valueOf(currentRank)
        ));
    }
}
