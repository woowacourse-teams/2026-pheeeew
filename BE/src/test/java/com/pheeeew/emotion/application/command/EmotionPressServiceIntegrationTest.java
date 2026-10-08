package com.pheeeew.emotion.application.command;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.dto.EmotionPressDailyResult;
import com.pheeeew.emotion.application.dto.EmotionPressResult;
import com.pheeeew.emotion.application.query.EmotionPressQueryService;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
class EmotionPressServiceIntegrationTest {

    private static final Instant 기준_시각 = Instant.parse("2026-10-06T03:00:00Z");
    private static final int 동시_요청_수 = 6;
    private static final int 데드락_회전_수 = 30;
    private static final int 정합성_회전_수 = 10;
    private static final String 자른_양_지표 = "pheeeew.emotion.press.clamped";

    @MockitoBean
    private Clock clock;

    @Autowired
    private EmotionPressService emotionPressService;

    @Autowired
    private EmotionPressQueryService emotionPressQueryService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MeterRegistry meterRegistry;

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
    void 같은_기기가_같은_날_다시_누르면_행_하나에_누적되고_응답은_오늘_전체_집계다() {
        // given
        Device 기기 = 기기를_저장한다();
        누른다(기기, Map.of(EmotionState.ANGRY, 2));

        // when
        EmotionPressResult 결과 = 누른다(기기, Map.of(EmotionState.ANGRY, 3));

        // then
        assertThat(결과.counts().get(EmotionState.ANGRY)).isEqualTo(5);
        assertThat(결과.total()).isEqualTo(5);
        assertThat(행_수()).isOne();
    }

    @Test
    void 누른_직후_응답은_내_일별_집계_조회와_같은_값이다() {
        // given
        Device 기기 = 기기를_저장한다();
        누른다(기기, Map.of(EmotionState.ANGRY, 2));

        // when
        EmotionPressResult 누른_결과 = 누른다(기기, Map.of(EmotionState.ANGRY, 1, EmotionState.EXHAUSTED, 3));
        EmotionPressDailyResult 조회_결과 = emotionPressQueryService.findMyDailyPresses(기기.getPublicId(), 0);

        // then
        assertThat(누른_결과.counts()).isEqualTo(조회_결과.counts());
        assertThat(누른_결과.total()).isEqualTo(조회_결과.total());
        assertThat(누른_결과.counts()).containsOnly(
                Map.entry(EmotionState.ANGRY, 3L),
                Map.entry(EmotionState.EXHAUSTED, 3L),
                Map.entry(EmotionState.FRUSTRATED, 0L),
                Map.entry(EmotionState.IRRITATED, 0L),
                Map.entry(EmotionState.DISCOURAGED, 0L));
        assertThat(누른_결과.total()).isEqualTo(6);
        assertThat(조회_결과.pressDate()).isEqualTo(LocalDate.ofInstant(기준_시각, ZoneId.of("Asia/Seoul")));
    }

    @Test
    void 다른_기기들이_같은_감정을_동시에_눌러도_서로_다른_행에_정확히_집계된다() throws Exception {
        // given
        List<Device> 기기들 = new ArrayList<>();
        for (int 번호 = 0; 번호 < 동시_요청_수; 번호++) {
            기기들.add(기기를_저장한다());
        }
        long 기존_데드락_수 = 데드락_수();
        List<Callable<Boolean>> 작업들 = 기기들.stream()
                .map(기기 -> (Callable<Boolean>) () -> {
                    for (int 회전 = 0; 회전 < 정합성_회전_수; 회전++) {
                        누른다(기기, Map.of(EmotionState.ANGRY, 3));
                    }

                    return true;
                })
                .toList();

        // when
        List<Boolean> 성공_여부들 = 실행한다(작업들);

        // then
        assertThat(성공_여부들).containsOnly(true);
        assertThat(행_수()).isEqualTo(동시_요청_수);
        assertThat(전체_합()).isEqualTo((long) 동시_요청_수 * 정합성_회전_수 * 3);
        assertThat(데드락_수() - 기존_데드락_수).isZero();
        for (Device 기기 : 기기들) {
            assertThat(오늘_집계(기기).counts().get(EmotionState.ANGRY)).isEqualTo((long) 정합성_회전_수 * 3);
        }
    }

