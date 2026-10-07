package com.pheeeew.device.domain;

import com.pheeeew.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "devices")
@Entity
public class Device extends BaseEntity {

    private static final int MAX_PLATFORM_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(length = DeviceNickname.MAX_LENGTH)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = MAX_PLATFORM_LENGTH)
    private DevicePlatform platform;

    @Builder
    private Device(UUID requestId, DevicePlatform platform, String nickname) {
        this.publicId = UUID.randomUUID();
        this.requestId = Objects.requireNonNull(requestId);
        this.platform = Objects.requireNonNull(platform);
        // 구버전 등록 경로는 전환 기간 동안 닉네임 없이도 기기를 생성한다.
        this.nickname = nickname == null ? null : DeviceNickname.from(nickname).value();
    }

    public void updateNickname(String nickname) {
        this.nickname = DeviceNickname.from(nickname).value();
    }
}
