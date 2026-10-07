package com.pheeeew.emotion.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
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
import com.pheeeew.emotion.application.dto.EmotionPressResult;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.exception.EmotionErrorCode;
import com.pheeeew.emotion.exception.EmotionException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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
            {"longitude":126.9774,"latitude":37.5669,"counts":{"ANGRY":9,"EXHAUSTED":3}}
            """;

    @Autowired
    private RestTestClient client;

    @MockitoBean
    private EmotionPressService emotionPressService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode("access-token")).thenReturn(AccessTokenFixture.액세스_토큰_클레임(DEVICE_PUBLIC_ID));
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid token"));
    }

    @Test
    void 좌표와_감정별_횟수를_서비스로_넘기고_오늘_집계를_반환한다() {
        // given
        when(emotionPressService.press(DEVICE_PUBLIC_ID, 126.9774, 37.5669,
                Map.of(EmotionState.ANGRY, 9, EmotionState.EXHAUSTED, 3)))
                .thenReturn(집계("11010530", 9, 3));

        // when
        RestTestClient.ResponseSpec 응답 = 누른다(정상_본문, "access-token");

        // then
        응답.expectStatus().isOk()
                .expectBody()
                .jsonPath("$.regionCode").isEqualTo("11010530")
                .jsonPath("$.total").isEqualTo(12)
                .jsonPath("$.counts.ANGRY").isEqualTo(9)
                .jsonPath("$.counts.EXHAUSTED").isEqualTo(3)
                .jsonPath("$.counts.FRUSTRATED").isEqualTo(0)
                .jsonPath("$.counts.IRRITATED").isEqualTo(0)
                .jsonPath("$.counts.DISCOURAGED").isEqualTo(0);
        verify(emotionPressService).press(DEVICE_PUBLIC_ID, 126.9774, 37.5669,
                Map.of(EmotionState.ANGRY, 9, EmotionState.EXHAUSTED, 3));
    }

    @Test
    void 빈_감정_목록도_거절하지_않고_오늘_집계를_반환한다() {
        // given
        when(emotionPressService.press(DEVICE_PUBLIC_ID, 126.9774, 37.5669, Map.of()))
                .thenReturn(집계("11010530", 0, 0));

        // when
        RestTestClient.ResponseSpec 응답 = 누른다("""
                {"longitude":126.9774,"latitude":37.5669,"counts":{}}
                """, "access-token");

        // then
        응답.expectStatus().isOk().expectBody().jsonPath("$.total").isEqualTo(0);
        verify(emotionPressService).press(DEVICE_PUBLIC_ID, 126.9774, 37.5669, Map.of());
    }

    @Test
    void 값이_영인_감정도_거절하지_않고_영이_그대로_서비스로_간다() {
        // given
        when(emotionPressService.press(DEVICE_PUBLIC_ID, 126.9774, 37.5669, Map.of(EmotionState.ANGRY, 0)))
                .thenReturn(집계("11010530", 7, 0));

        // when
        RestTestClient.ResponseSpec 응답 = 누른다("""
                {"longitude":126.9774,"latitude":37.5669,"counts":{"ANGRY":0}}
                """, "access-token");

        // then
        응답.expectStatus().isOk()
                .expectBody()
                .jsonPath("$.counts.ANGRY").isEqualTo(7)
                .jsonPath("$.total").isEqualTo(7);
        verify(emotionPressService).press(DEVICE_PUBLIC_ID, 126.9774, 37.5669, Map.of(EmotionState.ANGRY, 0));
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
    void 좌표와_감정별_횟수가_올바르지_않으면_누르지_않는다(String 본문) {
        // when
        RestTestClient.ResponseSpec 응답 = 누른다(본문, "access-token");

        // then
        응답.expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(emotionPressService);
    }

    @ParameterizedTest
    @MethodSource("서비스_실패들")
    void 서비스가_던진_실패_상태와_코드를_그대로_전달한다(RuntimeException 예외, int 상태, String 코드) {
        // given
        when(emotionPressService.press(any(), anyDouble(), anyDouble(), any())).thenThrow(예외);

        // when
        RestTestClient.ResponseSpec 응답 = 누른다(정상_본문, "access-token");

        // then
        응답.expectStatus().isEqualTo(상태).expectBody().jsonPath("$.code").isEqualTo(코드);
    }

    private RestTestClient.ResponseSpec 누른다(String 본문, String token) {
        RestTestClient.RequestBodySpec 요청 = client.post().uri(PRESS_URI);
        if (token != null) {
            요청.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        return 요청.contentType(MediaType.APPLICATION_JSON).body(본문).exchange();
    }

    private EmotionPressResult 집계(String regionCode, long angry, long exhausted) {
        Map<EmotionState, Long> counts = new EnumMap<>(EmotionState.class);
        for (EmotionState state : EmotionState.values()) {
            counts.put(state, 0L);
        }
        counts.put(EmotionState.ANGRY, angry);
        counts.put(EmotionState.EXHAUSTED, exhausted);

        return EmotionPressResult.of(regionCode, counts);
    }

    private static Stream<String> 잘못된_본문들() {
        return Stream.of(
                """
                {"latitude":37.5669,"counts":{"ANGRY":1}}
                """,
                """
                {"longitude":null,"latitude":37.5669,"counts":{"ANGRY":1}}
                """,
                """
                {"longitude":181,"latitude":37.5669,"counts":{"ANGRY":1}}
                """,
                """
                {"longitude":126.9774,"latitude":91,"counts":{"ANGRY":1}}
                """,
                """
                {"longitude":126.9774,"latitude":37.5669}
                """,
                """
                {"longitude":126.9774,"latitude":37.5669,"counts":null}
                """,
                """
                {"longitude":126.9774,"latitude":37.5669,"counts":{"ANGRY":-1}}
                """,
                """
                {"longitude":126.9774,"latitude":37.5669,"counts":{"ANGRY":null}}
                """,
                """
                {"longitude":126.9774,"latitude":37.5669,"counts":{"HAPPY":1}}
                """
        );
    }

    private static Stream<Arguments> 서비스_실패들() {
        return Stream.of(
                Arguments.of(new EmotionException(EmotionErrorCode.EMOTION_LOCATION_OUT_OF_SERVICE_AREA),
                        400, "EMOTION-014"),
                Arguments.of(new EmotionException(EmotionErrorCode.EMOTION_REGION_DATA_UNAVAILABLE),
                        503, "EMOTION-013"),
                Arguments.of(new DeviceException(DeviceErrorCode.DEVICE_NOT_FOUND), 401, "DEVICE-004")
        );
    }
}
