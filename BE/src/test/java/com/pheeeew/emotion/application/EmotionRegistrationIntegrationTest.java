package com.pheeeew.emotion.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.AudioUploadFixture.기본_업로드_빌더;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_NOT_READY;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_ALREADY_USED;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_REQUEST_ID_CONFLICT;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_SAVE_FAILED;
import static com.pheeeew.groups.fixture.GroupFixture.기본_그룹_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_스탬프_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.일반_멤버_빌더;
import static com.pheeeew.groups.exception.GroupErrorCode.GROUP_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.command.EmotionContentResolver;
import com.pheeeew.emotion.domain.AudioUpload;
import com.pheeeew.emotion.infra.S3ObjectVerifier;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.AudioUploadRepository;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.support.PostgisDataJpaTest;
import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupMember;
import com.pheeeew.groups.domain.GroupStamp;
import com.pheeeew.groups.domain.repository.GroupRepository;
import com.pheeeew.groups.domain.repository.GroupMemberRepository;
import com.pheeeew.groups.domain.repository.GroupStampRepository;
import com.pheeeew.groups.exception.GroupException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
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
    private AudioUploadRepository uploads;
    @Autowired
    private JdbcClient jdbc;
    @MockitoBean
    private S3ObjectVerifier objectVerifier;

    @Autowired
    private GroupRepository groups;
    @Autowired
    private GroupMemberRepository members;
    @Autowired
    private GroupStampRepository stamps;

    private Device device;
    private AudioUpload upload;

    @BeforeEach
    void setUp() {
        device = devices.save(기본_기기_빌더().build());
        upload = saveUpload(device);
    }

    @AfterEach
    void tearDown() {
        emotions.deleteAllInBatch();
        members.deleteAllInBatch();
        stamps.deleteAllInBatch();
        groups.deleteAllInBatch();
        uploads.deleteAllInBatch();
        devices.deleteAllInBatch();
    }

    @Test
    void 소속_그룹의_스탬프를_저장하고_탈퇴_후_재시도도_최초_연결을_유지한다() {
        // given
        Group group = groups.save(기본_그룹_빌더().build());
        GroupStamp stamp = stamps.save(기본_스탬프_빌더(group).build());
        GroupMember member = members.save(일반_멤버_빌더(group, device).build());
        UUID requestId = UUID.randomUUID();

        // when
        Emotion saved = service.save(requestId, EmotionState.FRUSTRATED, 126.97, 37.56, 0,
                null, null, group.getPublicId(), device.getPublicId());
        member.leave(Instant.now());
        members.saveAndFlush(member);
        Emotion retried = service.save(requestId, EmotionState.ANGRY, 126.97, 37.56, 0,
                null, "unused-upload", UUID.randomUUID(), device.getPublicId());

        // then
        assertThat(emotions.findById(saved.getId()).orElseThrow().getGroupStamp().getId()).isEqualTo(stamp.getId());
        assertThat(retried.getId()).isEqualTo(saved.getId());
        assertThat(retried.getGroupStamp().getId()).isEqualTo(stamp.getId());
        assertThat(emotions.count()).isOne();
        verifyNoInteractions(objectVerifier);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nonMember", "left", "deleted", "missing"})
    void 사용할_수_없는_그룹은_녹음_연결과_감정_저장_전에_거부한다(String reason) {
        // given
        Group group = groups.save(기본_그룹_빌더().build());
        stamps.save(기본_스탬프_빌더(group).build());
        if (!reason.equals("nonMember")) {
            GroupMember member = 일반_멤버_빌더(group, device).build();
            if (reason.equals("left")) {
                member.leave(Instant.now());
            }
            members.saveAndFlush(member);
        }
        if (reason.equals("deleted")) {
            group.delete(Instant.now());
            groups.saveAndFlush(group);
        }
        UUID groupId = reason.equals("missing") ? UUID.randomUUID() : group.getPublicId();

        // when / then
        assertThatThrownBy(() -> service.save(UUID.randomUUID(), EmotionState.FRUSTRATED, 126.97, 37.56, 0,
                null, upload.getUploadId(), groupId, device.getPublicId()))
                .isInstanceOfSatisfying(GroupException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(GROUP_NOT_FOUND));
        assertThat(emotions.count()).isZero();
        verifyNoInteractions(objectVerifier);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NONE", "MEMO", "AUDIO"})
    void 재요청은_내용을_바꾸거나_녹음을_다시_확인하지_않는다(String contentType) {
        // given
        UUID requestId = UUID.randomUUID();
        Emotion original = service.save(requestId, EmotionState.FRUSTRATED, 126.97, 37.56, 35.5,
                contentType.equals("MEMO") ? "최초 메모" : null,
                contentType.equals("AUDIO") ? upload.getUploadId() : null, null, device.getPublicId());
        clearInvocations(objectVerifier);

        // when
        Emotion retried = service.save(requestId, EmotionState.ANGRY, 129.07, 35.17, 90,
                null, "different-upload", null, device.getPublicId());

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
        verifyNoInteractions(objectVerifier);
    }

    @Test
    void 삭제된_감정도_재등록하지_않고_최초_식별자를_반환한다() {
        UUID requestId = UUID.randomUUID();
        Emotion original = service.save(requestId, EmotionState.FRUSTRATED, 126.97, 37.56, 0,
                null, null, null, device.getPublicId());
        jdbc.sql("UPDATE emotions SET deleted_at = now() WHERE id = :id").param("id", original.getId()).update();

        Emotion retried = saveAudio(requestId, device);

        assertThat(retried.getId()).isEqualTo(original.getId());
        assertThat(retried.getDeletedAt()).isNotNull();
        assertThat(emotions.count()).isOne();
        verifyNoInteractions(objectVerifier);
    }

    @Test
    void 다른_기기의_재요청은_녹음_확인_전에_거부한다() {
        UUID requestId = UUID.randomUUID();
        service.save(requestId, EmotionState.FRUSTRATED, 126.97, 37.56, 0, null, null, null, device.getPublicId());
        Device other = devices.save(기본_기기_빌더().build());

        assertThatThrownBy(() -> saveAudio(requestId, other)).isInstanceOfSatisfying(EmotionException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_REQUEST_ID_CONFLICT));
        verifyNoInteractions(objectVerifier);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 동시_삽입_실패_후_최초_감정을_조회하고_작성자를_검증한다(boolean differentDevice) throws Exception {
        // 두 요청 모두 선조회를 통과한 뒤 실제 DB 유니크 제약에서 경합하도록 한다.
        UUID requestId = UUID.randomUUID();
        Device second = differentDevice ? devices.save(기본_기기_빌더().build()) : device;
        AudioUpload secondUpload = saveUpload(second);
        CyclicBarrier ready = new CyclicBarrier(2);
        doAnswer(invocation -> {
            ready.await(5, TimeUnit.SECONDS);
            return null;
        }).when(objectVerifier).verify(any());

        List<Object> results;
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Object> first = executor.submit(() -> outcome(requestId, device, upload.getUploadId()));
            Future<Object> other = executor.submit(() -> outcome(requestId, second, secondUpload.getUploadId()));
            results = List.of(first.get(10, TimeUnit.SECONDS), other.get(10, TimeUnit.SECONDS));
        }

        assertThat(emotions.count()).isOne();
        Emotion stored = emotions.findAll().getFirst();
        assertThat(uploads.findAll()).filteredOn(value -> value.getClaimedRequestId() != null)
                .singleElement().satisfies(value -> {
                    assertThat(value.getClaimedRequestId()).isEqualTo(requestId);
                    assertThat(value.getObjectKey()).isEqualTo(stored.getContent().getAudio().getObjectKey());
                });
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 같은_업로드의_동시_연결은_잠금_후_최초_요청에만_허용한다(boolean sameRequest) throws Exception {
        // given: 첫 요청이 객체를 검증하는 동안 업로드 행 잠금을 유지한다.
        UUID requestId = UUID.randomUUID();
        UUID secondRequestId = sameRequest ? requestId : UUID.randomUUID();
        CountDownLatch verifying = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger firstBackendPid = new AtomicInteger();
        doAnswer(invocation -> {
            firstBackendPid.set(jdbc.sql("SELECT pg_backend_pid()").query(Integer.class).single());
            verifying.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(objectVerifier).verify(any());

        List<Object> results;
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Object> first = executor.submit(() -> outcome(requestId, device, upload.getUploadId()));
            try {
                assertThat(verifying.await(5, TimeUnit.SECONDS)).isTrue();
                Future<Object> second = executor.submit(() -> outcome(secondRequestId, device, upload.getUploadId()));
                // 스레드 시작 여부가 아니라 PostgreSQL에서 실제 잠금 대기를 확인한다.
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waiting;
                do {
                    waiting = jdbc.sql("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE :pid = ANY(pg_blocking_pids(pid)))")
                            .param("pid", firstBackendPid.get()).query(Boolean.class).single();
                    if (!waiting) {
                        Thread.sleep(10);
                    }
                } while (!waiting && System.nanoTime() < deadline);
                assertThat(waiting).isTrue();
                release.countDown();
                results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }

        // then
        assertThat(emotions.count()).isOne();
        Emotion stored = emotions.findAll().getFirst();
        assertThat(results).filteredOn(Emotion.class::isInstance).hasSize(sameRequest ? 2 : 1)
                .allSatisfy(value -> assertThat(((Emotion) value).getId()).isEqualTo(stored.getId()));
        if (!sameRequest) {
            assertThat(results).filteredOn(EmotionException.class::isInstance).singleElement().satisfies(value ->
                    assertThat(((EmotionException) value).getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_ALREADY_USED));
        }
        assertThat(uploads.findByUploadId(upload.getUploadId()).orElseThrow().getClaimedRequestId()).isEqualTo(requestId);
        verify(objectVerifier, times(1)).verify(any());
    }

    @Test
    void 최초_감정이_없는_저장_실패는_성공으로_처리하지_않는다() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("link constraint");
        doThrow(failure).when(objectVerifier).verify(any());

        assertThatThrownBy(() -> saveAudio(UUID.randomUUID(), device))
                .isInstanceOfSatisfying(EmotionException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(EMOTION_SAVE_FAILED);
                    assertThat(error.getCause()).isSameAs(failure);
                });
        assertThat(emotions.count()).isZero();
    }

    @Test
    void 업로드_검증_오류는_그대로_전달한다() {
        doThrow(new EmotionException(EMOTION_AUDIO_UPLOAD_NOT_READY)).when(objectVerifier).verify(any());
        assertThatThrownBy(() -> saveAudio(UUID.randomUUID(), device))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_NOT_READY));
        assertThat(emotions.count()).isZero();
    }

    private AudioUpload saveUpload(Device author) {
        return uploads.save(기본_업로드_빌더().deviceId(author.getId())
                .objectKey("recordings/" + UUID.randomUUID() + ".m4a")
                .expiresAt(Instant.now().plusSeconds(3600)).build());
    }

    private Emotion saveAudio(UUID requestId, Device author) {
        return saveAudio(requestId, author, upload.getUploadId());
    }

    private Emotion saveAudio(UUID requestId, Device author, String uploadId) {
        return service.save(requestId, EmotionState.FRUSTRATED, 126.97, 37.56, 35.5,
                null, uploadId, null, author.getPublicId());
    }

    private Object outcome(UUID requestId, Device author, String uploadId) {
        try {
            return saveAudio(requestId, author, uploadId);
        } catch (EmotionException exception) {
            return exception;
        }
    }
}
