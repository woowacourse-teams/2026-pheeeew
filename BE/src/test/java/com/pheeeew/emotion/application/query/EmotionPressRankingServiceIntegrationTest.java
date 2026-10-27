package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.DeviceDailyPressFixture.개인_프레스를_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import com.pheeeew.common.application.RankingDay;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.dto.EmotionDailyPressRankingItem;
import com.pheeeew.emotion.application.dto.EmotionDailyPressRankingResult;
import com.pheeeew.emotion.application.dto.EmotionPressRankingItem;
import com.pheeeew.emotion.application.dto.EmotionPressRankingResult;
import com.pheeeew.emotion.application.dto.EmotionStatePressRankingResult;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.groups.application.RankingWeek;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmotionPressRankingServiceIntegrationTest {

    private static final Instant 기준_시각 = Instant.parse("2026-10-08T03:00:00Z");
    private static final LocalDate 직전_일요일 = LocalDate.of(2026, 10, 4);
    private static final LocalDate 주_시작_월요일 = LocalDate.of(2026, 10, 5);
    private static final LocalDate 주_마지막_일요일 = LocalDate.of(2026, 10, 11);
    private static final LocalDate 다음_주_월요일 = LocalDate.of(2026, 10, 12);
    private static final LocalDate 기준_날짜 = LocalDate.of(2026, 10, 8);
    private static final int 상위_인원 = 100;

    @MockitoBean
    private Clock clock;

    @Autowired
    private EmotionPressRankingService emotionPressRankingService;

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
    void 여러_기기의_프레스를_전국_하나의_순위표로_합산한다() {
        // given
        Device 곰 = 기기와_프레스를_저장한다("한숨많은곰", 7);
        기기와_프레스를_저장한다("지친다람쥐", 3);

        // when
        EmotionPressRankingResult 결과 = emotionPressRankingService.findPressRanking(곰.getPublicId(), 0);

        // then
        assertThat(결과.items())
                .extracting(
                        EmotionPressRankingItem::rank,
                        EmotionPressRankingItem::nickname,
                        EmotionPressRankingItem::pressCount
                )
                .containsExactly(tuple(1, "한숨많은곰", 7L), tuple(2, "지친다람쥐", 3L));
    }

    @Test
    void 같은_기기가_다른_감정을_눌러도_한_항목으로_합친다() {
        // given
        Device 기기 = 기기를_저장한다("한숨많은곰");
        개인_프레스를_저장한다(jdbc, 기기.getId(), 주_시작_월요일, EmotionState.ANGRY, 10);
        개인_프레스를_저장한다(jdbc, 기기.getId(), 주_시작_월요일, EmotionState.EXHAUSTED, 5);
        개인_프레스를_저장한다(jdbc, 기기.getId(), 주_마지막_일요일, EmotionState.IRRITATED, 2);

        // when
        EmotionPressRankingResult 결과 = emotionPressRankingService.findPressRanking(기기.getPublicId(), 0);

        // then
        assertThat(결과.items())
                .extracting(EmotionPressRankingItem::pressCount)
                .containsExactly(17L);
    }

    @Test
    void 주_시작일_기록은_세고_직전_일요일과_종료일_기록은_세지_않는다() {
        // given
        Device 기기 = 기기를_저장한다("한숨많은곰");
        개인_프레스를_저장한다(jdbc, 기기.getId(), 직전_일요일, EmotionState.ANGRY, 100);
        개인_프레스를_저장한다(jdbc, 기기.getId(), 주_시작_월요일, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, 기기.getId(), 주_마지막_일요일, EmotionState.ANGRY, 2);
        개인_프레스를_저장한다(jdbc, 기기.getId(), 다음_주_월요일, EmotionState.ANGRY, 400);

        // when
        EmotionPressRankingResult 결과 = emotionPressRankingService.findPressRanking(기기.getPublicId(), 0);

        // then
        assertThat(결과.me().pressCount()).isEqualTo(3);
    }

    @Test
    void 동점은_공동_순위_일_이_이_사_로_매긴다() {
        // given
        Device 일등 = 기기와_프레스를_저장한다("일등곰", 10);
        기기와_프레스를_저장한다("동점곰", 5);
        기기와_프레스를_저장한다("동점범", 5);
        기기와_프레스를_저장한다("사등곰", 1);

        // when
        EmotionPressRankingResult 결과 = emotionPressRankingService.findPressRanking(일등.getPublicId(), 0);

        // then
        assertThat(결과.items())
                .extracting(EmotionPressRankingItem::rank, EmotionPressRankingItem::pressCount)
                .containsExactly(tuple(1, 10L), tuple(2, 5L), tuple(2, 5L), tuple(4, 1L));
    }

    @Test
    void 상위_백_밖이면_전체를_매긴_내_순위를_내리고_목록은_백_개로_자른다() {
        // given
        for (int 등수 = 1; 등수 <= 상위_인원; 등수++) {
            기기와_프레스를_저장한다(null, 1000 - 등수);
        }
        Device 내_기기 = 기기와_프레스를_저장한다("꼴찌곰", 1);

        // when
        EmotionPressRankingResult 결과 = emotionPressRankingService.findPressRanking(내_기기.getPublicId(), 0);

        // then
        assertThat(결과.items()).hasSize(상위_인원);
        assertThat(결과.items().getLast().rank()).isEqualTo(상위_인원);
        assertThat(결과.items()).noneMatch(EmotionPressRankingItem::mine);
        assertThat(결과.me())
                .isEqualTo(new EmotionPressRankingItem(상위_인원 + 1, "꼴찌곰", 1, true));
    }

    @Test
    void 상위_백_안에_있어도_me_를_함께_내린다() {
        // given
        기기와_프레스를_저장한다("일등곰", 10);
        Device 내_기기 = 기기와_프레스를_저장한다("둘째곰", 5);

        // when
        EmotionPressRankingResult 결과 = emotionPressRankingService.findPressRanking(내_기기.getPublicId(), 0);

        // then
        assertThat(결과.items()).extracting(EmotionPressRankingItem::mine).containsExactly(false, true);
        assertThat(결과.me()).isEqualTo(결과.items().getLast());
        assertThat(결과.me().rank()).isEqualTo(2);
    }

    @Test
    void 그_주에_누르지_않은_기기는_me_가_null_이고_목록에도_없다() {
        // given
        기기와_프레스를_저장한다("한숨많은곰", 9);
        Device 안_누른_기기 = 기기를_저장한다("조용한곰");
        개인_프레스를_저장한다(jdbc, 안_누른_기기.getId(), 직전_일요일, EmotionState.ANGRY, 50);

        // when
        EmotionPressRankingResult 결과 = emotionPressRankingService.findPressRanking(안_누른_기기.getPublicId(), 0);

        // then
        assertThat(결과.me()).isNull();
        assertThat(결과.items())
                .extracting(EmotionPressRankingItem::nickname)
                .containsExactly("한숨많은곰");
    }

    @Test
    void 아무도_누르지_않은_주는_빈_목록과_null_me_를_돌려준다() {
        // given
        Device 기기 = 기기를_저장한다("한숨많은곰");

        // when
        EmotionPressRankingResult 결과 = emotionPressRankingService.findPressRanking(기기.getPublicId(), 0);

        // then
        assertThat(결과.items()).isEmpty();
        assertThat(결과.me()).isNull();
    }

    @Test
    void 닉네임을_정하지_않은_기기는_익명으로_나온다() {
        // given
        Device 기기 = 기기와_프레스를_저장한다(null, 4);

        // when
        EmotionPressRankingResult 결과 = emotionPressRankingService.findPressRanking(기기.getPublicId(), 0);

        // then
        assertThat(결과.items())
                .extracting(EmotionPressRankingItem::nickname)
                .containsExactly("익명");
        assertThat(결과.me().nickname()).isEqualTo("익명");
    }

    @Test
    void 지난주를_요청하면_구간과_집계가_한_주_앞으로_간다() {
        // given
        Device 이번주_기기 = 기기와_프레스를_저장한다("이번주곰", 9);
        Device 지난주_기기 = 기기를_저장한다("지난주곰");
        개인_프레스를_저장한다(jdbc, 지난주_기기.getId(), 직전_일요일, EmotionState.ANGRY, 4);

        // when
        EmotionPressRankingResult 이번주 = emotionPressRankingService.findPressRanking(이번주_기기.getPublicId(), 0);
        EmotionPressRankingResult 지난주 = emotionPressRankingService.findPressRanking(이번주_기기.getPublicId(), 1);

        // then
        assertThat(이번주.weeksAgo()).isZero();
        assertThat(이번주.startAt()).isEqualTo(RankingWeek.of(기준_시각, 0).startAt());
        assertThat(이번주.endAt()).isEqualTo(RankingWeek.of(기준_시각, 0).endAt());
        assertThat(이번주.items())
                .extracting(EmotionPressRankingItem::nickname)
                .containsExactly("이번주곰");
        assertThat(지난주.weeksAgo()).isOne();
        assertThat(지난주.startAt()).isEqualTo(RankingWeek.of(기준_시각, 1).startAt());
        assertThat(지난주.endAt()).isEqualTo(RankingWeek.of(기준_시각, 1).endAt());
        assertThat(지난주.items())
                .extracting(EmotionPressRankingItem::nickname)
                .containsExactly("지난주곰");
    }

    @Test
    void 감정별_랭킹은_고른_감정만_세고_다른_감정은_섞지_않는다() {
        // given
        Device 곰 = 기기를_저장한다("한숨많은곰");
        개인_프레스를_저장한다(jdbc, 곰.getId(), 주_시작_월요일, EmotionState.ANGRY, 3);
        개인_프레스를_저장한다(jdbc, 곰.getId(), 주_시작_월요일, EmotionState.EXHAUSTED, 100);
        Device 다람쥐 = 기기를_저장한다("지친다람쥐");
        개인_프레스를_저장한다(jdbc, 다람쥐.getId(), 주_시작_월요일, EmotionState.ANGRY, 7);

        // when
        EmotionStatePressRankingResult 결과 =
                emotionPressRankingService.findPressRankingByState(곰.getPublicId(), EmotionState.ANGRY, 0);

        // then
        assertThat(결과.state()).isEqualTo(EmotionState.ANGRY);
        assertThat(결과.items())
                .extracting(
                        EmotionPressRankingItem::rank,
                        EmotionPressRankingItem::nickname,
                        EmotionPressRankingItem::pressCount
                )
                .containsExactly(tuple(1, "지친다람쥐", 7L), tuple(2, "한숨많은곰", 3L));
    }

    @Test
    void 감정별_랭킹은_같은_감정의_여러_날짜를_합산한다() {
        // given
        Device 기기 = 기기를_저장한다("한숨많은곰");
        개인_프레스를_저장한다(jdbc, 기기.getId(), 주_시작_월요일, EmotionState.ANGRY, 4);
        개인_프레스를_저장한다(jdbc, 기기.getId(), 주_마지막_일요일, EmotionState.ANGRY, 6);
        개인_프레스를_저장한다(jdbc, 기기.getId(), 직전_일요일, EmotionState.ANGRY, 100);
        개인_프레스를_저장한다(jdbc, 기기.getId(), 다음_주_월요일, EmotionState.ANGRY, 400);

        // when
        EmotionStatePressRankingResult 결과 =
                emotionPressRankingService.findPressRankingByState(기기.getPublicId(), EmotionState.ANGRY, 0);

        // then
        assertThat(결과.me().pressCount()).isEqualTo(10);
    }

    @Test
    void 고른_감정을_누르지_않았으면_다른_감정을_눌렀어도_me_가_null_이다() {
        // given
        Device 기기 = 기기를_저장한다("한숨많은곰");
        개인_프레스를_저장한다(jdbc, 기기.getId(), 주_시작_월요일, EmotionState.EXHAUSTED, 50);

        // when
        EmotionStatePressRankingResult 결과 =
                emotionPressRankingService.findPressRankingByState(기기.getPublicId(), EmotionState.ANGRY, 0);

        // then
        assertThat(결과.me()).isNull();
        assertThat(결과.items()).isEmpty();
    }

    @Test
    void 감정별_랭킹도_상위_백으로_자르고_백위_밖의_내_순위를_내린다() {
        // given
        for (int 등수 = 1; 등수 <= 상위_인원; 등수++) {
            기기와_프레스를_저장한다(null, 1000 - 등수);
        }
        Device 내_기기 = 기기와_프레스를_저장한다("꼴찌곰", 1);

        // when
        EmotionStatePressRankingResult 결과 =
                emotionPressRankingService.findPressRankingByState(내_기기.getPublicId(), EmotionState.ANGRY, 0);

        // then
        assertThat(결과.items()).hasSize(상위_인원);
        assertThat(결과.items().getLast().rank()).isEqualTo(상위_인원);
        assertThat(결과.items()).noneMatch(EmotionPressRankingItem::mine);
        assertThat(결과.me())
                .isEqualTo(new EmotionPressRankingItem(상위_인원 + 1, "꼴찌곰", 1, true));
    }

    @Test
    void 감정별_랭킹도_지난주를_요청하면_구간이_한_주_앞으로_간다() {
        // given
        Device 기기 = 기기를_저장한다("한숨많은곰");
        개인_프레스를_저장한다(jdbc, 기기.getId(), 주_시작_월요일, EmotionState.ANGRY, 9);
        개인_프레스를_저장한다(jdbc, 기기.getId(), 직전_일요일, EmotionState.ANGRY, 2);

        // when
        EmotionStatePressRankingResult 지난주 =
                emotionPressRankingService.findPressRankingByState(기기.getPublicId(), EmotionState.ANGRY, 1);

        // then
        assertThat(지난주.weeksAgo()).isOne();
        assertThat(지난주.startAt()).isEqualTo(RankingWeek.of(기준_시각, 1).startAt());
        assertThat(지난주.endAt()).isEqualTo(RankingWeek.of(기준_시각, 1).endAt());
        assertThat(지난주.me().pressCount()).isEqualTo(2);
    }

    @Test
    void 하루_단위로_전국_순위표를_만든다() {
        // given
        Device 곰 = 기기를_저장한다("한숨많은곰");
        Device 다람쥐 = 기기를_저장한다("지친다람쥐");
        개인_프레스를_저장한다(jdbc, 곰.getId(), 기준_날짜, EmotionState.ANGRY, 7);
        개인_프레스를_저장한다(jdbc, 다람쥐.getId(), 기준_날짜, EmotionState.ANGRY, 3);

        // when
        EmotionDailyPressRankingResult 결과 =
                emotionPressRankingService.findDailyPressRanking(곰.getPublicId(), 기준_날짜);

        // then
        assertThat(결과.date()).isEqualTo(기준_날짜);
        assertThat(결과.startAt()).isEqualTo(RankingDay.of(기준_날짜).startAt());
        assertThat(결과.endAt()).isEqualTo(RankingDay.of(기준_날짜).endAt());
        assertThat(결과.items())
                .extracting(
                        EmotionDailyPressRankingItem::rank,
                        EmotionDailyPressRankingItem::nickname,
                        EmotionDailyPressRankingItem::pressCount
                )
                .containsExactly(tuple(1, "한숨많은곰", 7L), tuple(2, "지친다람쥐", 3L));
    }

    @Test
    void 요청한_날짜의_기록만_세고_전날과_다음날은_세지_않는다() {
        // given
        Device 기기 = 기기를_저장한다("한숨많은곰");
        개인_프레스를_저장한다(jdbc, 기기.getId(), 기준_날짜.minusDays(1), EmotionState.ANGRY, 100);
        개인_프레스를_저장한다(jdbc, 기기.getId(), 기준_날짜, EmotionState.IRRITATED, 7);
        개인_프레스를_저장한다(jdbc, 기기.getId(), 기준_날짜.plusDays(1), EmotionState.ANGRY, 100);

        // when
        EmotionDailyPressRankingResult 결과 =
                emotionPressRankingService.findDailyPressRanking(기기.getPublicId(), 기준_날짜);

        // then
        assertThat(결과.me().pressCount()).isEqualTo(7);
        assertThat(결과.me().representativeState()).isEqualTo(EmotionState.IRRITATED);
    }

    @Test
    void 기록이_없는_날짜는_빈_목록과_null_me_를_돌려준다() {
        // given
        Device 기기 = 기기를_저장한다("한숨많은곰");
        개인_프레스를_저장한다(jdbc, 기기.getId(), 기준_날짜, EmotionState.ANGRY, 7);

        // when
        EmotionDailyPressRankingResult 결과 =
                emotionPressRankingService.findDailyPressRanking(기기.getPublicId(), 기준_날짜.minusDays(3));

        // then
        assertThat(결과.items()).isEmpty();
        assertThat(결과.me()).isNull();
    }

    @Test
    void 미래_날짜도_오류가_아니라_빈_결과다() {
        // given
        Device 기기 = 기기를_저장한다("한숨많은곰");
        LocalDate 먼_미래 = LocalDate.of(2099, 1, 1);

        // when
        EmotionDailyPressRankingResult 결과 =
                emotionPressRankingService.findDailyPressRanking(기기.getPublicId(), 먼_미래);

        // then
        assertThat(결과.date()).isEqualTo(먼_미래);
        assertThat(결과.items()).isEmpty();
        assertThat(결과.me()).isNull();
    }

    @Test
    void 서버의_오늘과_무관하게_요청한_날짜를_집계한다() {
        // given
        Device 기기 = 기기를_저장한다("한숨많은곰");
        개인_프레스를_저장한다(jdbc, 기기.getId(), 기준_날짜, EmotionState.ANGRY, 7);
        개인_프레스를_저장한다(jdbc, 기기.getId(), 주_시작_월요일, EmotionState.ANGRY, 4);

        // when
        EmotionDailyPressRankingResult 결과 =
                emotionPressRankingService.findDailyPressRanking(기기.getPublicId(), 주_시작_월요일);

        // then
        assertThat(결과.date()).isEqualTo(주_시작_월요일);
        assertThat(결과.me().pressCount()).isEqualTo(4);
    }

    @Test
    void 개인별_최다_감정을_합계와_함께_items와_me에_반환한다() {
        // given
        Device 내_기기 = 기기를_저장한다("가나다곰");
        Device 다른_기기 = 기기를_저장한다("나다라곰");
        개인_프레스를_저장한다(jdbc, 내_기기.getId(), 기준_날짜, EmotionState.IRRITATED, 70);
        개인_프레스를_저장한다(jdbc, 내_기기.getId(), 기준_날짜, EmotionState.EXHAUSTED, 30);
        개인_프레스를_저장한다(jdbc, 다른_기기.getId(), 기준_날짜, EmotionState.DISCOURAGED, 90);
        개인_프레스를_저장한다(jdbc, 다른_기기.getId(), 기준_날짜, EmotionState.ANGRY, 10);

        // when
        EmotionDailyPressRankingResult 결과 =
                emotionPressRankingService.findDailyPressRanking(내_기기.getPublicId(), 기준_날짜);

        // then
        assertThat(결과.items())
                .extracting(EmotionDailyPressRankingItem::rank, EmotionDailyPressRankingItem::pressCount,
                        EmotionDailyPressRankingItem::representativeState)
                .containsExactly(tuple(1, 100L, EmotionState.IRRITATED), tuple(1, 100L, EmotionState.DISCOURAGED));
        assertThat(결과.me()).isEqualTo(결과.items().getFirst());
    }

    @ParameterizedTest
    @CsvSource({"ANGRY,DISCOURAGED", "DISCOURAGED,EXHAUSTED", "EXHAUSTED,FRUSTRATED", "FRUSTRATED,IRRITATED"})
    void 공동_최다는_정해진_감정_우선순위를_따른다(EmotionState 우선_감정, EmotionState 다음_감정) {
        // given
        Device 기기 = 기기를_저장한다("공동최다곰");
        개인_프레스를_저장한다(jdbc, 기기.getId(), 기준_날짜, 다음_감정, 10);
        개인_프레스를_저장한다(jdbc, 기기.getId(), 기준_날짜, 우선_감정, 10);

        // when
        EmotionDailyPressRankingResult 결과 =
                emotionPressRankingService.findDailyPressRanking(기기.getPublicId(), 기준_날짜);

        // then
        assertThat(결과.me().pressCount()).isEqualTo(20);
        assertThat(결과.me().representativeState()).isEqualTo(우선_감정);
    }

    @Test
    void 일일_백위_밖_me에도_대표_감정을_반환한다() {
        // given
        for (int 등수 = 1; 등수 <= 상위_인원; 등수++) {
            Device 기기 = 기기를_저장한다(null);
            개인_프레스를_저장한다(jdbc, 기기.getId(), 기준_날짜, EmotionState.ANGRY, 1000 - 등수);
        }
        Device 내_기기 = 기기를_저장한다("꼴찌곰");
        개인_프레스를_저장한다(jdbc, 내_기기.getId(), 기준_날짜, EmotionState.EXHAUSTED, 1);

        // when
        EmotionDailyPressRankingResult 결과 =
                emotionPressRankingService.findDailyPressRanking(내_기기.getPublicId(), 기준_날짜);

        // then
        assertThat(결과.items()).hasSize(상위_인원);
        assertThat(결과.items().getLast().rank()).isEqualTo(100);
        assertThat(결과.items()).allSatisfy(item -> {
            assertThat(item.nickname()).isEqualTo("익명");
            assertThat(item.representativeState()).isEqualTo(EmotionState.ANGRY);
            assertThat(item.mine()).isFalse();
        });
        assertThat(결과.me().rank()).isEqualTo(101);
        assertThat(결과.me().pressCount()).isEqualTo(1);
        assertThat(결과.me().representativeState()).isEqualTo(EmotionState.EXHAUSTED);
    }

    @Test
    void 다른_사람의_일일_기록이_있어도_누르지_않은_나는_null이다() {
        // given
        Device 내_기기 = 기기를_저장한다("조용한곰");
        Device 다른_기기 = 기기를_저장한다("누른곰");
        개인_프레스를_저장한다(jdbc, 다른_기기.getId(), 기준_날짜, EmotionState.FRUSTRATED, 5);
        개인_프레스를_저장한다(jdbc, 내_기기.getId(), 기준_날짜.minusDays(1), EmotionState.ANGRY, 100);

        // when
        EmotionDailyPressRankingResult 결과 =
                emotionPressRankingService.findDailyPressRanking(내_기기.getPublicId(), 기준_날짜);

        // then
        assertThat(결과.items()).hasSize(1);
        assertThat(결과.items().getFirst().representativeState()).isEqualTo(EmotionState.FRUSTRATED);
        assertThat(결과.me()).isNull();
    }

    private Device 기기를_저장한다(String 닉네임) {
        return deviceRepository.saveAndFlush(기본_기기_빌더().nickname(닉네임).build());
    }

    private Device 기기와_프레스를_저장한다(String 닉네임, long 횟수) {
        Device 기기 = 기기를_저장한다(닉네임);
        개인_프레스를_저장한다(jdbc, 기기.getId(), 주_시작_월요일, EmotionState.ANGRY, 횟수);

        return 기기;
    }
}
