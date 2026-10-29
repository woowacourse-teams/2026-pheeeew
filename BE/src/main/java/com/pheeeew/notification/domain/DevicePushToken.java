package com.pheeeew.notification.domain;

import com.pheeeew.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "device_push_tokens")
@Entity
public class DevicePushToken extends BaseEntity {

    private static final int MAX_TOKEN_LENGTH = 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, updatable = false)
    private Long deviceId;

    @Column(nullable = false, length = MAX_TOKEN_LENGTH)
    private String token;

    @Builder
    private DevicePushToken(Long deviceId, String token) {
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("등록 기기는 필수입니다.");
        }
        this.deviceId = deviceId;
        this.token = validateToken(token);
    }

    public void changeToken(String token) {
        this.token = validateToken(token);
    }

    public PushTarget toTarget() {
        return PushTarget.of(deviceId, token);
    }

    private String validateToken(String token) {
        if (token == null || token.isEmpty() || token.length() > MAX_TOKEN_LENGTH) {
            throw new IllegalArgumentException("등록 값은 1자 이상 1024자 이하입니다.");
        }
        return token;
    }
}