    @Test
    void 같은_기기가_서로_반대_순서의_묶음을_동시에_보내도_데드락_없이_정확히_합산된다() throws Exception {
        // given
        Device 기기 = 기기를_저장한다();
        long 기존_데드락_수 = 데드락_수();
        List<Callable<Boolean>> 작업들 = new ArrayList<>();
        for (int 번호 = 0; 번호 < 동시_요청_수; 번호++) {
            Map<EmotionState, Integer> 반대_순서 = 번호 % 2 == 0
                    ? 순서대로(Map.entry(EmotionState.ANGRY, 1), Map.entry(EmotionState.EXHAUSTED, 1))
                    : 순서대로(Map.entry(EmotionState.EXHAUSTED, 1), Map.entry(EmotionState.ANGRY, 1));
            작업들.add(() -> {
                for (int 회전 = 0; 회전 < 데드락_회전_수; 회전++) {
                    누른다(기기, 반대_순서);
                }

                return true;
            });
        }

        // when
        List<Boolean> 성공_여부들 = 실행한다(작업들);

        // then
        assertThat(성공_여부들).containsOnly(true);
        EmotionPressResult 집계 = 오늘_집계(기기);
        assertThat(집계.counts().get(EmotionState.ANGRY)).isEqualTo((long) 동시_요청_수 * 데드락_회전_수);
        assertThat(집계.counts().get(EmotionState.EXHAUSTED)).isEqualTo((long) 동시_요청_수 * 데드락_회전_수);
        assertThat(집계.total()).isEqualTo(2L * 동시_요청_수 * 데드락_회전_수);
        assertThat(행_수()).isEqualTo(2);
        assertThat(데드락_수() - 기존_데드락_수).isZero();
    }

