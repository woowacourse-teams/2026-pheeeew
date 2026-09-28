package com.pheeeew.emotion.infra;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_PLAYBACK_UNAVAILABLE;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_UNAVAILABLE;

import com.pheeeew.common.infra.s3.S3Properties;
import com.pheeeew.emotion.application.AudioUrlIssuer;
import com.pheeeew.emotion.domain.AudioUpload;
import com.pheeeew.emotion.exception.EmotionException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@RequiredArgsConstructor
@Component
public class S3AudioUrlIssuer implements AudioUrlIssuer {

    private static final Duration PLAYBACK_URL_VALIDITY = Duration.ofMinutes(5);

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    @Override
    public UploadUrl issueUpload(AudioUpload upload, Duration validity) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(upload.getObjectKey())
                .contentType(upload.getContentType())
                .contentLength(upload.getContentLength())
                .ifNoneMatch("*")
                .build();

        try {
            PresignedPutObjectRequest signed = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(validity)
                    .putObjectRequest(request)
                    .build());
            // Host는 URL을 기준으로 HTTP 클라이언트가 설정한다.
            Map<String, List<String>> headers = signed.signedHeaders().entrySet().stream()
                    .filter(entry -> !entry.getKey().equalsIgnoreCase("host"))
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));

            return UploadUrl.of(signed.url().toExternalForm(), signed.expiration(), headers);
        } catch (SdkException exception) {
            throw new EmotionException(EMOTION_AUDIO_UPLOAD_UNAVAILABLE, exception);
        }
    }

    @Override
    public PlaybackUrl issuePlayback(String objectKey) {
        try {
            PresignedGetObjectRequest signed = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(PLAYBACK_URL_VALIDITY)
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(s3Properties.bucket())
                            .key(objectKey)
                            .build())
                    .build());

            return PlaybackUrl.of(signed.url().toExternalForm(), signed.expiration());
        } catch (SdkException exception) {
            throw new EmotionException(EMOTION_AUDIO_PLAYBACK_UNAVAILABLE, exception);
        }
    }
}
