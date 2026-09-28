package com.pheeeew.emotion.application;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;

import com.pheeeew.common.infra.s3.S3Properties;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.dto.AudioUploadResult;
import com.pheeeew.emotion.domain.AudioUpload;
import com.pheeeew.emotion.domain.repository.AudioUploadRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class AudioUploadService {

    private static final Duration UPLOAD_URL_VALIDITY = Duration.ofMinutes(5);
    private static final Duration UNCLAIMED_UPLOAD_VALIDITY = Duration.ofHours(24);

    private final AudioUploadRepository audioUploadRepository;
    private final DeviceRepository deviceRepository;
    private final AudioUrlIssuer audioUrlIssuer;
    private final S3Properties s3Properties;
    private final Clock clock;

    @Transactional
    public AudioUploadResult prepare(UUID devicePublicId, String contentType, long contentLength) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND)).getId();

        String objectKey = s3Properties.keyPrefix() + "audio/" + UUID.randomUUID();
        AudioUpload upload = save(deviceId, objectKey, contentType, contentLength,
                Instant.now(clock).plus(UNCLAIMED_UPLOAD_VALIDITY));

        AudioUrlIssuer.UploadUrl url = audioUrlIssuer.issueUpload(upload, UPLOAD_URL_VALIDITY);
        return AudioUploadResult.of(upload, url);
    }

    @Transactional
    public AudioUpload save(Long deviceId, String objectKey, String contentType, long contentLength, Instant expiresAt) {
        AudioUpload upload = AudioUpload.builder()
                .deviceId(deviceId)
                .objectKey(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .expiresAt(expiresAt)
                .build();
        return audioUploadRepository.save(upload);
    }
}
