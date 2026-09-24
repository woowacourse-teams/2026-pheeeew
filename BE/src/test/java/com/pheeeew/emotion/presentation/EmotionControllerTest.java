package com.pheeeew.emotion.presentation;

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
import com.pheeeew.emotion.application.emoji.EmotionEmojiService;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.exception.EmotionErrorCode;
import com.pheeeew.emotion.exception.EmotionException;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

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

    @Autowired
    private RestTestClient client;

    @MockitoBean
    private EmotionEmojiService emotionEmojiService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode("access-token")).thenReturn(AccessTokenFixture.액세스_토큰_클레임(DEVICE_PUBLIC_ID));
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid token"));
    }

    @ParameterizedTest
    @MethodSource("selectionRequests")
    void 인증된_기기의_이모지_상태를_반영하고_본문_없는_204를_반환한다(HttpMethod method, boolean selected) {
        // when
        RestTestClient.ResponseSpec result = request(method, EMOJI_URI + "?devicePublicId=" + UUID.randomUUID(), "access-token");

        // then
        result.expectStatus().isNoContent().expectBody().isEmpty();
        verify(emotionEmojiService).update(42L, DEVICE_PUBLIC_ID, EmojiType.HEART, selected);
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
        verifyNoInteractions(emotionEmojiService);
    }

    @ParameterizedTest
    @MethodSource("unauthenticatedRequests")
    void 인증이_없거나_유효하지_않으면_401을_반환한다(HttpMethod method, String token) {
        // when
        RestTestClient.ResponseSpec result = request(method, EMOJI_URI, token);

        // then
        result.expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("AUTH-001");
        verifyNoInteractions(emotionEmojiService);
    }

    @ParameterizedTest
    @MethodSource("serviceFailures")
    void 서비스_실패를_공통_오류_응답으로_반환한다(RuntimeException exception, int status, String code) {
        // given
        doThrow(exception).when(emotionEmojiService).update(42L, DEVICE_PUBLIC_ID, EmojiType.HEART, true);

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
                Arguments.of(new EmotionException(EmotionErrorCode.EMOTION_NOT_FOUND), 404, "SIGH-002")
        );
    }
}
