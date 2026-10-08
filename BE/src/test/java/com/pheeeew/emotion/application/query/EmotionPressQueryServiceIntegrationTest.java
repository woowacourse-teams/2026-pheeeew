package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.DeviceDailyPressFixture.개인_프레스를_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.dto.EmotionPressDailyResult;
import com.pheeeew.emotion.application.dto.EmotionPressTotalResult;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmotionPressQueryServiceIntegrationTest {

    private static final Instant 기준_시각 = Instant.parse("2026-10-08T03:00:00Z");

    @MockitoBean
    private Clock clock;

    @Autowired
    private EmotionPressQueryService emotionPressQueryService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(기준_시각);
    }

    @AfterEach
    void tearDown() {
        jdbc.sql("DELETE FROM device_daily_presses").update();
        deviceRepository.deleteAllInBatch();
    }

    @Test
    void 내_집계는_누르지_않은_감정도_영으로_돌려준다() {
        // given
        Device 기기 = 기기를_저장한다();
        개인_프레스를_저장한다(jdbc, 기기.getId(), 오늘(), EmotionState.ANGRY, 2);

        // when
        EmotionPressDailyResult 결과 = emotionPressQueryService.findMyDailyPresses(기기.getPublicId(), 0);

        // then
        assertThat(결과.counts()).hasSize(EmotionState.values().length);
        assertThat(결과.counts().get(EmotionState.EXHAUSTED)).isZero();
        assertThat(결과.total()).isEqualTo(2);
    }

    @Test
    void 내_집계는_어제_기록을_섞지_않는다() {
        // given
        Device 기기 = 기기를_저장한다();
        개인_프레스를_저장한다(jdbc, 기기.getId(), 오늘().minusDays(1), EmotionState.ANGRY, 9);
        개인_프레스를_저장한다(jdbc, 기기.getId(), 오늘(), EmotionState.ANGRY, 2);

        // when
        EmotionPressDailyResult 오늘_결과 = emotionPressQueryService.findMyDailyPresses(기기.getPublicId(), 0);
        EmotionPressDailyResult 어제_결과 = emotionPressQueryService.findMyDailyPresses(기기.getPublicId(), 1);

        // then
        assertThat(오늘_결과.total()).isEqualTo(2);
        assertThat(어제_결과.pressDate()).isEqualTo(오늘().minusDays(1));
        assertThat(어제_결과.total()).isEqualTo(9);
    }

    @Test
    void 내_집계에_다른_기기의_기록이_들어오지_않는다() {
        // given
        Device 내_기기 = 기기를_저장한다();
        Device 남의_기기 = 기기를_저장한다();
        개인_프레스를_저장한다(jdbc, 내_기기.getId(), 오늘(), EmotionState.ANGRY, 2);
        개인_프레스를_저장한다(jdbc, 남의_기기.getId(), 오늘(), EmotionState.ANGRY, 100);

        // when
        EmotionPressDailyResult 결과 = emotionPressQueryService.findMyDailyPresses(내_기기.getPublicId(), 0);

        // then
        assertThat(결과.total()).isEqualTo(2);
    }

    @Test
    void 누른_적이_없으면_다섯_감정이_모두_영이다() {
        // given
        Device 기기 = 기기를_저장한다();

        // when
        EmotionPressDailyResult 결과 = emotionPressQueryService.findMyDailyPresses(기기.getPublicId(), 0);

        // then
        assertThat(결과.counts().values()).containsOnly(0L);
        assertThat(결과.total()).isZero();
    }

    @Test
    void 없는_기기로_조회하면_거부한다() {
        // when
        Throwable 예외 = catchThrowable(
                () -> emotionPressQueryService.findMyDailyPresses(UUID.randomUUID(), 0));

        // then
        assertThat(예외).isInstanceOf(DeviceException.class);
    }

    @Test
    void 전체_총합은_모든_기기를_더한다() {
        // given
        Device 기기 = 기기를_저장한다();
        Device 다른_기기 = 기기를_저장한다();
        개인_프레스를_저장한다(jdbc, 기기.getId(), 오늘(), EmotionState.ANGRY, 4);
        개인_프레스를_저장한다(jdbc, 기기.getId(), 오늘(), EmotionState.EXHAUSTED, 3);
        개인_프레스를_저장한다(jdbc, 다른_기기.getId(), 오늘(), EmotionState.ANGRY, 5);

        // when
        EmotionPressTotalResult 결과 = emotionPressQueryService.findDailyTotal(0);

        // then
        assertThat(결과.pressDate()).isEqualTo(오늘());
        assertThat(결과.total()).isEqualTo(12);
    }

    @Test
    void 전체_총합도_어제_기록을_섞지_않는다() {
        // given
        Device 기기 = 기기를_저장한다();
        개인_프레스를_저장한다(jdbc, 기기.getId(), 오늘().minusDays(1), EmotionState.ANGRY, 9);
        개인_프레스를_저장한다(jdbc, 기기.getId(), 오늘(), EmotionState.ANGRY, 2);

        // when
        EmotionPressTotalResult 오늘_결과 = emotionPressQueryService.findDailyTotal(0);
        EmotionPressTotalResult 어제_결과 = emotionPressQueryService.findDailyTotal(1);

        // then
        assertThat(오늘_결과.total()).isEqualTo(2);
        assertThat(어제_결과.total()).isEqualTo(9);
    }

    @Test
    void 아무도_누르지_않은_날의_전체_총합은_영이다() {
        // when
        EmotionPressTotalResult 결과 = emotionPressQueryService.findDailyTotal(0);

        // then
        assertThat(결과.total()).isZero();
    }

    private LocalDate 오늘() {
        return LocalDate.ofInstant(기준_시각, ZoneId.of("Asia/Seoul"));
    }

    private Device 기기를_저장한다() {
        return deviceRepository.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
    }
}
