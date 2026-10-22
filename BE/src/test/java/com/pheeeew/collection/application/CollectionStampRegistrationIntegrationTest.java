package com.pheeeew.collection.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static com.pheeeew.emotion.fixture.AudioUploadFixture.기본_업로드_빌더;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_SAVE_FAILED;
import static org.mockito.Mockito.when;

import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.command.EmotionContentResolver;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.AudioUpload;
import com.pheeeew.emotion.domain.repository.AudioUploadRepository;
import com.pheeeew.emotion.infra.S3ObjectVerifier;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Import({EmotionCommandService.class, EmotionContentResolver.class,
        CollectionQueryService.class, CollectionProgressRepository.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CollectionStampRegistrationIntegrationTest {

    @Autowired
    private EmotionCommandService commands;
    @Autowired
    private CollectionQueryService queries;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private EmotionRepository emotions;
    @Autowired
    private JdbcClient jdbc;
    @MockitoBean
    private Clock clock;
    @MockitoBean
    private S3ObjectVerifier objectVerifier;
    @Autowired
    private AudioUploadRepository uploads;
    @Autowired
    private PlatformTransactionManager transactions;

    private Device device;

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(Instant.parse("2026-10-22T03:00:00Z"));
        검증용_지역_계층을_저장한다(jdbc);
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = now()").update();
        device = devices.save(기본_기기_빌더().build());
    }

    @AfterEach
    void tearDown() {
        jdbc.sql("ALTER TABLE device_daily_stamps DROP CONSTRAINT IF EXISTS test_stamp_failure").update();
        jdbc.sql("DELETE FROM device_daily_stamps").update();
        emotions.deleteAllInBatch();
        uploads.deleteAllInBatch();
        devices.deleteAllInBatch();
        jdbc.sql("DELETE FROM regions").update();
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = NULL").update();
    }

    @Test
    void 성공한_감정_등록마다_오늘의_해당_감정_스탬프가_증가한다() {
        // when
        save(UUID.randomUUID(), EmotionState.FRUSTRATED, device);
        save(UUID.randomUUID(), EmotionState.FRUSTRATED, device);

        // then
        assertThat(stamps(device, EmotionState.FRUSTRATED)).isEqualTo(2);
        assertThat(stamps(device, EmotionState.ANGRY)).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 동일_요청과_삭제_후_재시도는_집계를_다시_증가시키지_않는다(boolean deleted) {
        // given
        UUID requestId = UUID.randomUUID();
        Emotion first = save(requestId, EmotionState.FRUSTRATED, device);
        if (deleted) {
            commands.delete(first.getId(), device.getPublicId());
        }

        // when
        Emotion retried = save(requestId, EmotionState.ANGRY, device);

        // then
        assertThat(retried.getId()).isEqualTo(first.getId());
        assertThat(stamps(device, EmotionState.FRUSTRATED)).isOne();
        assertThat(stamps(device, EmotionState.ANGRY)).isZero();
        assertThat(emotions.count()).isOne();
    }

    @Test
    void 내용_수정과_삭제는_이미_인정한_횟수를_바꾸지_않는다() {
        // given
        Emotion first = save(UUID.randomUUID(), EmotionState.FRUSTRATED, device);

        // when
        commands.update(first.getId(), device.getPublicId(), EmotionState.FRUSTRATED,
                "수정한 메모", null, false, null);
        commands.delete(first.getId(), device.getPublicId());

        // then
        assertThat(stamps(device, EmotionState.FRUSTRATED)).isOne();
    }

    @Test
    void 기기와_감정별_진행도를_독립적으로_집계한다() {
        // given
        Device other = devices.save(기본_기기_빌더().build());

        // when
        save(UUID.randomUUID(), EmotionState.FRUSTRATED, device);
        save(UUID.randomUUID(), EmotionState.ANGRY, device);
        save(UUID.randomUUID(), EmotionState.ANGRY, device);
        save(UUID.randomUUID(), EmotionState.ANGRY, other);

        // then
        assertThat(stamps(device, EmotionState.FRUSTRATED)).isOne();
        assertThat(stamps(device, EmotionState.ANGRY)).isEqualTo(2);
        assertThat(stamps(other, EmotionState.FRUSTRATED)).isZero();
        assertThat(stamps(other, EmotionState.ANGRY)).isOne();
    }

    @Test
    void 한국_자정_전후의_신규_등록을_날짜별로_나누고_전날_요청은_다시_집계하지_않는다() {
        // given
        UUID previousRequest = UUID.randomUUID();
        when(clock.instant()).thenReturn(Instant.parse("2026-10-21T14:59:59.999Z"));
        save(previousRequest, EmotionState.FRUSTRATED, device);
        assertThat(stamps(device, EmotionState.FRUSTRATED)).isOne();

        // when
        when(clock.instant()).thenReturn(Instant.parse("2026-10-21T15:00:00Z"));
        save(previousRequest, EmotionState.FRUSTRATED, device);
        assertThat(stamps(device, EmotionState.FRUSTRATED)).isZero();
        save(UUID.randomUUID(), EmotionState.FRUSTRATED, device);
        save(UUID.randomUUID(), EmotionState.FRUSTRATED, device);

        // then
        assertThat(stamps(device, EmotionState.FRUSTRATED)).isEqualTo(2);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-21T14:59:59.999Z"));
        assertThat(stamps(device, EmotionState.FRUSTRATED)).isOne();
    }

    @Test
    void 스탬프_증가가_실패하면_새_감정과_녹음_연결도_함께_롤백한다() {
        // given
        save(UUID.randomUUID(), EmotionState.FRUSTRATED, device);
        AudioUpload upload = upload();
        UUID failedRequest = UUID.randomUUID();
        jdbc.sql("ALTER TABLE device_daily_stamps ADD CONSTRAINT test_stamp_failure CHECK (stamp_count < 2)").update();

        // when / then
        assertThatThrownBy(() -> commands.save(failedRequest, EmotionState.FRUSTRATED,
                126.97, 37.56, 0, null, upload.getUploadId(), null, device.getPublicId()))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_SAVE_FAILED));
        assertThat(emotions.findByRequestId(failedRequest)).isEmpty();
        assertThat(emotions.count()).isOne();
        assertThat(uploads.findById(upload.getId()).orElseThrow().getClaimedRequestId()).isNull();
        assertThat(stamps(device, EmotionState.FRUSTRATED)).isOne();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 같은_기기의_동시_등록은_서로_다른_요청만_집계한다(boolean sameRequest) throws Exception {
        // given
        UUID firstRequest = UUID.randomUUID();
        UUID secondRequest = sameRequest ? firstRequest : UUID.randomUUID();
        AudioUpload firstUpload = upload();
        AudioUpload secondUpload = upload();
        CyclicBarrier ready = new CyclicBarrier(2);
        doAnswer(invocation -> {
            ready.await(5, TimeUnit.SECONDS);
            return null;
        }).when(objectVerifier).verify(any());

        // when: 외부 트랜잭션으로 JVM 지도 쓰기 잠금을 우회하여 동시 요청 결과를 검증한다.
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentSave(firstRequest, firstUpload));
            var second = executor.submit(() -> concurrentSave(secondRequest, secondUpload));
            Emotion firstSaved = first.get(10, TimeUnit.SECONDS);
            Emotion secondSaved = second.get(10, TimeUnit.SECONDS);

            // then
            if (sameRequest) {
                assertThat(secondSaved.getId()).isEqualTo(firstSaved.getId());
            } else {
                assertThat(secondSaved.getId()).isNotEqualTo(firstSaved.getId());
            }
        }
        assertThat(stamps(device, EmotionState.FRUSTRATED)).isEqualTo(sameRequest ? 1 : 2);
        assertThat(emotions.count()).isEqualTo(sameRequest ? 1 : 2);
        assertThat(uploads.findAll()).filteredOn(upload -> upload.getClaimedRequestId() != null)
                .hasSize(sameRequest ? 1 : 2);
    }

    private Emotion concurrentSave(UUID requestId, AudioUpload upload) {
        return new TransactionTemplate(transactions).execute(status -> commands.save(requestId,
                EmotionState.FRUSTRATED, 126.97, 37.56, 0,
                null, upload.getUploadId(), null, device.getPublicId()));
    }

    private AudioUpload upload() {
        return uploads.save(기본_업로드_빌더().deviceId(device.getId())
                .objectKey("pheeeew/development/audio/" + UUID.randomUUID() + ".m4a")
                .expiresAt(Instant.now().plusSeconds(3600)).build());
    }

    private Emotion save(UUID requestId, EmotionState state, Device author) {
        return commands.save(requestId, state, 126.97, 37.56, 0,
                null, null, null, author.getPublicId());
    }

    private long stamps(Device author, EmotionState state) {
        return queries.findToday(author.getPublicId()).items().stream()
                .filter(item -> item.state() == state).findFirst().orElseThrow().stamps().count();
    }
}
