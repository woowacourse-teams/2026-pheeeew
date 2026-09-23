package com.pheeeew.emotion.domain;

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
@Table(name = "emotion_likes")
@Entity
public class EmotionLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "emotion_id", nullable = false, updatable = false)
    private Long emotionId;

    @Column(name = "device_id", nullable = false, updatable = false)
    private Long deviceId;

    @Builder
    private EmotionLike(Long emotionId, Long deviceId) {
        this.emotionId = Objects.requireNonNull(emotionId);
        this.deviceId = Objects.requireNonNull(deviceId);
    }
}
