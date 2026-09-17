package com.pheeeew.report.application;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.report.exception.SighReportErrorCode.SIGH_REPORT_SAVE_FAILED;
import static com.pheeeew.report.exception.SighReportErrorCode.SIGH_REPORT_SELF_NOT_ALLOWED;
import static com.pheeeew.sigh.exception.SighErrorCode.SIGH_NOT_FOUND;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.report.application.dto.SighReportResult;
import com.pheeeew.report.domain.SighReport;
import com.pheeeew.report.domain.repository.SighReportRepository;
import com.pheeeew.report.exception.SighReportException;
import com.pheeeew.sigh.domain.Sigh;
import com.pheeeew.sigh.domain.repository.SighRepository;
import com.pheeeew.sigh.exception.SighException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class SighReportService {

    private static final long AUTO_DELETE_REPORT_THRESHOLD = 5L;

    private final SighReportRepository sighReportRepository;
    private final SighRepository sighRepository;
    private final DeviceRepository deviceRepository;
    private final SighReportMetrics sighReportMetrics;

    @Transactional
    public int deleteReportedOverThreshold() {
        int deletedCount = sighReportRepository.deleteReportedOverThreshold(
                AUTO_DELETE_REPORT_THRESHOLD,
                Instant.now()
        );
        sighReportMetrics.recordAutoDeleted(deletedCount);

        return deletedCount;
    }

    public SighReportResult save(Long sighId, UUID devicePublicId, String reason) {
        Sigh sigh = findSigh(sighId);
        Long reporterDeviceId = findReporterDeviceId(devicePublicId);
        validateNotSelf(sigh, reporterDeviceId);

        Optional<SighReport> existingReport = sighReportRepository.findBySighIdAndReporterDeviceId(sighId, reporterDeviceId);

        if (existingReport.isPresent()) {
            return SighReportResult.of(existingReport.get(), false);
        }

        return saveNewReport(sighId, reporterDeviceId, reason);
    }

    private Sigh findSigh(Long sighId) {
        return sighRepository.findById(sighId)
                .orElseThrow(() -> new SighException(SIGH_NOT_FOUND));
    }

    private void validateNotSelf(Sigh sigh, Long reporterDeviceId) {
        if (reporterDeviceId.equals(sigh.getDeviceId())) {
            throw new SighReportException(SIGH_REPORT_SELF_NOT_ALLOWED);
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
