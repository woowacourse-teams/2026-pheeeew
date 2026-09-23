package com.pheeeew.report.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.sigh.fixture.SighFixture.기기가_있는_한숨_빌더;
import static com.pheeeew.sigh.fixture.SighFixture.기본_한숨_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.report.domain.EmotionReport;
import com.pheeeew.report.domain.repository.EmotionReportRepository;
import com.pheeeew.report.exception.EmotionReportErrorCode;
import com.pheeeew.report.exception.EmotionReportException;
import com.pheeeew.sigh.domain.Emotion;
import com.pheeeew.sigh.domain.repository.EmotionRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SighReportAutoDeleteIntegrationTest {

    private static final int 자동_삭제_임계값 = 5;

    @Autowired
    private EmotionReportService emotionReportService;

    @Autowired
    private EmotionReportRepository emotionReportRepository;

    @Autowired
    private EmotionRepository emotionRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @AfterEach
    void tearDown() {
        emotionReportRepository.deleteAllInBatch();
        emotionRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 4})
    void 신고가_임계값에_못_미치면_한숨이_남는다(int 신고_수) {
        // given
        Long sighId = 한숨을_저장한다();
        서로_다른_기기로_신고한다(sighId, 신고_수);

        // when
        int 삭제_수 = emotionReportService.deleteReportedOverThreshold();

        // then
        assertThat(삭제_수).isZero();
        assertThat(삭제_시각(sighId)).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 6})
    void 신고가_임계값_이상이면_한숨을_삭제한다(int 신고_수) {
        // given
        Long sighId = 한숨을_저장한다();
        서로_다른_기기로_신고한다(sighId, 신고_수);

        // when
        int 삭제_수 = emotionReportService.deleteReportedOverThreshold();

        // then
        assertThat(삭제_수).isOne();
        assertThat(삭제_시각(sighId)).isNotNull();
    }

    @Test
    void 삭제해도_신고_기록은_남는다() {
        // given
        Long sighId = 한숨을_저장한다();
        서로_다른_기기로_신고한다(sighId, 자동_삭제_임계값);

        // when
        emotionReportService.deleteReportedOverThreshold();

        // then
        assertThat(emotionReportRepository.count()).isEqualTo(자동_삭제_임계값);
    }

    @Test
    void 이미_삭제한_한숨은_다시_갱신하지_않는다() {
        // given
        Long sighId = 한숨을_저장한다();
        서로_다른_기기로_신고한다(sighId, 자동_삭제_임계값);
        emotionReportService.deleteReportedOverThreshold();
        Instant 첫_삭제_시각 = 삭제_시각(sighId);

        // when
        int 두_번째_삭제_수 = emotionReportService.deleteReportedOverThreshold();

        // then
        assertThat(두_번째_삭제_수).isZero();
        assertThat(삭제_시각(sighId)).isEqualTo(첫_삭제_시각);
    }

    @Test
    void 임계값을_넘긴_한숨만_삭제하고_나머지는_남긴다() {
        // given
        Long 삭제될_한숨 = 한숨을_저장한다();
        Long 남을_한숨 = 한숨을_저장한다();
        서로_다른_기기로_신고한다(삭제될_한숨, 자동_삭제_임계값);
        서로_다른_기기로_신고한다(남을_한숨, 자동_삭제_임계값 - 1);

        // when
        int 삭제_수 = emotionReportService.deleteReportedOverThreshold();

        // then
        assertThat(삭제_수).isOne();
        assertThat(삭제_시각(삭제될_한숨)).isNotNull();
        assertThat(삭제_시각(남을_한숨)).isNull();
    }

    @Test
    void 여러_한숨이_임계값을_넘기면_한_번에_삭제한다() {
        // given
        List<Long> 한숨들 = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Long sighId = 한숨을_저장한다();
            서로_다른_기기로_신고한다(sighId, 자동_삭제_임계값);
            한숨들.add(sighId);
        }

        // when
        int 삭제_수 = emotionReportService.deleteReportedOverThreshold();

        // then
        assertThat(삭제_수).isEqualTo(3);
        assertThat(한숨들).allSatisfy(sighId -> assertThat(삭제_시각(sighId)).isNotNull());
    }

    @Test
    void 자기_한숨은_신고할_수_없다() {
        // given
        Device 작성자 = deviceRepository.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
        Long sighId = 작성자가_쓴_한숨을_저장한다(작성자.getId());

        // when
        Throwable throwable = catchThrowable(
                () -> emotionReportService.save(sighId, 작성자.getPublicId(), "테스트 신고")
        );

        // then
        assertThat(throwable).isInstanceOf(EmotionReportException.class);
        assertThat(((EmotionReportException) throwable).getErrorCode())
                .isEqualTo(EmotionReportErrorCode.EMOTION_REPORT_SELF_NOT_ALLOWED);
        assertThat(emotionReportRepository.count()).isZero();
    }

    @Test
    void 남의_한숨은_신고할_수_있다() {
        // given
        Device 작성자 = deviceRepository.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
        Device 신고자 = deviceRepository.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
        Long sighId = 작성자가_쓴_한숨을_저장한다(작성자.getId());

        // when
        emotionReportService.save(sighId, 신고자.getPublicId(), "테스트 신고");

        // then
        assertThat(emotionReportRepository.count()).isOne();
    }

    @Test
    void 작성자를_모르는_한숨은_누구나_신고할_수_있다() {
        // given
        Device 신고자 = deviceRepository.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
        Long sighId = 한숨을_저장한다();

        // when
        emotionReportService.save(sighId, 신고자.getPublicId(), "테스트 신고");

        // then
        assertThat(emotionReportRepository.count()).isOne();
    }

    private Long 작성자가_쓴_한숨을_저장한다(Long deviceId) {
        Emotion sigh = emotionRepository.saveAndFlush(
                기기가_있는_한숨_빌더(deviceId).requestId(UUID.randomUUID()).build()
        );

        return sigh.getId();
    }

    private Long 한숨을_저장한다() {
        Emotion sigh = emotionRepository.saveAndFlush(기본_한숨_빌더().requestId(UUID.randomUUID()).build());

        return sigh.getId();
    }

    private void 서로_다른_기기로_신고한다(Long sighId, int 신고_수) {
        for (int i = 0; i < 신고_수; i++) {
            Device device = deviceRepository.saveAndFlush(
                    기본_기기_빌더().requestId(UUID.randomUUID()).build()
            );
            emotionReportRepository.saveAndFlush(
                    EmotionReport.builder()
                            .emotionId(sighId)
                            .reporterDeviceId(device.getId())
                            .reason("테스트 신고")
                            .build()
            );
        }
    }

    private Instant 삭제_시각(Long sighId) {
        return jdbcClient.sql("SELECT deleted_at FROM sighs WHERE id = ?")
                .param(sighId)
                .query(Instant.class)
                .optional()
                .orElse(null);
    }
}
