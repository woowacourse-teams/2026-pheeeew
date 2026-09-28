package com.pheeeew.emotion.infra;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_NOT_READY;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_UNAVAILABLE;
import static com.pheeeew.emotion.fixture.AudioUploadFixture.기본_업로드_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.common.infra.s3.S3Properties;
import com.pheeeew.emotion.domain.AudioUpload;
import com.pheeeew.emotion.exception.EmotionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3ObjectVerifierTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final S3ObjectVerifier verifier = new S3ObjectVerifier(s3Client,
            new S3Properties("pheeeew-test", "pheeeew/development/", "ap-northeast-2"));

    @Test
    void 발급한_객체의_파일_조건을_HEAD로만_확인한다() {
        // given
        AudioUpload upload = 기본_업로드_빌더().build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength(upload.getContentLength()).contentType(upload.getContentType()).build());

        // when
        verifier.verify(upload);

        // then
        verify(s3Client).headObject(HeadObjectRequest.builder()
                .bucket("pheeeew-test").key(upload.getObjectKey()).build());
        verifyNoMoreInteractions(s3Client);
        assertThat(upload.getClaimedRequestId()).isNull();
    }

    @ParameterizedTest
    @CsvSource(value = {"0,audio/mp4", "1023,audio/mp4", "1025,audio/mp4", "1024,audio/mpeg",
            "NULL,audio/mp4", "1024,NULL"}, nullValues = "NULL")
    void 크기나_콘텐츠_유형이_발급_정보와_다르면_거부한다(Long contentLength, String contentType) {
        // given
        AudioUpload upload = 기본_업로드_빌더().build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength(contentLength).contentType(contentType).build());

        // when / then
        assertThatThrownBy(() -> verifier.verify(upload)).isInstanceOfSatisfying(EmotionException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_NOT_READY));
        assertThat(upload.getClaimedRequestId()).isNull();
    }

    @Test
    void 객체가_없으면_원인을_보존한_미완료_오류로_변환한다() {
        // given
        S3Exception failure = s3Failure(404, null);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(failure);

        // when / then
        assertThatThrownBy(() -> verifier.verify(기본_업로드_빌더().build()))
                .isInstanceOfSatisfying(EmotionException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_NOT_READY))
                .hasCause(failure);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 429, 500, 503})
    void 권한이나_저장소_오류를_업로드_미완료로_처리하지_않는다(int statusCode) {
        // given
        S3Exception failure = s3Failure(statusCode, null);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(failure);

        // when / then
        assertThatThrownBy(() -> verifier.verify(기본_업로드_빌더().build()))
                .isInstanceOfSatisfying(EmotionException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_UNAVAILABLE))
                .hasCause(failure);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 버킷_없음이_명시되면_404여도_저장소_오류다(boolean typedException) {
        // given
        S3Exception failure = typedException ? NoSuchBucketException.builder().statusCode(404).build()
                : s3Failure(404, "NoSuchBucket");
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(failure);

        // when / then
        assertThatThrownBy(() -> verifier.verify(기본_업로드_빌더().build()))
                .isInstanceOfSatisfying(EmotionException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_UNAVAILABLE))
                .hasCause(failure);
    }

    @Test
    void 네트워크나_자격_증명_실패의_원인을_보존한다() {
        // given
        SdkClientException failure = SdkClientException.create("test network failure");
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(failure);

        // when / then
        assertThatThrownBy(() -> verifier.verify(기본_업로드_빌더().build()))
                .isInstanceOfSatisfying(EmotionException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_UNAVAILABLE))
                .hasCause(failure);
    }

    private S3Exception s3Failure(int statusCode, String errorCode) {
        S3Exception.Builder builder = S3Exception.builder();
        builder.statusCode(statusCode);
        if (errorCode != null) {
            builder.awsErrorDetails(AwsErrorDetails.builder().errorCode(errorCode).build());
        }
        return (S3Exception) builder.build();
    }
}
