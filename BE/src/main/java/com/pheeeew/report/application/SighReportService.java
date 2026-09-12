package com.pheeeew.report.application;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.report.exception.SighReportErrorCode.SIGH_REPORT_SAVE_FAILED;
import static com.pheeeew.sigh.exception.SighErrorCode.SIGH_NOT_FOUND;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.report.domain.SighReport;
import com.pheeeew.report.domain.repository.SighReportRepository;
import com.pheeeew.report.exception.SighReportException;
import com.pheeeew.sigh.domain.repository.SighRepository;
import com.pheeeew.sigh.exception.SighException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 트랜잭션 애노테이션을 두지 않는다. 근거는 ADR-0004에 있다.
 *
 * <p>같은 기기의 중복 신고는 유니크 제약 위반을 잡아 기존 신고를 재조회하는 방식으로 처리한다.
 * 이 흐름을 하나의 트랜잭션으로 묶으면 flush 실패로 영속성 컨텍스트가 오염되어 뒤이은 재조회가
 * 불가능해진다. {@code SighService}가 같은 이유로 트랜잭션을 두지 않는다.
 */
@RequiredArgsConstructor
@Service
public class SighReportService {

    private final SighReportRepository sighReportRepository;
    private final SighRepository sighRepository;
    private final DeviceRepository deviceRepository;

    public SighReportResult save(Long sighId, UUID devicePublicId, String reason) {
        validateSighExists(sighId);
        Long reporterDeviceId = findReporterDeviceId(devicePublicId);

        Optional<SighReport> existingReport = sighReportRepository.findBySighIdAndReporterDeviceId(sighId, reporterDeviceId);

        if (existingReport.isPresent()) {
            return SighReportResult.of(existingReport.get(), false);
        }

        return saveNewReport(sighId, reporterDeviceId, reason);
    }

    private void validateSighExists(Long sighId) {
        if (!sighRepository.existsById(sighId)) {
            throw new SighException(SIGH_NOT_FOUND);
        }
    }

    private Long findReporterDeviceId(UUID devicePublicId) {
        return deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
    }

    private SighReportResult saveNewReport(Long sighId, Long reporterDeviceId, String reason) {
        SighReport report = SighReport.builder()
                .sighId(sighId)
                .reporterDeviceId(reporterDeviceId)
                .reason(reason)
                .build();

        try {
            return SighReportResult.of(sighReportRepository.saveAndFlush(report), true);
        } catch (DataIntegrityViolationException cause) {
            return findExistingReport(sighId, reporterDeviceId, cause);
        }
    }

    private SighReportResult findExistingReport(
            Long sighId,
            Long reporterDeviceId,
            DataIntegrityViolationException cause
    ) {
        return sighReportRepository.findBySighIdAndReporterDeviceId(sighId, reporterDeviceId)
                .map(report -> SighReportResult.of(report, false))
                .orElseThrow(() -> new SighReportException(SIGH_REPORT_SAVE_FAILED, cause));
    }
}
