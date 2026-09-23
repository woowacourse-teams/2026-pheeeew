package com.pheeeew.report.application;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.report.exception.EmotionReportErrorCode.EMOTION_REPORT_SAVE_FAILED;
import static com.pheeeew.report.exception.EmotionReportErrorCode.EMOTION_REPORT_SELF_NOT_ALLOWED;
import static com.pheeeew.sigh.exception.EmotionErrorCode.EMOTION_NOT_FOUND;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.report.application.dto.EmotionReportResult;
import com.pheeeew.report.domain.EmotionReport;
import com.pheeeew.report.domain.repository.EmotionReportRepository;
import com.pheeeew.report.exception.EmotionReportException;
import com.pheeeew.sigh.domain.Emotion;
import com.pheeeew.sigh.domain.repository.EmotionRepository;
import com.pheeeew.sigh.exception.EmotionException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class EmotionReportService {

    private static final long AUTO_DELETE_REPORT_THRESHOLD = 5L;

    private final EmotionReportRepository emotionReportRepository;
    private final EmotionRepository emotionRepository;
    private final DeviceRepository deviceRepository;
    private final EmotionReportMetrics emotionReportMetrics;

    @Transactional
    public int deleteReportedOverThreshold() {
        int deletedCount = emotionReportRepository.deleteReportedOverThreshold(
                AUTO_DELETE_REPORT_THRESHOLD,
                Instant.now()
        );
        emotionReportMetrics.recordAutoDeleted(deletedCount);

        return deletedCount;
    }

    public EmotionReportResult save(Long emotionId, UUID devicePublicId, String reason) {
        Emotion emotion = findEmotion(emotionId);
        Long reporterDeviceId = findReporterDeviceId(devicePublicId);
        validateNotSelf(emotion, reporterDeviceId);

        Optional<EmotionReport> existingReport = emotionReportRepository.findByEmotionIdAndReporterDeviceId(emotionId, reporterDeviceId);

        if (existingReport.isPresent()) {
            return EmotionReportResult.of(existingReport.get(), false);
        }

        return saveNewReport(emotionId, reporterDeviceId, reason);
    }

    private Emotion findEmotion(Long emotionId) {
        return emotionRepository.findById(emotionId)
                .orElseThrow(() -> new EmotionException(EMOTION_NOT_FOUND));
    }

    private void validateNotSelf(Emotion emotion, Long reporterDeviceId) {
        if (reporterDeviceId.equals(emotion.getDeviceId())) {
            throw new EmotionReportException(EMOTION_REPORT_SELF_NOT_ALLOWED);
        }
    }

    private Long findReporterDeviceId(UUID devicePublicId) {
        return deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
    }

    private EmotionReportResult saveNewReport(Long emotionId, Long reporterDeviceId, String reason) {
        EmotionReport report = EmotionReport.builder()
                .emotionId(emotionId)
                .reporterDeviceId(reporterDeviceId)
                .reason(reason)
                .build();

        try {
            return EmotionReportResult.of(emotionReportRepository.saveAndFlush(report), true);
        } catch (DataIntegrityViolationException cause) {
            return findExistingReport(emotionId, reporterDeviceId, cause);
        }
    }

    private EmotionReportResult findExistingReport(
            Long emotionId,
            Long reporterDeviceId,
            DataIntegrityViolationException cause
    ) {
        return emotionReportRepository.findByEmotionIdAndReporterDeviceId(emotionId, reporterDeviceId)
                .map(report -> EmotionReportResult.of(report, false))
                .orElseThrow(() -> new EmotionReportException(EMOTION_REPORT_SAVE_FAILED, cause));
    }
}
