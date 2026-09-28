package com.pheeeew.emotion.domain;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_ALREADY_USED;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_NOT_FOUND;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_NOT_READY;

import com.pheeeew.common.domain.BaseEntity;
import com.pheeeew.emotion.exception.EmotionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "audio_uploads")
@Entity
public class AudioUpload extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "upload_id", nullable = false, updatable = false, length = 36)
    private String uploadId;

    @Column(name = "device_id", nullable = false, updatable = false)
    private Long deviceId;

    @Column(name = "object_key", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String objectKey;

    @Column(name = "content_type", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String contentType;

    @Column(name = "content_length", nullable = false, updatable = false)
    private long contentLength;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "claimed_request_id")
    private UUID claimedRequestId;

    @Builder
    private AudioUpload(Long deviceId, String objectKey, String contentType, long contentLength, Instant expiresAt) {
        this.uploadId = UUID.randomUUID().toString();
        this.deviceId = Objects.requireNonNull(deviceId);
        this.objectKey = requireText(objectKey);
        this.contentType = requireText(contentType);
        if (contentLength <= 0) {
            throw new IllegalArgumentException("녹음 파일 크기는 0보다 커야 합니다.");
        }
        this.contentLength = contentLength;
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public void validateClaim(Long deviceId, UUID requestId, Instant now) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(now);
        if (!this.deviceId.equals(deviceId)) {
            throw new EmotionException(EMOTION_AUDIO_UPLOAD_NOT_FOUND);
        }
        if (claimedRequestId != null) {
            if (!claimedRequestId.equals(requestId)) {
                throw new EmotionException(EMOTION_AUDIO_UPLOAD_ALREADY_USED);
            }
            return;
        }
        if (!now.isBefore(expiresAt)) {
            throw new EmotionException(EMOTION_AUDIO_UPLOAD_NOT_READY);
        }
    }

    // 실제 객체 검증 후 호출하며, 동시 연결과 롤백은 호출자의 DB 트랜잭션에서 보장한다.
    public String claim(Long deviceId, UUID requestId, Instant now) {
        validateClaim(deviceId, requestId, now);
        claimedRequestId = requestId;
        return objectKey;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("녹음 객체 키와 콘텐츠 유형은 비어 있을 수 없습니다.");
        }
        return value;
    }
}
