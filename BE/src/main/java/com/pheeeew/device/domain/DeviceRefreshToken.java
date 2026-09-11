package com.pheeeew.device.domain;

import com.pheeeew.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "device_refresh_tokens")
@Entity
public class DeviceRefreshToken extends BaseEntity {

    private static final int TOKEN_HASH_LENGTH = 64;
    private static final Pattern TOKEN_HASH_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, updatable = false)
    private Long deviceId;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = TOKEN_HASH_LENGTH)
    private String tokenHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Builder
    private DeviceRefreshToken(Long deviceId, UUID sessionId, String tokenHash) {
        this.deviceId = Objects.requireNonNull(deviceId);
        this.sessionId = Objects.requireNonNull(sessionId);
        this.tokenHash = requireValidTokenHash(tokenHash);
    }

    public void revoke() {
        if (revokedAt != null) {
            return;
        }
        this.revokedAt = Instant.now();
    }

    public boolean isUsable(Instant now) {
        Objects.requireNonNull(now);
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    private String requireValidTokenHash(String tokenHash) {
        if (tokenHash == null || !TOKEN_HASH_PATTERN.matcher(tokenHash).matches()) {
            throw new IllegalArgumentException("refresh token 해시는 64자 16진 소문자여야 합니다.");
        }
        return tokenHash;
    }
}
