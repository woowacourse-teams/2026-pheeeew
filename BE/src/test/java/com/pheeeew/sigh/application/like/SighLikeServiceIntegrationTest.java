package com.pheeeew.sigh.application.like;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.sigh.fixture.SighFixture.기본_한숨_빌더;
import static com.pheeeew.sigh.fixture.SighLikeFixture.기본_좋아요_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.sigh.application.like.dto.SighLikeResult;
import com.pheeeew.sigh.domain.Sigh;
import com.pheeeew.sigh.domain.SighLike;
import com.pheeeew.sigh.domain.repository.SighLikeRepository;
import com.pheeeew.sigh.domain.repository.SighRepository;
import com.pheeeew.sigh.exception.SighErrorCode;
import com.pheeeew.sigh.exception.SighException;
import com.pheeeew.support.PostgisDataJpaTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@PostgisDataJpaTest
@Import(SighLikeService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SighLikeServiceIntegrationTest {

    @Autowired
    private SighLikeService sighLikeService;

    @Autowired
    private SighLikeRepository sighLikeRepository;

    @Autowired
    private SighRepository sighRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcClient jdbcClient;

    private Device device;
    private Sigh sigh;

    @BeforeEach
    void setUp() {
        device = deviceRepository.save(기본_기기_빌더().build());
        sigh = sighRepository.save(기본_한숨_빌더().build());
    }

    @AfterEach
    void tearDown() {
        sighLikeRepository.deleteAllInBatch();
        sighRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
    }

    @Test
    void 요청한_좋아요_상태를_반영하고_같은_상태를_반복_요청해도_유지한다() {
        // given
        Long sighId = sigh.getId();
        UUID devicePublicId = device.getPublicId();

        // when / then
        assertThat(sighLikeService.update(sighId, devicePublicId, false)).isEqualTo(SighLikeResult.of(false, 0));
        assertLikeCount(sighId, 0);
        assertThat(sighLikeService.update(sighId, devicePublicId, true)).isEqualTo(SighLikeResult.of(true, 1));
        assertLikeCount(sighId, 1);
        Long likeId = sighLikeRepository.findBySighIdAndDeviceId(sighId, device.getId()).orElseThrow().getId();
        assertThat(sighLikeService.update(sighId, devicePublicId, true)).isEqualTo(SighLikeResult.of(true, 1));
        assertThat(sighLikeRepository.findAll()).extracting(SighLike::getId).containsExactly(likeId);
        assertLikeCount(sighId, 1);

        assertThat(sighLikeService.update(sighId, devicePublicId, false)).isEqualTo(SighLikeResult.of(false, 0));
        assertLikeCount(sighId, 0);
        assertThat(sighLikeService.update(sighId, devicePublicId, false)).isEqualTo(SighLikeResult.of(false, 0));
        assertLikeCount(sighId, 0);
        assertThat(sighLikeService.update(sighId, devicePublicId, true)).isEqualTo(SighLikeResult.of(true, 1));
        assertLikeCount(sighId, 1);
        assertThat(sighLikeRepository.findBySighIdAndDeviceId(sighId, device.getId()))
                .get().extracting(SighLike::getId).isNotEqualTo(likeId);
    }

    @Test
    void 좋아요를_취소해도_다른_기기나_다른_한숨의_좋아요는_유지한다() {
        // given
        Device anotherDevice = deviceRepository.save(기본_기기_빌더().build());
        Sigh anotherSigh = sighRepository.save(기본_한숨_빌더().build());
        saveLike(sigh.getId(), device.getId());
        SighLike anotherDeviceLike = saveLike(sigh.getId(), anotherDevice.getId());
        SighLike anotherSighLike = saveLike(anotherSigh.getId(), device.getId());

        // when
        SighLikeResult result = sighLikeService.update(sigh.getId(), device.getPublicId(), false);

        // then
        assertThat(result).isEqualTo(SighLikeResult.of(false, 1));
        assertThat(sighLikeRepository.findBySighIdAndDeviceId(sigh.getId(), device.getId())).isEmpty();
        assertThat(sighLikeRepository.findAll()).extracting(SighLike::getId)
                .containsExactlyInAnyOrder(anotherDeviceLike.getId(), anotherSighLike.getId());
        assertLikeCount(sigh.getId(), 1);
        assertLikeCount(anotherSigh.getId(), 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 없는_기기나_없는_한숨이나_삭제된_한숨은_요청한_상태와_관계없이_거부한다(boolean liked) {
        // given
        SighLike like = saveLike(sigh.getId(), device.getId());

        // when / then
        assertThatThrownBy(() -> sighLikeService.update(sigh.getId(), UUID.randomUUID(), liked))
                .isInstanceOfSatisfying(DeviceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
        assertThatThrownBy(() -> sighLikeService.update(Long.MAX_VALUE, device.getPublicId(), liked))
                .isInstanceOfSatisfying(SighException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(SighErrorCode.SIGH_NOT_FOUND));
        Sigh latestSigh = sighRepository.findById(sigh.getId()).orElseThrow();
        latestSigh.delete();
        sighRepository.saveAndFlush(latestSigh);
        assertThatThrownBy(() -> sighLikeService.update(sigh.getId(), device.getPublicId(), liked))
                .isInstanceOfSatisfying(SighException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(SighErrorCode.SIGH_NOT_FOUND));
        assertThat(sighLikeRepository.findAll()).extracting(SighLike::getId).containsExactly(like.getId());
        assertLikeCount(sigh.getId(), 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 트랜잭션이_실패하면_좋아요_생성이나_삭제와_개수가_함께_롤백된다(boolean desiredLiked) {
        // given
        SighLike original = desiredLiked ? null : saveLike(sigh.getId(), device.getId());

        // when
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            sighLikeService.update(sigh.getId(), device.getPublicId(), desiredLiked);
            sighLikeRepository.flush();
            assertLikeCount(sigh.getId(), desiredLiked ? 1 : 0);
            throw new IllegalStateException("저장 후 실패");
        })).isInstanceOf(IllegalStateException.class).hasMessage("저장 후 실패");

        // then
        if (desiredLiked) {
            assertThat(sighLikeRepository.findAll()).isEmpty();
        } else {
            assertThat(sighLikeRepository.findAll()).extracting(SighLike::getId).containsExactly(original.getId());
        }
        assertLikeCount(sigh.getId(), desiredLiked ? 0 : 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 낙관적_잠금_충돌이_발생하면_먼저_성공한_좋아요와_개수를_보존한다(boolean desiredLiked) {
        // given
        SighLike original = desiredLiked ? null : saveLike(sigh.getId(), device.getId());
        Device anotherDevice = deviceRepository.save(기본_기기_빌더().build());
        TransactionTemplate anotherTransaction = new TransactionTemplate(transactionManager);
        anotherTransaction.setPropagationBehavior(Propagation.REQUIRES_NEW.value());

        // when
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // 바깥 트랜잭션이 읽은 버전을 유지한 채 다른 기기의 변경을 먼저 커밋한다.
            sighRepository.findById(sigh.getId()).orElseThrow();
            anotherTransaction.executeWithoutResult(anotherStatus ->
                    sighLikeService.update(sigh.getId(), anotherDevice.getPublicId(), true));
            sighLikeService.update(sigh.getId(), device.getPublicId(), desiredLiked);
            sighLikeRepository.flush();
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // then
        assertThat(sighLikeRepository.findBySighIdAndDeviceId(sigh.getId(), anotherDevice.getId())).isPresent();
        if (desiredLiked) {
            assertThat(sighLikeRepository.findBySighIdAndDeviceId(sigh.getId(), device.getId())).isEmpty();
        } else {
            assertThat(sighLikeRepository.findBySighIdAndDeviceId(sigh.getId(), device.getId()))
                    .get().extracting(SighLike::getId).isEqualTo(original.getId());
        }
        assertLikeCount(sigh.getId(), desiredLiked ? 1 : 2);
    }

    private void assertLikeCount(Long sighId, long expected) {
        long likeRowCount = jdbcClient.sql("SELECT COUNT(*) FROM sigh_likes WHERE sigh_id = :sighId")
                .param("sighId", sighId)
                .query(Long.class)
                .single();

        assertThat(likeRowCount).isEqualTo(expected);
        assertThat(sighRepository.findById(sighId).orElseThrow().getLikeCount()).isEqualTo(expected);
    }

    private SighLike saveLike(Long sighId, Long deviceId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            sighRepository.findById(sighId).orElseThrow().increaseLikeCount();
            return sighLikeRepository.save(기본_좋아요_빌더()
                    .sighId(sighId)
                    .deviceId(deviceId)
                    .build());
        });
    }
}
