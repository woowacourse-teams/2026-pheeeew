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
@Table(name = "sigh_blocks")
@Entity
public class EmotionBlock extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_device_id", nullable = false, updatable = false)
    private Long blockerDeviceId;

    @Column(name = "sigh_id", nullable = false, updatable = false)
    private Long emotionId;

    @Builder
    private EmotionBlock(Long blockerDeviceId, Long emotionId) {
        this.blockerDeviceId = Objects.requireNonNull(blockerDeviceId);
        this.emotionId = Objects.requireNonNull(emotionId);
    }
}
