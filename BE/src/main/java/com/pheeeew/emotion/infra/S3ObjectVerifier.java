package com.pheeeew.emotion.infra;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_NOT_READY;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_UNAVAILABLE;

import com.pheeeew.common.infra.s3.S3Properties;
import com.pheeeew.emotion.domain.AudioUpload;
import com.pheeeew.emotion.exception.EmotionException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@RequiredArgsConstructor
@Component
public class S3ObjectVerifier {

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    // 소유권·연결 가능 여부 확인 뒤 호출한다. 메타데이터만 검사하며 객체나 연결 상태를 변경하지 않는다.
    public void verify(AudioUpload upload) {
        try {
            HeadObjectResponse object = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(upload.getObjectKey())
                    .build());
            validateMetadata(object, upload);
        } catch (SdkException exception) {
            if (exception instanceof S3Exception s3Exception && isMissingObject(s3Exception)) {
                throw new EmotionException(EMOTION_AUDIO_UPLOAD_NOT_READY, exception);
            }

            throw new EmotionException(EMOTION_AUDIO_UPLOAD_UNAVAILABLE, exception);
        }
    }

    private void validateMetadata(HeadObjectResponse object, AudioUpload upload) {
        if (!Objects.equals(object.contentLength(), upload.getContentLength())
                || !upload.getContentType().equals(object.contentType())) {
            throw new EmotionException(EMOTION_AUDIO_UPLOAD_NOT_READY);
        }
    }

    private boolean isMissingObject(S3Exception exception) {
        return exception.statusCode() == 404
                && !(exception instanceof NoSuchBucketException)
                && (exception.awsErrorDetails() == null
                || !"NoSuchBucket".equals(exception.awsErrorDetails().errorCode()));
    }
}