    @Test
    void KST_자정을_넘기면_날짜가_다른_행이_생기고_응답은_그_날짜_집계만_돌려준다() {
        // given
        Device 기기 = 기기를_저장한다();
        when(clock.instant()).thenReturn(Instant.parse("2026-10-06T14:59:59Z"));
        누른다(기기, Map.of(EmotionState.ANGRY, 2));

        // when
        when(clock.instant()).thenReturn(Instant.parse("2026-10-06T15:00:00Z"));
        EmotionPressResult 결과 = 누른다(기기, Map.of(EmotionState.ANGRY, 1));

        // then
        assertThat(결과.counts().get(EmotionState.ANGRY)).isOne();
        assertThat(결과.total()).isOne();
        assertThat(행_수()).isEqualTo(2);
        assertThat(날짜들()).containsExactly(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7));
    }

    @Test
    void 감정별_상한을_넘기면_서른만_반영되고_자른_양이_지표에_오른다() {
        // given
        Device 기기 = 기기를_저장한다();
        double 기존_자른_양 = 자른_양("per_state");
        double 기존_적용_합 = 적용된_합();

        // when
        EmotionPressResult 결과 = 누른다(기기, Map.of(EmotionState.ANGRY, 45));

        // then
        assertThat(결과.counts().get(EmotionState.ANGRY)).isEqualTo(30);
        assertThat(결과.total()).isEqualTo(30);
        assertThat(자른_양("per_state") - 기존_자른_양).isEqualTo(15);
        assertThat(적용된_합() - 기존_적용_합).isEqualTo(30);
    }

    @Test
    void 전체_합_상한을_넘기면_백만_반영되고_이름이_뒤인_감정은_행이_생기지_않는다() {
        // given
        Device 기기 = 기기를_저장한다();
        double 기존_자른_양 = 자른_양("total");

        // when
        EmotionPressResult 결과 = 누른다(기기, 순서대로(
                Map.entry(EmotionState.IRRITATED, 30),
                Map.entry(EmotionState.FRUSTRATED, 30),
                Map.entry(EmotionState.EXHAUSTED, 30),
                Map.entry(EmotionState.DISCOURAGED, 30),
                Map.entry(EmotionState.ANGRY, 30)
        ));

        // then
        assertThat(결과.total()).isEqualTo(100);
        assertThat(결과.counts().get(EmotionState.FRUSTRATED)).isEqualTo(10);
        assertThat(결과.counts().get(EmotionState.IRRITATED)).isZero();
        assertThat(행_수()).isEqualTo(4);
        assertThat(자른_양("total") - 기존_자른_양).isEqualTo(50);
    }

    @Test
    void 값이_영인_감정은_행을_만들지_않고_집계를_그대로_돌려준다() {
        // given
        Device 기기 = 기기를_저장한다();
        누른다(기기, Map.of(EmotionState.ANGRY, 2));

        // when
        EmotionPressResult 결과 = 누른다(기기, Map.of(EmotionState.ANGRY, 0, EmotionState.EXHAUSTED, 0));

        // then
        assertThat(결과.counts().get(EmotionState.ANGRY)).isEqualTo(2);
        assertThat(결과.counts().get(EmotionState.EXHAUSTED)).isZero();
        assertThat(결과.total()).isEqualTo(2);
        assertThat(행_수()).isOne();
    }

    @Test
    void 유효한_증가가_없으면_행을_만들지_않고_빈_요청_지표만_오른다() {
        // given
        Device 기기 = 기기를_저장한다();
        long 기존_빈_요청_수 = 빈_요청_수();
        long 기존_적용_기록_수 = 적용_기록_수();

        // when
        EmotionPressResult 결과 = 누른다(기기, Map.of());

        // then
        assertThat(결과.counts()).hasSize(EmotionState.values().length);
        assertThat(결과.total()).isZero();
        assertThat(행_수()).isZero();
        assertThat(빈_요청_수() - 기존_빈_요청_수).isOne();
        assertThat(적용_기록_수() - 기존_적용_기록_수).isZero();
    }

    @Test
    void 토큰의_기기를_찾을_수_없으면_기기_오류다() {
        // given
        UUID 없는_기기 = UUID.randomUUID();

        // when
        Throwable 예외 = catchThrowable(
                () -> emotionPressService.press(없는_기기, Map.of(EmotionState.ANGRY, 1))
        );

        // then
        assertThat(예외).isInstanceOfSatisfying(DeviceException.class,
                오류 -> assertThat(오류.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
        assertThat(행_수()).isZero();
    }

    @Test
    void 저장된_프레스_행에는_좌표와_지역이_남지_않는다() {
        // given
        Device 기기 = 기기를_저장한다();

        // when
        누른다(기기, Map.of(EmotionState.ANGRY, 1));

        // then
        assertThat(프레스_표의_열_이름들()).containsExactly(
                "created_at", "device_id", "id", "press_count", "press_date", "state", "updated_at");
    }

    private EmotionPressResult 누른다(Device 기기, Map<EmotionState, Integer> counts) {
        return emotionPressService.press(기기.getPublicId(), counts);
    }

    private EmotionPressResult 오늘_집계(Device 기기) {
        return 누른다(기기, Map.of());
    }

    private Device 기기를_저장한다() {
        return deviceRepository.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
    }

    @SafeVarargs
    private Map<EmotionState, Integer> 순서대로(Map.Entry<EmotionState, Integer>... 항목들) {
        Map<EmotionState, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<EmotionState, Integer> 항목 : 항목들) {
            counts.put(항목.getKey(), 항목.getValue());
        }

        return counts;
    }

    private long 행_수() {
        return jdbc.sql("SELECT count(*) FROM device_daily_presses").query(Long.class).single();
    }

    private long 전체_합() {
        return jdbc.sql("SELECT COALESCE(SUM(press_count), 0) FROM device_daily_presses")
                .query(Long.class).single();
    }

    private List<LocalDate> 날짜들() {
        return jdbc.sql("SELECT DISTINCT press_date FROM device_daily_presses ORDER BY press_date")
                .query(LocalDate.class).list();
    }

    private List<String> 프레스_표의_열_이름들() {
        return jdbc.sql("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'device_daily_presses'
                ORDER BY column_name
                """).query(String.class).list();
    }

    private long 빈_요청_수() {
        return (long) meterRegistry.get("pheeeew.emotion.press.empty").counter().count();
    }

    private long 데드락_수() {
        return (long) meterRegistry.get("pheeeew.emotion.press.deadlocks").counter().count();
    }

    private long 적용_기록_수() {
        return meterRegistry.get("pheeeew.emotion.press.applied").summary().count();
    }

    private double 적용된_합() {
        return meterRegistry.get("pheeeew.emotion.press.applied").summary().totalAmount();
    }

    private double 자른_양(String 상한) {
        return meterRegistry.get(자른_양_지표).tag("limit", 상한).summary().totalAmount();
    }

    private List<Boolean> 실행한다(List<Callable<Boolean>> 작업들) throws Exception {
        CountDownLatch 준비 = new CountDownLatch(작업들.size());
        CountDownLatch 출발 = new CountDownLatch(1);
        ExecutorService 실행기 = Executors.newFixedThreadPool(작업들.size());
        try {
            List<Future<Boolean>> 미래들 = 작업들.stream()
                    .map(작업 -> 실행기.submit(() -> {
                        준비.countDown();
                        출발.await(5, TimeUnit.SECONDS);

                        return 작업.call();
                    }))
                    .toList();
            준비.await(5, TimeUnit.SECONDS);
            출발.countDown();

            List<Boolean> 성공_여부들 = new ArrayList<>();
            for (Future<Boolean> 미래 : 미래들) {
                성공_여부들.add(미래.get(30, TimeUnit.SECONDS));
            }

            return 성공_여부들;
        } finally {
            실행기.shutdownNow();
        }
    }
}
