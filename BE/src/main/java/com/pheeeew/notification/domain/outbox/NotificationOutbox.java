package com.pheeeew.notification.domain.outbox;

import com.pheeeew.common.domain.BaseEntity;
import com.pheeeew.notification.domain.PushMessage;
import com.pheeeew.notification.domain.PushSendResult;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@MappedSuperclass
public abstract class NotificationOutbox extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, updatable = false)
    private Long deviceId;

    @Column(name = "target_date", nullable = false, updatable = false)
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationOutboxStatus status;

    @Column(name = "discovered_at", nullable = false, updatable = false)
    private Instant discoveredAt;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(name = "claim_expires_at")
    private Instant claimExpiresAt;

    @Column(name = "result_code", length = 40)
    private String resultCode;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected NotificationOutbox(
            Long deviceId,
            LocalDate targetDate,
            Instant discoveredAt,
            Instant availableAt,
            Instant expiresAt
    ) {
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("알림을 받을 기기는 필수입니다.");
        }
        if (targetDate == null || discoveredAt == null || availableAt == null || expiresAt == null) {
            throw new IllegalArgumentException("알림 날짜와 발견, 예정, 만료 시각은 필수입니다.");
        }
        if (availableAt.isBefore(discoveredAt)) {
            throw new IllegalArgumentException("발송 예정 시각은 발견 시각보다 앞설 수 없습니다.");
        }
        if (!expiresAt.isAfter(discoveredAt)) {
            throw new IllegalArgumentException("만료 시각은 발견 시각보다 뒤여야 합니다.");
        }
        this.deviceId = deviceId;
        this.targetDate = targetDate;
        this.status = NotificationOutboxStatus.PENDING;
        this.discoveredAt = discoveredAt;
        this.availableAt = availableAt;
        this.expiresAt = expiresAt;
        this.attemptCount = 0;
    }

    public abstract NotificationOutboxType type();

    public PushMessage message() {
        NotificationOutboxType type = type();
        return PushMessage.of(type.getTitle(), type.getBody(), Map.of(
                "type", type.name(),
                "date", targetDate.toString()
        ));
    }

    public void claim(UUID claimToken, Instant claimExpiresAt, Instant now) {
        if (claimToken == null || claimExpiresAt == null || now == null) {
            throw new IllegalArgumentException("선점 토큰과 선점 기한, 현재 시각은 필수입니다.");
        }
        if (!claimExpiresAt.isAfter(now)) {
            throw new IllegalArgumentException("선점 기한은 현재 시각보다 뒤여야 합니다.");
        }
        if (!isClaimableAt(now)) {
            throw new IllegalStateException("지금 선점할 수 없는 알림 작업입니다.");
        }
        this.claimToken = claimToken;
        this.claimExpiresAt = claimExpiresAt;
        this.attemptCount++;
    }

    public void markSent(UUID claimToken, Instant now) {
        validateOwner(claimToken);
        close(NotificationOutboxStatus.SENT, null, now);
    }

    public void scheduleRetry(UUID claimToken, Instant nextAvailableAt, Instant now) {
        validateOwner(claimToken);
        if (nextAvailableAt == null || now == null) {
            throw new IllegalArgumentException("다음 발송 시각과 현재 시각은 필수입니다.");
        }
        if (!nextAvailableAt.isAfter(now) || !nextAvailableAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("다음 발송 시각은 현재 시각 뒤, 만료 시각 앞이어야 합니다.");
        }
        this.availableAt = nextAvailableAt;
        this.resultCode = PushSendResult.RETRYABLE.name();
        releaseClaim();
    }

    public void cancel(UUID claimToken, String resultCode, Instant now) {
        validateOwner(claimToken);
        close(NotificationOutboxStatus.CANCELED, requireResultCode(resultCode), now);
    }

    public void fail(UUID claimToken, String resultCode, Instant now) {
        validateOwner(claimToken);
        close(NotificationOutboxStatus.FAILED, requireResultCode(resultCode), now);
    }

    public void expire(String resultCode, Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("현재 시각은 필수입니다.");
        }
        if (!isExpiredAt(now)) {
            throw new IllegalStateException("만료 처리할 수 없는 알림 작업입니다.");
        }
        close(NotificationOutboxStatus.CANCELED, requireResultCode(resultCode), now);
    }

    private boolean isClaimableAt(Instant now) {
        return status == NotificationOutboxStatus.PENDING
                && !availableAt.isAfter(now)
                && expiresAt.isAfter(now)
                && isClaimReleasedAt(now);
    }

    private boolean isClaimReleasedAt(Instant now) {
        return claimExpiresAt == null || !claimExpiresAt.isAfter(now);
    }

    private void validateOwner(UUID claimToken) {
        if (status != NotificationOutboxStatus.PENDING || claimToken == null || !claimToken.equals(this.claimToken)) {
            throw new IllegalStateException("선점하지 않은 알림 작업의 결과는 기록할 수 없습니다.");
        }
    }

    private void close(NotificationOutboxStatus status, String resultCode, Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("현재 시각은 필수입니다.");
        }
        this.status = status;
        this.resultCode = resultCode;
        this.finishedAt = now;
        releaseClaim();
    }

    private void releaseClaim() {
        this.claimToken = null;
        this.claimExpiresAt = null;
    }

    private String requireResultCode(String resultCode) {
        if (resultCode == null || resultCode.isBlank()) {
            throw new IllegalArgumentException("결과 코드는 필수입니다.");
        }
        return resultCode;
    }

    private boolean isExpiredAt(Instant now) {
        return status == NotificationOutboxStatus.PENDING
                && !expiresAt.isAfter(now)
                && isClaimReleasedAt(now);
    }
}
