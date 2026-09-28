package com.pheeeew.emotion.infra;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_PLAYBACK_UNAVAILABLE;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_UNAVAILABLE;
import static com.pheeeew.emotion.fixture.AudioUploadFixture.기본_업로드_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pheeeew.common.infra.s3.S3Properties;
import com.pheeeew.emotion.application.AudioUrlIssuer.PlaybackUrl;
import com.pheeeew.emotion.application.AudioUrlIssuer.UploadUrl;
import com.pheeeew.emotion.domain.AudioUpload;
import com.pheeeew.emotion.exception.EmotionException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class S3AudioUrlIssuerTest {

    @ParameterizedTest
    @CsvSource({"development,300", "production,120"})
    void 환경별_객체_키와_파일_조건과_덮어쓰기_방지를_서명한다(String environment, long validitySeconds) {
        // given
        String prefix = "pheeeew/" + environment + "/";
        S3Properties properties = new S3Properties("pheeeew-test", prefix, "ap-northeast-2");
        AudioUpload upload = 기본_업로드_빌더().objectKey(prefix + "audio/음성 기록.m4a").build();
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .build()) {
            S3AudioUrlIssuer issuer = new S3AudioUrlIssuer(presigner, properties);

            // when
            UploadUrl result = issuer.issueUpload(upload, Duration.ofSeconds(validitySeconds));

            // then
            URI url = URI.create(result.uploadUrl());
            Map<String, String> query = queryParameters(url);
            assertThat(url.getScheme()).isEqualTo("https");
            assertThat(url.getHost()).isEqualTo("pheeeew-test.s3.ap-northeast-2.amazonaws.com");
            assertThat(url.getPath()).isEqualTo("/" + upload.getObjectKey());
            assertThat(query.get("X-Amz-Expires")).isEqualTo(Long.toString(validitySeconds));
            assertThat(query.get("X-Amz-SignedHeaders").split(";"))
                    .contains("host", "content-type", "content-length", "if-none-match");
            assertThat(result.headers()).containsEntry("content-type", List.of(upload.getContentType()))
                    .containsEntry("content-length", List.of(Long.toString(upload.getContentLength())))
                    .containsEntry("if-none-match", List.of("*"))
                    .doesNotContainKey("host");
            Instant signedAt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                    .withZone(ZoneOffset.UTC).parse(query.get("X-Amz-Date"), Instant::from);
            assertThat(result.expiresAt()).isBetween(signedAt.plusSeconds(validitySeconds),
                    signedAt.plusSeconds(validitySeconds + 1));
            assertThat(upload.getClaimedRequestId()).isNull();
        }
    }

    @Test
    void 재생_URL은_같은_객체_키로_5분간_유효하며_업로드_헤더를_요구하지_않는다() {
        // given
        S3Properties properties = new S3Properties("pheeeew-test", "pheeeew/development/", "ap-northeast-2");
        String objectKey = 기본_업로드_빌더().build().getObjectKey();
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .build()) {
            S3AudioUrlIssuer issuer = new S3AudioUrlIssuer(presigner, properties);

            // when
            PlaybackUrl result = issuer.issuePlayback(objectKey);

            // then
            URI url = URI.create(result.playbackUrl());
            Map<String, String> query = queryParameters(url);
            assertThat(url.getScheme()).isEqualTo("https");
            assertThat(url.getHost()).isEqualTo("pheeeew-test.s3.ap-northeast-2.amazonaws.com");
            assertThat(url.getPath()).isEqualTo("/" + objectKey);
            assertThat(query.get("X-Amz-Expires")).isEqualTo("300");
            assertThat(query.get("X-Amz-SignedHeaders")).isEqualTo("host");
            Instant signedAt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                    .withZone(ZoneOffset.UTC).parse(query.get("X-Amz-Date"), Instant::from);
            assertThat(result.expiresAt()).isBetween(signedAt.plusSeconds(300), signedAt.plusSeconds(301));
        }
    }

    @Test
    void 재생_서명_실패는_원인을_보존한_재생_오류로_변환한다() {
        // given
        S3Presigner presigner = mock(S3Presigner.class);
        SdkClientException failure = SdkClientException.create("test credential failure");
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenThrow(failure);
        S3AudioUrlIssuer issuer = new S3AudioUrlIssuer(presigner,
                new S3Properties("pheeeew-test", "pheeeew/development/", "ap-northeast-2"));

        // when / then
        assertThatThrownBy(() -> issuer.issuePlayback("audio/recording.m4a"))
                .isInstanceOfSatisfying(EmotionException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(EMOTION_AUDIO_PLAYBACK_UNAVAILABLE))
                .hasCause(failure);
    }

    @Test
    void 자격_증명_등의_서명_실패는_원인을_보존한_저장소_오류로_변환한다() {
        // given
        S3Presigner presigner = mock(S3Presigner.class);
        SdkClientException failure = SdkClientException.create("test credential failure");
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenThrow(failure);
        S3AudioUrlIssuer issuer = new S3AudioUrlIssuer(presigner,
                new S3Properties("pheeeew-test", "pheeeew/development/", "ap-northeast-2"));

        // when / then
        assertThatThrownBy(() -> issuer.issueUpload(기본_업로드_빌더().build(), Duration.ofMinutes(5)))
                .isInstanceOfSatisfying(EmotionException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_UNAVAILABLE))
                .hasCause(failure);
    }

    private Map<String, String> queryParameters(URI url) {
        return Arrays.stream(url.getRawQuery().split("&"))
                .map(parameter -> parameter.split("=", 2))
                .collect(Collectors.toMap(parameter -> parameter[0],
                        parameter -> URLDecoder.decode(parameter[1], StandardCharsets.UTF_8)));
    }
}
