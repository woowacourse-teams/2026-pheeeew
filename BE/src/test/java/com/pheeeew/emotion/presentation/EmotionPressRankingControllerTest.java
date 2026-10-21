package com.pheeeew.emotion.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.appversion.infra.metrics.AppVersionMetricsFilter;
import com.pheeeew.auth.fixture.AccessTokenFixture;
import com.pheeeew.auth.infra.security.AuthenticationErrorHandler;
import com.pheeeew.auth.infra.security.SecurityConfig;
import com.pheeeew.auth.presentation.config.AuthWebMvcConfig;
import com.pheeeew.auth.presentation.resolver.CurrentDeviceArgumentResolver;
import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.emotion.application.dto.EmotionDailyPressRankingResult;
import com.pheeeew.emotion.application.dto.EmotionPressRankingItem;
import com.pheeeew.emotion.application.dto.EmotionPressRankingResult;
import com.pheeeew.emotion.application.dto.EmotionStatePressRankingResult;
import com.pheeeew.emotion.application.query.EmotionPressRankingService;
import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
@ImportAutoConfiguration({ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, AuthenticationErrorHandler.class, AuthWebMvcConfig.class,
        CurrentDeviceArgumentResolver.class, GlobalExceptionHandler.class})
@WebMvcTest(
        controllers = EmotionPressRankingController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AppVersionMetricsFilter.class)
)
class EmotionPressRankingControllerTest {

    private static final UUID DEVICE_PUBLIC_ID = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");
    private static final String RANKING_URI = "/api/v2/emotions/press-rankings";
    private static final String STATE_RANKING_URI = "/api/v2/emotions/press-rankings/states/ANGRY";
    private static final String DAILY_RANKING_URI = "/api/v2/emotions/press-rankings/daily";
    private static final LocalDate 조회_날짜 = LocalDate.of(2026, 10, 21);
    private static final Instant 구간_시작 = Instant.parse("2026-10-04T15:00:00Z");
    private static final Instant 구간_종료 = Instant.parse("2026-10-11T15:00:00Z");
    private static final EmotionPressRankingItem 내_항목 =
            new EmotionPressRankingItem(2, "지친다람쥐", 388, true);

    @Autowired
    private RestTestClient client;

