package com.pheeeew.sigh.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.auth.fixture.AccessTokenFixture;
import com.pheeeew.auth.infra.security.AuthenticationErrorHandler;
import com.pheeeew.auth.infra.security.SecurityConfig;
import com.pheeeew.auth.presentation.config.AuthWebMvcConfig;
import com.pheeeew.auth.presentation.resolver.CurrentDeviceArgumentResolver;
import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.sigh.application.SighService;
import com.pheeeew.sigh.application.like.SighLikeRetryService;
import com.pheeeew.sigh.application.like.dto.SighLikeResult;
import com.pheeeew.sigh.exception.SighErrorCode;
import com.pheeeew.sigh.exception.SighException;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
@ImportAutoConfiguration({ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, AuthenticationErrorHandler.class, AuthWebMvcConfig.class,
        CurrentDeviceArgumentResolver.class, GlobalExceptionHandler.class})
@WebMvcTest(SighV2Controller.class)
class SighV2LikeControllerTest {

    private static final UUID DEVICE_PUBLIC_ID = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");
    private static final String LIKES_URI = "/api/v2/sighs/42/likes";

    @Autowired
    private RestTestClient client;

    @MockitoBean
    private SighLikeRetryService sighLikeRetryService;

    @MockitoBean
    private SighService sighService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode("access-token")).thenReturn(AccessTokenFixture.액세스_토큰_클레임(DEVICE_PUBLIC_ID));
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid token"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 요청_본문의_기기_식별자와_무관하게_인증된_기기의_상태를_변경하고_결과를_반환한다(boolean liked) {
        // given
        long likeCount = liked ? 12 : 11;
        when(sighLikeRetryService.update(42L, DEVICE_PUBLIC_ID, liked))
                .thenReturn(SighLikeResult.of(liked, likeCount));

        // when
        RestTestClient.ResponseSpec result = request(LIKES_URI, "access-token", """
                {"liked":%s,"deviceId":"00000000-0000-4000-8000-000000009999"}
                """.formatted(liked));

        // then
        result.expectStatus().isOk().expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody().json("{\"liked\":%s,\"likeCount\":%s}".formatted(liked, likeCount), JsonCompareMode.STRICT);
        verify(sighLikeRetryService).update(42L, DEVICE_PUBLIC_ID, liked);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{\"liked\":null}", "{\"liked\":\"invalid\"}", "{"})
    void 좋아요_상태가_없거나_본문이_잘못되면_400을_반환한다(String body) {
        // given / when
        RestTestClient.ResponseSpec result = request(LIKES_URI, "access-token", body);

        // then
        result.expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(sighLikeRetryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number"})
    void 한숨_ID_형식이_올바르지_않으면_400을_반환한다(String sighId) {
        // given / when
        RestTestClient.ResponseSpec result = request("/api/v2/sighs/" + sighId + "/likes", "access-token", "{\"liked\":true}");

        // then
        result.expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(sighLikeRetryService);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "invalid-token")
    void 인증이_없거나_유효하지_않으면_401을_반환한다(String token) {
        // given / when
        RestTestClient.ResponseSpec result = request(LIKES_URI, token, "{\"liked\":true}");

        // then
        result.expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("AUTH-001");
        verifyNoInteractions(sighLikeRetryService);
        if (token == null) {
            verifyNoInteractions(jwtDecoder);
        }
    }

    @ParameterizedTest
    @MethodSource("serviceFailures")
    void 서비스_실패는_공통_오류_응답으로_반환한다(RuntimeException exception, int status, String code) {
        // given
        when(sighLikeRetryService.update(42L, DEVICE_PUBLIC_ID, true)).thenThrow(exception);

        // when
        RestTestClient.ResponseSpec result = request(LIKES_URI, "access-token", "{\"liked\":true}");

        // then
        result.expectStatus().isEqualTo(status).expectBody().jsonPath("$.code").isEqualTo(code);
    }

    private RestTestClient.ResponseSpec request(String uri, String token, String body) {
        RestTestClient.RequestBodySpec request = client.post().uri(uri).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request.body(body).exchange();
    }

    private static Stream<Arguments> serviceFailures() {
        return Stream.of(
                Arguments.of(new DeviceException(DeviceErrorCode.DEVICE_NOT_FOUND), 401, "DEVICE-004"),
                Arguments.of(new SighException(SighErrorCode.SIGH_NOT_FOUND), 404, "SIGH-002"),
                Arguments.of(new ObjectOptimisticLockingFailureException("conflict", null), 500, "COMMON-002")
        );
    }
}
