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
@Table(name = "sigh_reports")
@Entity
public class SighReport extends BaseEntity {

    private static final int MAX_REASON_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sigh_id", nullable = false, updatable = false)
    private Long sighId;

    @Column(name = "reporter_device_id", nullable = false, updatable = false)
    private Long reporterDeviceId;

    @Column(nullable = false, updatable = false, length = MAX_REASON_LENGTH)
    private String reason;

    @Builder
    private SighReport(Long sighId, Long reporterDeviceId, String reason) {
        this.sighId = Objects.requireNonNull(sighId);
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
