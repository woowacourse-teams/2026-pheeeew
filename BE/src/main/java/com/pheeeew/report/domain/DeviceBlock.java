package com.pheeeew.report.domain;

import com.pheeeew.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "device_blocks")
@Entity
public class DeviceBlock extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_device_id", nullable = false, updatable = false)
    private Long blockerDeviceId;

    @Column(name = "blocked_device_id", nullable = false, updatable = false)
    private Long blockedDeviceId;

    @Column(name = "origin_emotion_id", nullable = false, updatable = false)
    private Long originEmotionId;

    @Builder
    private DeviceBlock(Long blockerDeviceId, Long blockedDeviceId, Long originEmotionId) {
        this.blockerDeviceId = Objects.requireNonNull(blockerDeviceId);
        this.blockedDeviceId = requireNotSelf(blockerDeviceId, blockedDeviceId);
        this.originEmotionId = Objects.requireNonNull(originEmotionId);
    }

    private Long requireNotSelf(Long blockerDeviceId, Long blockedDeviceId) {
        Objects.requireNonNull(blockedDeviceId);
        if (blockerDeviceId.equals(blockedDeviceId)) {
            throw new IllegalArgumentException("자기 자신을 차단할 수 없습니다.");
        }
        return blockedDeviceId;
    }
}
