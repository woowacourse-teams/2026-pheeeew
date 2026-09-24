package com.pheeeew.emotion.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_NOT_READY;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_REQUEST_ID_CONFLICT;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_SAVE_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.command.EmotionContentResolver;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.support.PostgisDataJpaTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Import({EmotionCommandService.class, EmotionContentResolver.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmotionRegistrationIntegrationTest {

    @Autowired
    private EmotionCommandService service;
    @Autowired
    private EmotionRepository emotions;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private JdbcClient jdbc;
    @MockitoBean
    private AudioUploadLinker linker;

    private Device device;

    @BeforeEach
    void setUp() {
        device = devices.save(기본_기기_빌더().build());
    }

    @AfterEach
    void tearDown() {
        emotions.deleteAllInBatch();
        devices.deleteAllInBatch();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NONE", "MEMO", "AUDIO"})
    void 재요청은_내용을_바꾸거나_녹음을_다시_확인하지_않는다(String contentType) {
        // given
        UUID requestId = UUID.randomUUID();
        when(linker.claim("upload", device.getId(), requestId)).thenReturn("recordings/original.m4a");
        Emotion original = service.save(requestId, EmotionState.FRUSTRATED, 126.97, 37.56, 35.5,
                contentType.equals("MEMO") ? "최초 메모" : null,
                contentType.equals("AUDIO") ? "upload" : null, device.getPublicId());
        clearInvocations(linker);

        // when
        Emotion retried = service.save(requestId, EmotionState.ANGRY, 129.07, 35.17, 90,
                null, "different-upload", device.getPublicId());

        // then
        assertThat(retried.getId()).isEqualTo(original.getId());
        assertThat(retried.getRequestId()).isEqualTo(requestId);
        assertThat(retried.getDeviceId()).isEqualTo(device.getId());
        assertThat(retried.getState()).isEqualTo(EmotionState.FRUSTRATED);
        assertThat(retried.getLongitude()).isEqualTo(126.97);
        assertThat(retried.getLatitude()).isEqualTo(37.56);
        assertThat(retried.getRotationDegrees()).isEqualTo(35.5);
        assertThat(retried.getMemo()).isEqualTo(original.getMemo());
        assertThat(retried.getContent().getAudio()).isEqualTo(original.getContent().getAudio());
        assertThat(retried.getNickname()).isEqualTo(original.getNickname());
        assertThat(emotions.count()).isOne();
        verifyNoInteractions(linker);
    }

    @Test
    void 삭제된_감정도_재등록하지_않고_최초_식별자를_반환한다() {
        UUID requestId = UUID.randomUUID();
        Emotion original = service.save(requestId, EmotionState.FRUSTRATED, 126.97, 37.56, 0,
                null, null, device.getPublicId());
        jdbc.sql("UPDATE emotions SET deleted_at = now() WHERE id = :id").param("id", original.getId()).update();

        Emotion retried = saveAudio(requestId, device);

        assertThat(retried.getId()).isEqualTo(original.getId());
        assertThat(retried.getDeletedAt()).isNotNull();
        assertThat(emotions.count()).isOne();
        verifyNoInteractions(linker);
    }

    @Test
    void 다른_기기의_재요청은_녹음_확인_전에_거부한다() {
        UUID requestId = UUID.randomUUID();
        service.save(requestId, EmotionState.FRUSTRATED, 126.97, 37.56, 0, null, null, device.getPublicId());
        Device other = devices.save(기본_기기_빌더().build());

        assertThatThrownBy(() -> saveAudio(requestId, other)).isInstanceOfSatisfying(EmotionException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_REQUEST_ID_CONFLICT));
        verifyNoInteractions(linker);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 동시_삽입_실패_후_최초_감정을_조회하고_작성자를_검증한다(boolean differentDevice) throws Exception {
        // 두 요청 모두 선조회를 통과한 뒤 실제 DB 유니크 제약에서 경합하도록 한다.
        UUID requestId = UUID.randomUUID();
        Device second = differentDevice ? devices.save(기본_기기_빌더().build()) : device;
        CyclicBarrier ready = new CyclicBarrier(2);
        when(linker.claim(anyString(), anyLong(), any())).thenAnswer(invocation -> {
            ready.await(5, TimeUnit.SECONDS);
            return "recordings/" + invocation.getArgument(1) + ".m4a";
        });

        List<Object> results;
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Object> first = executor.submit(() -> outcome(requestId, device));
            Future<Object> other = executor.submit(() -> outcome(requestId, second));
            results = List.of(first.get(10, TimeUnit.SECONDS), other.get(10, TimeUnit.SECONDS));
        }

        assertThat(emotions.count()).isOne();
        Emotion stored = emotions.findAll().getFirst();
        assertThat(results).filteredOn(Emotion.class::isInstance).hasSize(differentDevice ? 1 : 2)
                .allSatisfy(result -> {
                    Emotion saved = (Emotion) result;
                    assertThat(saved.getId()).isEqualTo(stored.getId());
                    assertThat(saved.getDeviceId()).isEqualTo(stored.getDeviceId());
                    assertThat(saved.getContent().getAudio()).isEqualTo(stored.getContent().getAudio());
                });
        if (differentDevice) {
            assertThat(results).filteredOn(EmotionException.class::isInstance).singleElement().satisfies(result ->
                    assertThat(((EmotionException) result).getErrorCode()).isEqualTo(EMOTION_REQUEST_ID_CONFLICT));
        }
    }

    @Test
    void 최초_감정이_없는_저장_실패는_성공으로_처리하지_않는다() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("link constraint");
        when(linker.claim(anyString(), anyLong(), any())).thenThrow(failure);

        assertThatThrownBy(() -> saveAudio(UUID.randomUUID(), device))
                .isInstanceOfSatisfying(EmotionException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(EMOTION_SAVE_FAILED);
                    assertThat(error.getCause()).isSameAs(failure);
                });
        assertThat(emotions.count()).isZero();
    }

    @Test
    void 업로드_검증_오류는_그대로_전달한다() {
        when(linker.claim(anyString(), anyLong(), any())).thenThrow(new EmotionException(EMOTION_AUDIO_UPLOAD_NOT_READY));
        assertThatThrownBy(() -> saveAudio(UUID.randomUUID(), device))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_NOT_READY));
        assertThat(emotions.count()).isZero();
    }

    private Emotion saveAudio(UUID requestId, Device author) {
        return service.save(requestId, EmotionState.FRUSTRATED, 126.97, 37.56, 35.5,
                null, "upload", author.getPublicId());
    }

    private Object outcome(UUID requestId, Device author) {
        try {
            return saveAudio(requestId, author);
        } catch (EmotionException exception) {
            return exception;
        }
    }
}
