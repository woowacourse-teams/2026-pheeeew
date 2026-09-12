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
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "device_challenges")
@Entity
public class DeviceChallenge extends BaseEntity {

    private static final int CHALLENGE_LENGTH = 43;
    private static final Pattern CHALLENGE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = CHALLENGE_LENGTH)
    private String challenge;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Builder
    private DeviceChallenge(String challenge, Instant expiresAt) {
        this.challenge = requireValidChallenge(challenge);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    private String requireValidChallenge(String challenge) {
        if (challenge == null || !CHALLENGE_PATTERN.matcher(challenge).matches()) {
            throw new IllegalArgumentException("challenge 는 43자 Base64url 문자열이어야 합니다.");
        }
        return challenge;
    }
}
