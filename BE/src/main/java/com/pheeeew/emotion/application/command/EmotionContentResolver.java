package com.pheeeew.emotion.application.command;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_NOT_FOUND;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_NOT_READY;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_UNAVAILABLE;

import com.pheeeew.emotion.domain.Audio;
import com.pheeeew.emotion.domain.AudioUpload;
import com.pheeeew.emotion.domain.EmotionContent;
import com.pheeeew.emotion.domain.repository.AudioUploadRepository;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.emotion.infra.S3ObjectVerifier;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class EmotionContentResolver {

    private final AudioUploadRepository audioUploadRepository;
    private final ObjectProvider<S3ObjectVerifier> objectVerifier;
    private final PlatformTransactionManager transactionManager;
    private final EntityManager entityManager;

    /** S3 확인 결과만 보관하고 업로드 연결 상태는 변경하지 않는다. */
    public PreparedContent prepare(String memo, String audioUploadId, Long deviceId, UUID requestId) {
        try {
            return prepareContent(memo, audioUploadId, deviceId, requestId);
        } catch (RuntimeException failure) {
            // 쓰기 잠금 안의 멱등성·그룹·지역 검사를 먼저 수행한 뒤 기존 위치에서 실패를 전달한다.
            return new PreparedContent(null, null, deviceId, requestId, failure);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public EmotionContent resolve(PreparedContent prepared, Long deviceId, UUID requestId) {
        if (!Objects.equals(prepared.deviceId, deviceId) || !Objects.equals(prepared.requestId, requestId)) {
            throw new EmotionException(EMOTION_AUDIO_UPLOAD_NOT_FOUND);
        }
        if (prepared.failure != null) {
            throw prepared.failure;
        }
        if (prepared.upload == null) {
            return EmotionContent.builder().memo(prepared.memo).build();
        }

        UploadCheck checked = prepared.upload;
        AudioUpload upload = audioUploadRepository.findByUploadIdForUpdate(checked.uploadId())
                .orElseThrow(() -> new EmotionException(EMOTION_AUDIO_UPLOAD_NOT_FOUND));
        // 외부 트랜잭션의 1차 캐시에 사전 조회 값이 남아 있어도 최신 연결 상태를 읽는다.
        entityManager.refresh(upload, LockModeType.PESSIMISTIC_WRITE);
        upload.validateClaim(deviceId, requestId, Instant.now());
        if (!checked.matches(upload) || upload.getClaimedRequestId() == null && !checked.verified()) {
            throw new EmotionException(EMOTION_AUDIO_UPLOAD_NOT_READY);
        }
        if (upload.getClaimedRequestId() == null) {
            upload.claim(deviceId, requestId, Instant.now());
        }

        return EmotionContent.builder()
                .audio(Audio.builder().objectKey(upload.getObjectKey()).build())
                .build();
    }

    private PreparedContent prepareContent(String memo, String audioUploadId, Long deviceId, UUID requestId) {
        EmotionContent content = EmotionContent.builder().memo(memo).build();
        if (audioUploadId == null) {
            return new PreparedContent(content.getMemo(), null, deviceId, requestId, null);
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

        AudioUpload upload = audioUploadRepository.findByUploadId(audioUploadId)
                .orElseThrow(() -> new EmotionException(EMOTION_AUDIO_UPLOAD_NOT_FOUND));
        upload.validateClaim(deviceId, requestId, Instant.now());
        boolean verified = upload.getClaimedRequestId() == null;
        if (verified) {
            // 호출자의 미커밋 업로드는 조회하되 네트워크 호출 동안에는 트랜잭션을 중단한다.
            TransactionTemplate outsideTransaction = new TransactionTemplate(transactionManager);
            outsideTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
            outsideTransaction.executeWithoutResult(status -> verifier.verify(upload));
        }
        return new PreparedContent(null, UploadCheck.of(upload, verified), deviceId, requestId, null);
    }

    /** 외부에서 생성하거나 수정할 수 없는 사전 확인 결과. JPA 엔티티는 보관하지 않는다. */
    public static final class PreparedContent {

        private final String memo;
        private final UploadCheck upload;
        private final Long deviceId;
        private final UUID requestId;
        private final RuntimeException failure;

        private PreparedContent(String memo, UploadCheck upload, Long deviceId, UUID requestId, RuntimeException failure) {
            this.memo = memo;
            this.upload = upload;
            this.deviceId = deviceId;
            this.requestId = requestId;
            this.failure = failure;
        }
    }

    private record UploadCheck(Long id, String uploadId, String objectKey, String contentType,
                               long contentLength, boolean verified) {

        private static UploadCheck of(AudioUpload upload, boolean verified) {
            return new UploadCheck(upload.getId(), upload.getUploadId(), upload.getObjectKey(),
                    upload.getContentType(), upload.getContentLength(), verified);
        }

        private boolean matches(AudioUpload upload) {
            return id.equals(upload.getId()) && objectKey.equals(upload.getObjectKey())
                    && contentType.equals(upload.getContentType()) && contentLength == upload.getContentLength();
        }
    }
}
