package com.pheeeew.emotion.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_UNAVAILABLE;
import static com.pheeeew.emotion.fixture.AudioUploadFixture.기본_업로드_빌더;
import static com.pheeeew.emotion.fixture.AudioUploadFixture.만료_시각;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.common.infra.s3.S3Properties;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.dto.AudioUploadResult;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.emotion.domain.AudioUpload;
import com.pheeeew.emotion.domain.repository.AudioUploadRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@PostgisDataJpaTest
@Import({AudioUploadService.class, AudioUploadServiceIntegrationTest.UploadTestConfiguration.class})
class AudioUploadServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-28T04:00:00Z");

    @MockitoBean
    private AudioUrlIssuer audioUrlIssuer;

    @Autowired
    private AudioUploadService audioUploadService;

    @Autowired
    private AudioUploadRepository audioUploadRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void 발급_이력과_감사_시각을_저장하고_업로드_식별자로_조회한다() {
        // given
        Long deviceId = deviceRepository.save(기본_기기_빌더().build()).getId();
        AudioUpload input = 기본_업로드_빌더().deviceId(deviceId).build();

        // when
        AudioUpload saved = save(input);
        entityManager.flush();
        entityManager.clear();
        AudioUpload found = audioUploadRepository.findByUploadId(saved.getUploadId()).orElseThrow();

        // then
        assertThat(found.getId()).isNotNull();
        assertThat(UUID.fromString(found.getUploadId()).toString()).isEqualTo(saved.getUploadId());
        assertThat(found.getDeviceId()).isEqualTo(deviceId);
        assertThat(found.getObjectKey()).isEqualTo(input.getObjectKey());
        assertThat(found.getContentType()).isEqualTo(input.getContentType());
        assertThat(found.getContentLength()).isEqualTo(input.getContentLength());
        assertThat(found.getExpiresAt()).isEqualTo(input.getExpiresAt());
        assertThat(found.getClaimedRequestId()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void 같은_감정에_교체한_두_업로드의_연결_이력을_모두_보존한다() {
        // given
        Long deviceId = deviceRepository.save(기본_기기_빌더().build()).getId();
        AudioUpload first = save(기본_업로드_빌더().deviceId(deviceId).build());
        AudioUpload second = save(기본_업로드_빌더().deviceId(deviceId).objectKey("audio/replacement.m4a").build());
        UUID requestId = UUID.randomUUID();

        // when
        first.claim(deviceId, requestId, 만료_시각.minusSeconds(1));
        second.claim(deviceId, requestId, 만료_시각.minusSeconds(1));
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(first.getUploadId()).isNotEqualTo(second.getUploadId());
        assertThat(audioUploadRepository.findByUploadId(first.getUploadId()).orElseThrow().getClaimedRequestId())
                .isEqualTo(requestId);
        assertThat(audioUploadRepository.findByUploadId(second.getUploadId()).orElseThrow().getClaimedRequestId())
                .isEqualTo(requestId);
    }

    @Test
    void 동일_객체_키를_별도_업로드로_중복_발급할_수_없다() {
        // given
        Long deviceId = deviceRepository.save(기본_기기_빌더().build()).getId();
        AudioUpload input = 기본_업로드_빌더().deviceId(deviceId).build();
        save(input);

        // when / then
        assertThatThrownBy(() -> save(input)).isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uk_audio_uploads_object_key");
    }

    @Test
    void 존재하지_않는_기기의_업로드를_저장할_수_없다() {
        // given
        AudioUpload input = 기본_업로드_빌더().deviceId(-1L).build();

        // when / then
        assertThatThrownBy(() -> save(input)).isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("fk_audio_uploads_device");
    }

    @Test
    void 업로드_식별자는_DB에서도_중복을_거부한다() {
        // given
        Long deviceId = deviceRepository.save(기본_기기_빌더().build()).getId();
        AudioUpload first = save(기본_업로드_빌더().deviceId(deviceId).build());
        AudioUpload second = save(기본_업로드_빌더().deviceId(deviceId).objectKey("audio/another.m4a").build());
        entityManager.flush();

        // when / then
        assertThatThrownBy(() -> jdbcClient.sql("UPDATE audio_uploads SET upload_id = :uploadId WHERE id = :id")
                .param("uploadId", first.getUploadId()).param("id", second.getId()).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uk_audio_uploads_upload_id");
    }

    @Test
    void 서버가_키와_기한을_정하고_저장된_이력으로_PUT_URL을_발급한다() {
        // given
        Device device = deviceRepository.save(기본_기기_빌더().build());
        Map<String, List<String>> headers = Map.of("content-type", List.of("audio/mp4"),
                "content-length", List.of("1024"), "if-none-match", List.of("*"));
        // 응답은 자체 계산한 시각이 아니라 서명기가 반환한 실제 만료 시각을 사용한다.
        Instant signedExpiresAt = NOW.plusSeconds(299);
        when(audioUrlIssuer.issueUpload(any(), eq(Duration.ofMinutes(5)))).thenAnswer(invocation -> {
            AudioUpload upload = invocation.getArgument(0);
            assertThat(upload.getId()).isNotNull();
            assertThat(upload.getDeviceId()).isEqualTo(device.getId());
            assertThat(upload.getContentType()).isEqualTo("audio/mp4");
            assertThat(upload.getContentLength()).isEqualTo(1024);
            return AudioUrlIssuer.UploadUrl.of("https://example.com/signed-put", signedExpiresAt, headers);
        });

        // when
        AudioUploadResult result = audioUploadService.prepare(device.getPublicId(), "audio/mp4", 1024);
        entityManager.flush();
        entityManager.clear();
        AudioUpload saved = audioUploadRepository.findByUploadId(result.uploadId()).orElseThrow();

        // then
        assertThat(saved.getObjectKey()).startsWith("pheeeew/development/audio/");
        assertThat(UUID.fromString(saved.getObjectKey().substring("pheeeew/development/audio/".length())))
                .isNotNull();
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(saved.getClaimedRequestId()).isNull();
        assertThat(result.uploadUrl()).isEqualTo("https://example.com/signed-put");
        assertThat(result.expiresAt()).isEqualTo(signedExpiresAt);
        assertThat(result.headers()).isEqualTo(headers);
    }

    @Test
    void 같은_기기의_업로드_준비도_매번_다른_식별자와_키를_발급한다() {
        // given
        Device device = deviceRepository.save(기본_기기_빌더().build());
        when(audioUrlIssuer.issueUpload(any(), any())).thenReturn(AudioUrlIssuer.UploadUrl.of(
                "https://example.com/signed-put", NOW.plusSeconds(300), Map.of()));

        // when
        AudioUploadResult first = audioUploadService.prepare(device.getPublicId(), "audio/mp4", 1024);
        AudioUploadResult second = audioUploadService.prepare(device.getPublicId(), "audio/mp4", 1024);

        // then
        assertThat(first.uploadId()).isNotEqualTo(second.uploadId());
        assertThat(audioUploadRepository.findAll()).extracting(AudioUpload::getObjectKey).doesNotHaveDuplicates();
        assertThat(audioUploadRepository.count()).isEqualTo(2);
    }

    @Test
    void 없는_기기는_업로드_이력과_URL을_발급하지_않는다() {
        // when / then
        assertThatThrownBy(() -> audioUploadService.prepare(UUID.randomUUID(), "audio/mp4", 1024))
                .isInstanceOfSatisfying(DeviceException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(DEVICE_NOT_FOUND));
        assertThat(audioUploadRepository.count()).isZero();
        verifyNoInteractions(audioUrlIssuer);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void URL_발급이_실패하면_업로드_이력도_롤백한다() {
        // given
        Device device = deviceRepository.save(기본_기기_빌더().build());
        EmotionException failure = new EmotionException(EMOTION_AUDIO_UPLOAD_UNAVAILABLE,
                new IllegalStateException("signing failure"));
        when(audioUrlIssuer.issueUpload(any(), any())).thenThrow(failure);

        try {
            // when / then
            assertThatThrownBy(() -> audioUploadService.prepare(device.getPublicId(), "audio/mp4", 1024))
                    .isSameAs(failure);
            assertThat(audioUploadRepository.count()).isZero();
        } finally {
            audioUploadRepository.deleteAllInBatch();
            deviceRepository.deleteById(device.getId());
        }
    }

    private AudioUpload save(AudioUpload input) {
        return audioUploadService.save(input.getDeviceId(), input.getObjectKey(), input.getContentType(),
                input.getContentLength(), input.getExpiresAt());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class UploadTestConfiguration {

        @Bean
        S3Properties s3Properties() {
            return new S3Properties("test-bucket", "pheeeew/development/", "ap-northeast-2");
        }

        @Bean
        @Primary
        Clock uploadClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
