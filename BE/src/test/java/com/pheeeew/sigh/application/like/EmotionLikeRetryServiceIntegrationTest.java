package com.pheeeew.sigh.application.like;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.sigh.fixture.SighFixture.기본_한숨_빌더;
import static com.pheeeew.sigh.fixture.SighLikeFixture.기본_좋아요_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.sigh.application.like.dto.SighLikeResult;
import com.pheeeew.sigh.domain.Sigh;
import com.pheeeew.sigh.domain.repository.SighLikeRepository;
import com.pheeeew.sigh.domain.repository.SighRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@PostgisDataJpaTest
@Import({EmotionLikeRetryService.class, SighLikeService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmotionLikeRetryServiceIntegrationTest {

    @Autowired
    private EmotionLikeRetryService emotionLikeRetryService;

    @MockitoSpyBean
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 낙관적_잠금_충돌은_롤백한_뒤_새_트랜잭션에서_재시도한다(boolean liked) {
        // given
        if (!liked) {
            saveLike();
        }
        conflictOnFirstAttempts(liked, 1);

        // when
        SighLikeResult result = emotionLikeRetryService.update(sigh.getId(), device.getPublicId(), liked);

        // then
        assertThat(result).isEqualTo(SighLikeResult.of(liked, liked ? 2 : 1));
        verify(serviceSpy(), times(2)).update(sigh.getId(), device.getPublicId(), liked);
        assertThat(sighLikeRepository.findBySighIdAndDeviceId(sigh.getId(), device.getId()).isPresent())
                .isEqualTo(liked);
        assertLikeCount(liked ? 2 : 1);
    }

    @Test
    void 세_번_모두_충돌하면_예외를_전파하고_다른_기기의_성공한_변경을_보존한다() {
        // given
        conflictOnFirstAttempts(true, 3);

        // when / then
        assertThatThrownBy(() -> emotionLikeRetryService.update(sigh.getId(), device.getPublicId(), true))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(serviceSpy(), times(3)).update(sigh.getId(), device.getPublicId(), true);
        assertThat(sighLikeRepository.findBySighIdAndDeviceId(sigh.getId(), device.getId())).isEmpty();
        assertLikeCount(3);
    }

    @Test
    void 낙관적_잠금_충돌이_아닌_예외는_재시도하지_않는다() {
        // given
        UUID missingDevicePublicId = UUID.randomUUID();

        // when / then
        assertThatThrownBy(() -> emotionLikeRetryService.update(sigh.getId(), missingDevicePublicId, true))
                .isInstanceOfSatisfying(DeviceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
        verify(serviceSpy()).update(sigh.getId(), missingDevicePublicId, true);
        assertLikeCount(0);
    }

    @Test
    void 외부_트랜잭션에서는_재시도_서비스를_실행하지_않는다() {
        // given
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // when / then
        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                emotionLikeRetryService.update(sigh.getId(), device.getPublicId(), true)))
                .isInstanceOf(IllegalTransactionStateException.class);
        verifyNoInteractions(serviceSpy());
        assertLikeCount(0);
    }

    private void saveLike() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            sighRepository.findById(sigh.getId()).orElseThrow().increaseLikeCount();
            sighLikeRepository.save(기본_좋아요_빌더()
                    .sighId(sigh.getId())
                    .deviceId(device.getId())
                    .build());
        });
    }

    private void conflictOnFirstAttempts(boolean liked, int conflictCount) {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            if (attempts.incrementAndGet() <= conflictCount) {
                sighRepository.findById(sigh.getId()).orElseThrow();
                TransactionTemplate anotherTransaction = new TransactionTemplate(transactionManager);
                anotherTransaction.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
                anotherTransaction.executeWithoutResult(status -> {
                    Device anotherDevice = deviceRepository.save(기본_기기_빌더().build());
                    sighLikeService.update(sigh.getId(), anotherDevice.getPublicId(), true);
                });
            }
            return invocation.callRealMethod();
        }).when(serviceSpy()).update(sigh.getId(), device.getPublicId(), liked);
    }

    private SighLikeService serviceSpy() {
        return AopTestUtils.getUltimateTargetObject(sighLikeService);
    }

    private void assertLikeCount(long expected) {
        long likeRowCount = jdbcClient.sql("SELECT COUNT(*) FROM sigh_likes WHERE sigh_id = :sighId")
                .param("sighId", sigh.getId())
                .query(Long.class)
                .single();

        assertThat(likeRowCount).isEqualTo(expected);
        assertThat(sighRepository.findById(sigh.getId()).orElseThrow().getLikeCount()).isEqualTo(expected);
    }
}