    @MockitoBean
    private EmotionPressRankingService emotionPressRankingService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode("access-token")).thenReturn(AccessTokenFixture.액세스_토큰_클레임(DEVICE_PUBLIC_ID));
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid token"));
    }

    @Test
    void weeksAgo_를_생략하면_토큰의_기기로_이번_주를_조회한다() {
        // given
        when(emotionPressRankingService.findPressRanking(DEVICE_PUBLIC_ID, 0)).thenReturn(순위표(내_항목));

        // when
        RestTestClient.ResponseSpec 응답 = 조회한다(RANKING_URI, "access-token");

        // then
        응답.expectStatus().isOk()
                .expectBody()
                .jsonPath("$.weeksAgo").isEqualTo(0)
                .jsonPath("$.startAt").isEqualTo("2026-10-04T15:00:00Z")
                .jsonPath("$.endAt").isEqualTo("2026-10-11T15:00:00Z")
                .jsonPath("$.items[0].rank").isEqualTo(1)
                .jsonPath("$.me.rank").isEqualTo(2)
                .jsonPath("$.me.pressCount").isEqualTo(388);
        verify(emotionPressRankingService).findPressRanking(DEVICE_PUBLIC_ID, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "521", "abc"})
    void weeksAgo_가_범위나_형식을_벗어나면_조회하지_않는다(String weeksAgo) {
        // when
        RestTestClient.ResponseSpec 응답 = 조회한다(RANKING_URI + "?weeksAgo=" + weeksAgo, "access-token");

        // then
        응답.expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(emotionPressRankingService);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 520})
    void weeksAgo_의_경계값은_거부하지_않는다(int weeksAgo) {
        // given
        when(emotionPressRankingService.findPressRanking(DEVICE_PUBLIC_ID, weeksAgo)).thenReturn(순위표(내_항목));

        // when
        RestTestClient.ResponseSpec 응답 = 조회한다(RANKING_URI + "?weeksAgo=" + weeksAgo, "access-token");

        // then
        응답.expectStatus().isOk();
        verify(emotionPressRankingService).findPressRanking(DEVICE_PUBLIC_ID, weeksAgo);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"invalid-token"})
    void 인증이_없거나_유효하지_않으면_403이_아니라_401이다(String token) {
        // when
        RestTestClient.ResponseSpec 응답 = 조회한다(RANKING_URI, token);

        // then
        응답.expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTH-001");
        verifyNoInteractions(emotionPressRankingService);
    }

    @Test
    void 순위_항목에_기기_식별자와_mine_을_담지_않는다() {
        // given
        when(emotionPressRankingService.findPressRanking(DEVICE_PUBLIC_ID, 0)).thenReturn(순위표(내_항목));

        // when
        String 본문 = 본문을_받는다(RANKING_URI);

        // then
        assertThat(본문)
                .contains("\"items\":[{\"rank\":1,\"nickname\":\"한숨많은곰\",\"pressCount\":412}]")
                .contains("\"me\":{\"rank\":2,\"nickname\":\"지친다람쥐\",\"pressCount\":388}")
                .doesNotContain("mine")
                .doesNotContain(DEVICE_PUBLIC_ID.toString());
    }

    @Test
    void me_가_없으면_본문에서_사라지지_않고_null_로_실린다() {
        // given
        when(emotionPressRankingService.findPressRanking(DEVICE_PUBLIC_ID, 0)).thenReturn(순위표(null));

        // when
        String 본문 = 본문을_받는다(RANKING_URI);

        // then
        assertThat(본문).contains("\"me\":null");
    }

    @Test
    void 감정별_랭킹은_경로의_감정을_그대로_담아_돌려준다() {
        // given
        when(emotionPressRankingService.findPressRankingByState(DEVICE_PUBLIC_ID, EmotionState.ANGRY, 0))
                .thenReturn(감정별_순위표(내_항목));

        // when
        RestTestClient.ResponseSpec 응답 = 조회한다(STATE_RANKING_URI, "access-token");

        // then
        응답.expectStatus().isOk()
                .expectBody()
                .jsonPath("$.state").isEqualTo("ANGRY")
                .jsonPath("$.weeksAgo").isEqualTo(0)
                .jsonPath("$.items[0].rank").isEqualTo(1)
                .jsonPath("$.me.rank").isEqualTo(2);
        verify(emotionPressRankingService).findPressRankingByState(DEVICE_PUBLIC_ID, EmotionState.ANGRY, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HAPPY", "angry", "0"})
    void 감정_목록에_없는_state_는_조회하지_않는다(String state) {
        // when
        RestTestClient.ResponseSpec 응답 =
                조회한다("/api/v2/emotions/press-rankings/states/" + state, "access-token");

        // then
        응답.expectStatus().isBadRequest();
        verifyNoInteractions(emotionPressRankingService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "521", "abc"})
    void 감정별_랭킹도_weeksAgo_가_범위를_벗어나면_조회하지_않는다(String weeksAgo) {
        // when
        RestTestClient.ResponseSpec 응답 =
                조회한다(STATE_RANKING_URI + "?weeksAgo=" + weeksAgo, "access-token");

        // then
        응답.expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(emotionPressRankingService);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"invalid-token"})
    void 감정별_랭킹도_인증이_없으면_403이_아니라_401이다(String token) {
        // when
        RestTestClient.ResponseSpec 응답 = 조회한다(STATE_RANKING_URI, token);

        // then
        응답.expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTH-001");
        verifyNoInteractions(emotionPressRankingService);
    }

    @Test
    void 감정별_랭킹도_기기_식별자와_mine_을_담지_않고_빈_me_는_null_로_실린다() {
        // given
        when(emotionPressRankingService.findPressRankingByState(DEVICE_PUBLIC_ID, EmotionState.ANGRY, 0))
                .thenReturn(감정별_순위표(null));

        // when
        String 본문 = 본문을_받는다(STATE_RANKING_URI);

        // then
        assertThat(본문)
                .contains("\"state\":\"ANGRY\"")
                .contains("\"items\":[{\"rank\":1,\"nickname\":\"한숨많은곰\",\"pressCount\":412}]")
                .contains("\"me\":null")
                .doesNotContain("mine")
                .doesNotContain(DEVICE_PUBLIC_ID.toString());
    }

    private String 본문을_받는다(String uri) {
        return 조회한다(uri, "access-token")
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }

    private RestTestClient.ResponseSpec 조회한다(String uri, String token) {
        RestTestClient.RequestHeadersSpec<?> 요청 = client.get().uri(uri);
        if (token != null) {
            요청.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        return 요청.exchange();
    }

    @Test
    void date_로_그_날짜의_순위를_조회한다() {
        // given
        when(emotionPressRankingService.findDailyPressRanking(DEVICE_PUBLIC_ID, 조회_날짜))
                .thenReturn(일별_순위표(내_항목));

        // when
        RestTestClient.ResponseSpec 응답 = 조회한다(DAILY_RANKING_URI + "?date=2026-10-21", "access-token");

        // then
        응답.expectStatus().isOk()
                .expectBody()
                .jsonPath("$.date").isEqualTo("2026-10-21")
                .jsonPath("$.startAt").isEqualTo("2026-10-20T15:00:00Z")
                .jsonPath("$.endAt").isEqualTo("2026-10-21T15:00:00Z")
                .jsonPath("$.items[0].rank").isEqualTo(1)
                .jsonPath("$.items[0].mine").doesNotExist()
                .jsonPath("$.me.rank").isEqualTo(2)
                .jsonPath("$.me.pressCount").isEqualTo(388);
        verify(emotionPressRankingService).findDailyPressRanking(DEVICE_PUBLIC_ID, 조회_날짜);
    }

    @Test
    void date_를_생략하면_조회하지_않는다() {
        // when
        RestTestClient.ResponseSpec 응답 = 조회한다(DAILY_RANKING_URI, "access-token");

        // then
        응답.expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(emotionPressRankingService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13-01", "20261021", "어제", "2026-10-21T00:00:00"})
    void date_형식이_올바르지_않으면_조회하지_않는다(String date) {
        // when
        RestTestClient.ResponseSpec 응답 = 조회한다(DAILY_RANKING_URI + "?date=" + date, "access-token");

        // then
        응답.expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(emotionPressRankingService);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"invalid-token"})
    void 일별_조회도_인증이_없거나_유효하지_않으면_403이_아니라_401이다(String token) {
        // when
        RestTestClient.ResponseSpec 응답 = 조회한다(DAILY_RANKING_URI + "?date=2026-10-21", token);

        // then
        응답.expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTH-001");
        verifyNoInteractions(emotionPressRankingService);
    }

    private EmotionDailyPressRankingResult 일별_순위표(EmotionPressRankingItem me) {
        return EmotionDailyPressRankingResult.of(
                조회_날짜,
                Instant.parse("2026-10-20T15:00:00Z"),
                Instant.parse("2026-10-21T15:00:00Z"),
                List.of(new EmotionPressRankingItem(1, "한숨많은곰", 412, false)),
                me
        );
    }

    private EmotionStatePressRankingResult 감정별_순위표(EmotionPressRankingItem me) {
        return EmotionStatePressRankingResult.of(
                EmotionState.ANGRY,
                0,
                구간_시작,
                구간_종료,
                List.of(new EmotionPressRankingItem(1, "한숨많은곰", 412, false)),
                me
        );
    }

    private EmotionPressRankingResult 순위표(EmotionPressRankingItem me) {
        return EmotionPressRankingResult.of(
                0,
                구간_시작,
                구간_종료,
                List.of(new EmotionPressRankingItem(1, "한숨많은곰", 412, false)),
                me
        );
    }
}
