package com.pheeeew.emotion.presentation;

import static com.pheeeew.emotion.fixture.EmotionFixture.서울시청_좌표;
import static org.mockito.Mockito.doThrow;
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
import com.pheeeew.emotion.application.emoji.dto.EmotionEmojiResult;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.query.EmotionQueryService;
import com.pheeeew.emotion.application.query.dto.EmotionDetailView;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.exception.EmotionErrorCode;
import com.pheeeew.emotion.exception.EmotionException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.util.ReflectionTestUtils;

@AutoConfigureRestTestClient
@ImportAutoConfiguration({ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, AuthenticationErrorHandler.class, AuthWebMvcConfig.class,
        CurrentDeviceArgumentResolver.class, GlobalExceptionHandler.class})
@WebMvcTest(
        controllers = EmotionController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AppVersionMetricsFilter.class)
)
class EmotionControllerTest {

    private static final UUID DEVICE_PUBLIC_ID = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");
    private static final String EMOJI_URI = "/api/v1/emotions/42/emojis/HEART";
    private static final String EMOTION_URI = "/api/v1/emotions/42";
    private static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");

    @Autowired
    private RestTestClient client;

    @MockitoBean
    private EmotionCommandService emotionCommandService;

    @MockitoBean
    private EmotionQueryService emotionQueryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode("access-token")).thenReturn(AccessTokenFixture.액세스_토큰_클레임(DEVICE_PUBLIC_ID));
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid token"));
    }

    @Test
    void 인증된_기기의_감정_상세와_이모지_상태를_GeoJSON으로_반환한다() {
        // given
        when(emotionQueryService.findById(42L, DEVICE_PUBLIC_ID)).thenReturn(detailView());

        // when
        RestTestClient.ResponseSpec result = request(HttpMethod.GET,
                EMOTION_URI + "?devicePublicId=" + UUID.randomUUID(), "access-token");

        // then
        result.expectStatus().isOk()
                .expectHeader().contentType(GEO_JSON)
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-cache, private")
                .expectBody().json("""
                        {
                          "type":"Feature",
                          "id":42,
                          "geometry":{"type":"Point","coordinates":[126.9774,37.5669]},
                          "properties":{
                            "createdAt":"2026-09-24T12:00:00Z",
                            "state":"FRUSTRATED",
                            "rotationDegrees":35.5,
                            "memo":"답답한 하루",
                            "nickname":"먼지구름",
                            "emojis":[
                              {"type":"HEART","count":2,"selected":true},
                              {"type":"LAUGH","count":0,"selected":false},
                              {"type":"CRY","count":0,"selected":false},
                              {"type":"DIZZY","count":0,"selected":false},
                              {"type":"RAGE","count":0,"selected":false},
                              {"type":"SKULL","count":0,"selected":false}
                            ]
                          }
                        }
                        """, JsonCompareMode.STRICT);
        verify(emotionQueryService).findById(42L, DEVICE_PUBLIC_ID);
    }

    @Test
    void 상세_조회에서_잘못된_감정_ID는_400을_반환한다() {
        for (String uri : new String[]{"/api/v1/emotions/0", "/api/v1/emotions/-1", "/api/v1/emotions/invalid"}) {
            request(HttpMethod.GET, uri, "access-token")
                    .expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        }
        verifyNoInteractions(emotionQueryService);
    }

    @Test
    void 상세_조회에서_인증이_없거나_유효하지_않으면_401을_반환한다() {
        request(HttpMethod.GET, EMOTION_URI, null)
                .expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("AUTH-001");
        request(HttpMethod.GET, EMOTION_URI, "invalid-token")
                .expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("AUTH-001");
        verifyNoInteractions(emotionQueryService);
    }

    @ParameterizedTest
    @MethodSource("detailFailures")
    void 상세_조회에서_서비스_실패를_공통_오류로_반환한다(RuntimeException exception, int status, String code) {
        when(emotionQueryService.findById(42L, DEVICE_PUBLIC_ID)).thenThrow(exception);

        request(HttpMethod.GET, EMOTION_URI, "access-token")
                .expectStatus().isEqualTo(status).expectBody().jsonPath("$.code").isEqualTo(code);
    }

    @ParameterizedTest
    @MethodSource("selectionRequests")
    void 인증된_기기의_이모지_상태를_반영하고_본문_없는_204를_반환한다(HttpMethod method, boolean selected) {
        // when
        RestTestClient.ResponseSpec result = request(method, EMOJI_URI + "?devicePublicId=" + UUID.randomUUID(), "access-token");

        // then
        result.expectStatus().isNoContent().expectBody().isEmpty();
        verify(emotionCommandService).updateEmoji(42L, DEVICE_PUBLIC_ID, EmojiType.HEART, selected);
    }

    @ParameterizedTest
    @MethodSource("methods")
    void 유효하지_않은_감정_ID와_이모지_코드는_400을_반환한다(HttpMethod method) {
        // when / then
        for (String uri : new String[]{
                "/api/v1/emotions/0/emojis/HEART",
                "/api/v1/emotions/-1/emojis/HEART",
                "/api/v1/emotions/invalid/emojis/HEART",
                "/api/v1/emotions/42/emojis/UNKNOWN"
        }) {
            request(method, uri, "access-token")
                    .expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        }
        verifyNoInteractions(emotionCommandService);
    }

    @ParameterizedTest
    @MethodSource("unauthenticatedRequests")
    void 인증이_없거나_유효하지_않으면_401을_반환한다(HttpMethod method, String token) {
        // when
        RestTestClient.ResponseSpec result = request(method, EMOJI_URI, token);

        // then
        result.expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("AUTH-001");
        verifyNoInteractions(emotionCommandService);
    }

    @ParameterizedTest
    @MethodSource("serviceFailures")
    void 서비스_실패를_공통_오류_응답으로_반환한다(RuntimeException exception, int status, String code) {
        // given
        doThrow(exception).when(emotionCommandService).updateEmoji(42L, DEVICE_PUBLIC_ID, EmojiType.HEART, true);

        // when
        RestTestClient.ResponseSpec result = request(HttpMethod.PUT, EMOJI_URI, "access-token");

        // then
        result.expectStatus().isEqualTo(status).expectBody().jsonPath("$.code").isEqualTo(code);
    }

    private RestTestClient.ResponseSpec request(HttpMethod method, String uri, String token) {
        RestTestClient.RequestBodySpec request = client.method(method).uri(uri);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request.exchange();
    }

    private EmotionDetailView detailView() {
        Emotion emotion = Emotion.builder()
                .requestId(UUID.randomUUID())
                .location(서울시청_좌표())
                .state(EmotionState.FRUSTRATED)
                .rotationDegrees(35.5)
                .memo("답답한 하루")
                .nickname("먼지구름")
                .build();
        ReflectionTestUtils.setField(emotion, "id", 42L);
        ReflectionTestUtils.setField(emotion, "createdAt", Instant.parse("2026-09-24T12:00:00Z"));

        return EmotionDetailView.of(emotion, List.of(
                EmotionEmojiResult.of(EmojiType.HEART, 2, true),
                EmotionEmojiResult.of(EmojiType.LAUGH, 0, false),
                EmotionEmojiResult.of(EmojiType.CRY, 0, false),
                EmotionEmojiResult.of(EmojiType.DIZZY, 0, false),
                EmotionEmojiResult.of(EmojiType.RAGE, 0, false),
                EmotionEmojiResult.of(EmojiType.SKULL, 0, false)
        ));
    }

    private static Stream<Arguments> detailFailures() {
        return Stream.of(
                Arguments.of(new DeviceException(DeviceErrorCode.DEVICE_NOT_FOUND), 401, "DEVICE-004"),
                Arguments.of(new EmotionException(EmotionErrorCode.EMOTION_NOT_VISIBLE), 404, "EMOTION-002")
        );
    }

    private static Stream<Arguments> selectionRequests() {
        return Stream.of(Arguments.of(HttpMethod.PUT, true), Arguments.of(HttpMethod.DELETE, false));
    }

    private static Stream<HttpMethod> methods() {
        return Stream.of(HttpMethod.PUT, HttpMethod.DELETE);
    }

    private static Stream<Arguments> unauthenticatedRequests() {
        return methods().flatMap(method -> Stream.of(
                Arguments.of(method, null),
                Arguments.of(method, "invalid-token")
        ));
    }

    private static Stream<Arguments> serviceFailures() {
        return Stream.of(
                Arguments.of(new DeviceException(DeviceErrorCode.DEVICE_NOT_FOUND), 401, "DEVICE-004"),
                Arguments.of(new EmotionException(EmotionErrorCode.EMOTION_NOT_VISIBLE), 404, "EMOTION-002")
        );
    }
}
