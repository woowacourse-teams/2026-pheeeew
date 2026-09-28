package com.pheeeew.emotion.application;

import com.pheeeew.emotion.domain.AudioUpload;
import com.pheeeew.emotion.domain.repository.AudioUploadRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class AudioUploadService {

    private final AudioUploadRepository audioUploadRepository;

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
