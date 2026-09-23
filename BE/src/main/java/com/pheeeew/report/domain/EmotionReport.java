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
@Table(name = "emotion_reports")
@Entity
public class EmotionReport extends BaseEntity {

    private static final int MAX_REASON_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "emotion_id", nullable = false, updatable = false)
    private Long emotionId;

    @Column(name = "reporter_device_id", nullable = false, updatable = false)
    private Long reporterDeviceId;

    @Column(nullable = false, updatable = false, length = MAX_REASON_LENGTH)
    private String reason;

    @Builder
    private EmotionReport(Long emotionId, Long reporterDeviceId, String reason) {
        this.emotionId = Objects.requireNonNull(emotionId);
        this.reporterDeviceId = Objects.requireNonNull(reporterDeviceId);
        this.reason = requireValidReason(reason);
    }

    private String requireValidReason(String reason) {
        Objects.requireNonNull(reason);
        String stripped = reason.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "신고 사유는 공백이 아닌 %d자 이하의 값이어야 합니다.".formatted(MAX_REASON_LENGTH)
            );
        }
        return stripped;
    }
}
