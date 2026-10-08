package com.pheeeew.emotion.presentation;

import static org.mockito.ArgumentMatchers.any;
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
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.command.EmotionPressService;
import com.pheeeew.emotion.application.dto.EmotionPressDailyResult;
import com.pheeeew.emotion.application.dto.EmotionPressResult;
import com.pheeeew.emotion.application.dto.EmotionPressTotalResult;
import com.pheeeew.emotion.application.query.EmotionPressQueryService;
import com.pheeeew.emotion.domain.EmotionState;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
@ImportAutoConfiguration({ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, AuthenticationErrorHandler.class, AuthWebMvcConfig.class,
        CurrentDeviceArgumentResolver.class, GlobalExceptionHandler.class})
@WebMvcTest(
        controllers = EmotionPressController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AppVersionMetricsFilter.class)
)
class EmotionPressControllerTest {

    private static final UUID DEVICE_PUBLIC_ID = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");
    private static final String PRESS_URI = "/api/v2/emotions/presses";
    private static final String 정상_본문 = """
            {"counts":{"ANGRY":9,"EXHAUSTED":3}}
            """;

    @Autowired
    private RestTestClient client;

    @MockitoBean
    private EmotionPressService emotionPressService;

    @MockitoBean
    private EmotionPressQueryService emotionPressQueryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode("access-token")).thenReturn(AccessTokenFixture.액세스_토큰_클레임(DEVICE_PUBLIC_ID));
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid token"));
    }

    @Test
    void 감정별_횟수를_서비스로_넘기고_오늘_집계를_반환한다() {
        // given
        when(emotionPressService.press(DEVICE_PUBLIC_ID, Map.of(EmotionState.ANGRY, 9, EmotionState.EXHAUSTED, 3)))
                .thenReturn(집계(9, 3));

        // when
        RestTestClient.ResponseSpec 응답 = 누른다(정상_본문, "access-token");

        // then
        응답.expectStatus().isOk()
                .expectBody()
                .jsonPath("$.regionCode").doesNotExist()
                .jsonPath("$.total").isEqualTo(12)
                .jsonPath("$.counts.ANGRY").isEqualTo(9)
                .jsonPath("$.counts.EXHAUSTED").isEqualTo(3)
                .jsonPath("$.counts.FRUSTRATED").isEqualTo(0)
                .jsonPath("$.counts.IRRITATED").isEqualTo(0)
                .jsonPath("$.counts.DISCOURAGED").isEqualTo(0);
        verify(emotionPressService).press(DEVICE_PUBLIC_ID, Map.of(EmotionState.ANGRY, 9, EmotionState.EXHAUSTED, 3));
    }

    @Test
    void 빈_감정_목록도_거절하지_않고_오늘_집계를_반환한다() {
        // given
        when(emotionPressService.press(DEVICE_PUBLIC_ID, Map.of()))
                .thenReturn(집계(0, 0));

        // when
        RestTestClient.ResponseSpec 응답 = 누른다("""
                {"counts":{}}
                """, "access-token");

        // then
        응답.expectStatus().isOk().expectBody().jsonPath("$.total").isEqualTo(0);
        verify(emotionPressService).press(DEVICE_PUBLIC_ID, Map.of());
    }

    @Test
    void 값이_영인_감정도_거절하지_않고_영이_그대로_서비스로_간다() {
        // given
        when(emotionPressService.press(DEVICE_PUBLIC_ID, Map.of(EmotionState.ANGRY, 0)))
                .thenReturn(집계(7, 0));

        // when
        RestTestClient.ResponseSpec 응답 = 누른다("""
                {"counts":{"ANGRY":0}}
                """, "access-token");

        // then
        응답.expectStatus().isOk()
                .expectBody()
                .jsonPath("$.counts.ANGRY").isEqualTo(7)
                .jsonPath("$.total").isEqualTo(7);
        verify(emotionPressService).press(DEVICE_PUBLIC_ID, Map.of(EmotionState.ANGRY, 0));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"invalid-token"})
    void 인증이_없거나_유효하지_않으면_403이_아니라_401이다(String token) {
        // when
        RestTestClient.ResponseSpec 응답 = 누른다(정상_본문, token);

        // then
        응답.expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("AUTH-001");
        verifyNoInteractions(emotionPressService);
    }

    @ParameterizedTest
    @MethodSource("잘못된_본문들")
    void 감정별_횟수가_올바르지_않으면_누르지_않는다(String 본문) {
        // when
        RestTestClient.ResponseSpec 응답 = 누른다(본문, "access-token");

        // then
        응답.expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(emotionPressService);
    }

    @Test
    void 서비스가_던진_실패_상태와_코드를_그대로_전달한다() {
        // given
        when(emotionPressService.press(any(), any()))
                .thenThrow(new DeviceException(DeviceErrorCode.DEVICE_NOT_FOUND));

        // when
        RestTestClient.ResponseSpec 응답 = 누른다(정상_본문, "access-token");

        // then
        응답.expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("DEVICE-004");
    }

    @Test
    void 내_집계는_기본으로_오늘을_조회한다() {
        // given
        when(emotionPressQueryService.findMyDailyPresses(DEVICE_PUBLIC_ID, 0))
                .thenReturn(일별_집계(LocalDate.of(2026, 10, 8), 9, 3));

        // when
        RestTestClient.ResponseSpec 응답 = 조회한다(PRESS_URI + "/me", "access-token");

        // then
        응답.expectStatus().isOk()
                .expectBody()
                .jsonPath("$.pressDate").isEqualTo("2026-10-08")
                .jsonPath("$.counts.ANGRY").isEqualTo(9)
                .jsonPath("$.counts.EXHAUSTED").isEqualTo(3)
                .jsonPath("$.counts.FRUSTRATED").isEqualTo(0)
                .jsonPath("$.total").isEqualTo(12);
        verify(emotionPressQueryService).findMyDailyPresses(DEVICE_PUBLIC_ID, 0);
    }

    @Test
    void 내_집계는_며칠_전인지_골라_조회한다() {
        // given
        when(emotionPressQueryService.findMyDailyPresses(DEVICE_PUBLIC_ID, 3))
                .thenReturn(일별_집계(LocalDate.of(2026, 10, 5), 1, 0));

        // when
        RestTestClient.ResponseSpec 응답 = 조회한다(PRESS_URI + "/me?daysAgo=3", "access-token");

        // then
        응답.expectStatus().isOk();
        verify(emotionPressQueryService).findMyDailyPresses(DEVICE_PUBLIC_ID, 3);
    }

    @Test
    void 전체_총합은_총합만_돌려준다() {
        // given
        when(emotionPressQueryService.findDailyTotal(0))
                .thenReturn(EmotionPressTotalResult.of(LocalDate.of(2026, 10, 8), 48213));

        // when
        RestTestClient.ResponseSpec 응답 = 조회한다(PRESS_URI + "/total", "access-token");

        // then
        응답.expectStatus().isOk()
                .expectBody()
                .jsonPath("$.pressDate").isEqualTo("2026-10-08")
                .jsonPath("$.total").isEqualTo(48213)
                .jsonPath("$.counts").doesNotExist();
        verify(emotionPressQueryService).findDailyTotal(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/me?daysAgo=-1", "/me?daysAgo=366", "/total?daysAgo=-1", "/total?daysAgo=366"})
    void 며칠_전인지가_범위를_벗어나면_조회하지_않는다(String 경로) {
        // when
        RestTestClient.ResponseSpec 응답 = 조회한다(PRESS_URI + 경로, "access-token");

        // then
        응답.expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(emotionPressQueryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/me", "/total"})
    void 인증_없이_조회하면_거부한다(String 경로) {
        // when
        RestTestClient.ResponseSpec 응답 = 조회한다(PRESS_URI + 경로, null);

        // then
        응답.expectStatus().isUnauthorized();
        verifyNoInteractions(emotionPressQueryService);
    }

    private RestTestClient.ResponseSpec 조회한다(String uri, String token) {
        RestTestClient.RequestHeadersSpec<?> 요청 = client.get().uri(uri);
        if (token != null) {
            요청.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        return 요청.exchange();
    }

    private EmotionPressDailyResult 일별_집계(LocalDate pressDate, long angry, long exhausted) {
        Map<EmotionState, Long> counts = new EnumMap<>(EmotionState.class);
        for (EmotionState state : EmotionState.values()) {
            counts.put(state, 0L);
        }
        counts.put(EmotionState.ANGRY, angry);
        counts.put(EmotionState.EXHAUSTED, exhausted);

        return EmotionPressDailyResult.of(pressDate, counts);
    }

    private RestTestClient.ResponseSpec 누른다(String 본문, String token) {
        RestTestClient.RequestBodySpec 요청 = client.post().uri(PRESS_URI);
        if (token != null) {
            요청.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        return 요청.contentType(MediaType.APPLICATION_JSON).body(본문).exchange();
    }

    private EmotionPressResult 집계(long angry, long exhausted) {
        Map<EmotionState, Long> counts = new EnumMap<>(EmotionState.class);
        for (EmotionState state : EmotionState.values()) {
            counts.put(state, 0L);
        }
        counts.put(EmotionState.ANGRY, angry);
        counts.put(EmotionState.EXHAUSTED, exhausted);

        return EmotionPressResult.from(counts);
    }

    private static Stream<String> 잘못된_본문들() {
        return Stream.of(
                """
                {}
                """,
                """
                {"counts":null}
                """,
                """
                {"counts":{"ANGRY":-1}}
                """,
                """
                {"counts":{"ANGRY":null}}
                """,
                """
                {"counts":{"HAPPY":1}}
                """
        );
    }
}
