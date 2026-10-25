package com.pheeeew.notification.presentation;

import static com.pheeeew.notification.fixture.PushTokenFixture.기본_등록_값;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.auth.fixture.AccessTokenFixture;
import com.pheeeew.auth.infra.security.AuthenticationErrorHandler;
import com.pheeeew.auth.infra.security.SecurityConfig;
import com.pheeeew.auth.presentation.config.AuthWebMvcConfig;
import com.pheeeew.auth.presentation.resolver.CurrentDeviceArgumentResolver;
import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.common.logging.RequestLoggingConfiguration;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.notification.application.NotificationSettingService;
import com.pheeeew.notification.domain.NotificationSettings;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
@ImportAutoConfiguration({ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, AuthenticationErrorHandler.class, AuthWebMvcConfig.class,
        CurrentDeviceArgumentResolver.class, GlobalExceptionHandler.class, RequestLoggingConfiguration.class})
@WebMvcTest(controllers = NotificationSettingController.class)
class NotificationSettingControllerTest {

    private static final UUID DEVICE_PUBLIC_ID = UUID.fromString("5d1c0a7e-8b3f-4e21-9c6d-2f4a8b7e1c90");
    private static final String PUSH_TOKEN_URI = "/api/v3/devices/me/push-token";
    private static final String SETTINGS_URI = "/api/v3/devices/me/notification-settings";

    @Autowired
    private RestTestClient client;

    @MockitoBean
    private NotificationSettingService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode("access-token")).thenReturn(AccessTokenFixture.액세스_토큰_클레임(DEVICE_PUBLIC_ID));
    }

    @Test
    void 등록_값을_저장하고_본문_없이_응답한다() {
        // when / then
        client.put().uri(PUSH_TOKEN_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"token\":\"" + 기본_등록_값 + "\"}")
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();
        verify(service).updatePushToken(DEVICE_PUBLIC_ID, 기본_등록_값);
    }

    @Test
    void 지원_상한인_ASCII_1024자는_받는다() {
        // given
        String 상한_값 = "a".repeat(1024);

        // when / then
        client.put().uri(PUSH_TOKEN_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"token\":\"" + 상한_값 + "\"}")
                .exchange()
                .expectStatus().isNoContent();
        verify(service).updatePushToken(DEVICE_PUBLIC_ID, 상한_값);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"token\":null}",
            "{\"token\":\"   \"}",
            "{\"token\":\"a\\u0000b\"}"
    })
    void 비어_있거나_저장할_수_없는_등록_값은_거절한다(String body) {
        // when / then
        client.put().uri(PUSH_TOKEN_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(service);
    }

    @Test
    void 글자_수가_1024_이하여도_UTF_8_1024바이트를_넘으면_거절한다() {
        // given
        String 다중_바이트_값 = "a".repeat(1023) + "é";

        // when / then
        client.put().uri(PUSH_TOKEN_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"token\":\"" + 다중_바이트_값 + "\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(service);
    }

    @Test
    void 등록_값을_해제하고_본문_없이_응답한다() {
        // when / then
        client.delete().uri(PUSH_TOKEN_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();
        verify(service).deletePushToken(DEVICE_PUBLIC_ID);
    }

    @Test
    void 수신_설정을_캐시하지_않는_응답으로_조회한다() {
        // given
        when(service.findSettings(DEVICE_PUBLIC_ID)).thenReturn(NotificationSettings.of(true, false));

        // when / then
        client.get().uri(SETTINGS_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().json("""
                        {"collectionIssuedEnabled":true,"rankDroppedEnabled":false}
                        """, JsonCompareMode.STRICT);
    }

    @Test
    void 두_수신_설정을_함께_바꾸고_본문_없이_응답한다() {
        // when / then
        client.put().uri(SETTINGS_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"collectionIssuedEnabled\":false,\"rankDroppedEnabled\":true}")
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();
        verify(service).updateSettings(DEVICE_PUBLIC_ID, false, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"collectionIssuedEnabled\":false}",
            "{\"collectionIssuedEnabled\":null,\"rankDroppedEnabled\":true}",
            "{}"
    })
    void 수신_설정은_두_값이_모두_있어야_바꾼다(String body) {
        // when / then
        client.put().uri(SETTINGS_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(service);
    }

    @Test
    void 기기가_없으면_기존_기기_없음_401을_반환한다() {
        // given
        when(service.findSettings(DEVICE_PUBLIC_ID)).thenThrow(new DeviceException(DeviceErrorCode.DEVICE_NOT_FOUND));

        // when / then
        client.get().uri(SETTINGS_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT " + PUSH_TOKEN_URI, "DELETE " + PUSH_TOKEN_URI, "GET " + SETTINGS_URI, "PUT " + SETTINGS_URI})
    void 네_API_는_인증_토큰이_없으면_거절한다(String route) {
        // given
        String[] parts = route.split(" ");

        // when / then
        client.method(HttpMethod.valueOf(parts[0])).uri(parts[1])
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("AUTH-001");
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST " + PUSH_TOKEN_URI, "PATCH " + SETTINGS_URI, "DELETE " + SETTINGS_URI})
    void 정의하지_않은_메서드는_등록_기기여도_거절한다(String route) {
        // given
        String[] parts = route.split(" ");

        // when / then
        client.method(HttpMethod.valueOf(parts[0])).uri(parts[1])
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("AUTH-002");
        verifyNoInteractions(service);
    }
}
