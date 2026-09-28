package com.pheeeew.emotion.application.command;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_NOT_FOUND;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_UNAVAILABLE;

import com.pheeeew.emotion.domain.Audio;
import com.pheeeew.emotion.domain.AudioUpload;
import com.pheeeew.emotion.domain.EmotionContent;
import com.pheeeew.emotion.domain.repository.AudioUploadRepository;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.emotion.infra.S3ObjectVerifier;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class EmotionContentResolver {

    private final AudioUploadRepository audioUploadRepository;
    private final ObjectProvider<S3ObjectVerifier> objectVerifier;

    @Transactional(propagation = Propagation.MANDATORY)
    public EmotionContent resolve(String memo, String audioUploadId, Long deviceId, UUID requestId) {
        EmotionContent content = EmotionContent.builder().memo(memo).build();
        if (audioUploadId == null) {
            return content;
        }
        if (audioUploadId.isBlank()) {
            throw new IllegalArgumentException("녹음 업로드 식별자는 비어 있을 수 없습니다.");
        }
        if (content.getMemo() != null) {
            throw new IllegalArgumentException("메모와 녹음은 함께 등록할 수 없습니다.");
        }

        S3ObjectVerifier verifier = objectVerifier.getIfAvailable();
        if (verifier == null) {
            throw new EmotionException(EMOTION_AUDIO_UPLOAD_UNAVAILABLE);
        }

        // 잠금은 감정 저장 트랜잭션이 끝날 때까지 유지하여 한 업로드의 중복 연결을 막는다.
        AudioUpload upload = audioUploadRepository.findByUploadIdForUpdate(audioUploadId)
                .orElseThrow(() -> new EmotionException(EMOTION_AUDIO_UPLOAD_NOT_FOUND));
        upload.validateClaim(deviceId, requestId, Instant.now());
        if (upload.getClaimedRequestId() == null) {
            verifier.verify(upload);
            upload.claim(deviceId, requestId, Instant.now());
        }

        return EmotionContent.builder()
                .audio(Audio.builder()
                        .objectKey(upload.getObjectKey())
                        .build())
                .build();
    }
}
