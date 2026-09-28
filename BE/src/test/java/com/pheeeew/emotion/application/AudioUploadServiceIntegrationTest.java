package com.pheeeew.emotion.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.AudioUploadFixture.기본_업로드_빌더;
import static com.pheeeew.emotion.fixture.AudioUploadFixture.만료_시각;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.AudioUpload;
import com.pheeeew.emotion.domain.repository.AudioUploadRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@PostgisDataJpaTest
@Import(AudioUploadService.class)
class AudioUploadServiceIntegrationTest {

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

    private AudioUpload save(AudioUpload input) {
        return audioUploadService.save(input.getDeviceId(), input.getObjectKey(), input.getContentType(),
                input.getContentLength(), input.getExpiresAt());
    }
}
