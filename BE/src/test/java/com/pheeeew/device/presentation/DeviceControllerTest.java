package com.pheeeew.device.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.device.application.DeviceService;
import com.pheeeew.device.application.DeviceTokenService;
import com.pheeeew.device.application.dto.AccessTokenResult;
import com.pheeeew.device.application.dto.DeviceSaveResult;
import com.pheeeew.device.domain.DevicePlatform;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
@Import(GlobalExceptionHandler.class)
@WebMvcTest(DeviceController.class)
class DeviceControllerTest {

    private static final String DEVICES_URI = "/api/v2/devices";
    private static final String TOKENS_URI = "/api/v2/devices/tokens";
    private static final UUID REQUEST_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String ACCESS_TOKEN = "eyJhbGciOiJSUzI1NiJ9.access.signature";
    private static final String REFRESH_TOKEN =
            "5d1ad34e-1e20-4f20-a20e-3825a095fe6b.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXkw";

    private final RestTestClient client;

    @MockitoBean
    private DeviceService deviceService;

    @MockitoBean
    private DeviceTokenService deviceTokenService;

    @Autowired
    DeviceControllerTest(RestTestClient client) {
        this.client = client;
    }

    @Test
    void 최초_등록하면_201과_토큰만_반환한다() {
        // given
        when(deviceService.save(REQUEST_ID, DevicePlatform.ANDROID))
                .thenReturn(DeviceSaveResult.of(AccessTokenResult.of(ACCESS_TOKEN, 1800L), REFRESH_TOKEN, true));

        // when
        RestTestClient.ResponseSpec result = 등록한다(기본_등록_본문());

        // then
        result.expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .json(기본_등록_응답(), JsonCompareMode.STRICT);
        verify(deviceService).save(REQUEST_ID, DevicePlatform.ANDROID);
    }

    @Test
    void 같은_요청_식별자로_재시도하면_200과_토큰만_반환한다() {
        // given
        when(deviceService.save(REQUEST_ID, DevicePlatform.ANDROID))
                .thenReturn(DeviceSaveResult.of(AccessTokenResult.of(ACCESS_TOKEN, 1800L), REFRESH_TOKEN, false));

        // when
        RestTestClient.ResponseSpec result = 등록한다(기본_등록_본문());

        // then
        result.expectStatus().isOk()
                .expectBody()
                .json(기본_등록_응답(), JsonCompareMode.STRICT);
        verify(deviceService).save(REQUEST_ID, DevicePlatform.ANDROID);
    }

    @Test
    void 무결성_증명_값을_생략해도_플랫폼만으로_등록한다() {
        // given
        when(deviceService.save(REQUEST_ID, DevicePlatform.IOS))
                .thenReturn(DeviceSaveResult.of(AccessTokenResult.of(ACCESS_TOKEN, 1800L), REFRESH_TOKEN, true));

        // when
        RestTestClient.ResponseSpec result = 등록한다("""
                {
                  "requestId": "550e8400-e29b-41d4-a716-446655440000",
                  "attestation": {"platform": "IOS"}
                }
                """);

        // then
        result.expectStatus().isCreated();
        verify(deviceService).save(REQUEST_ID, DevicePlatform.IOS);
    }

    @Test
    void 요청_본문에_기기_식별자를_넣어도_서버가_쓰지_않는다() {
        // given
        when(deviceService.save(REQUEST_ID, DevicePlatform.ANDROID))
                .thenReturn(DeviceSaveResult.of(AccessTokenResult.of(ACCESS_TOKEN, 1800L), REFRESH_TOKEN, true));

        // when
        RestTestClient.ResponseSpec result = 등록한다("""
                {
                  "requestId": "550e8400-e29b-41d4-a716-446655440000",
                  "deviceId": 1,
                  "publicId": "a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa",
                  "attestation": {"platform": "ANDROID", "token": null, "keyId": null}
                }
                """);

        // then
        result.expectStatus().isCreated()
                .expectBody()
                .json(기본_등록_응답(), JsonCompareMode.STRICT);
        verify(deviceService).save(REQUEST_ID, DevicePlatform.ANDROID);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"attestation\": {\"platform\": \"ANDROID\"}}",
            "{\"requestId\": null, \"attestation\": {\"platform\": \"ANDROID\"}}",
            "{\"requestId\": \"not-a-uuid\", \"attestation\": {\"platform\": \"ANDROID\"}}",
            "{\"requestId\": \"550e8400-e29b-41d4-a716-446655440000\"}",
            "{\"requestId\": \"550e8400-e29b-41d4-a716-446655440000\", \"attestation\": null}",
            "{\"requestId\": \"550e8400-e29b-41d4-a716-446655440000\", \"attestation\": {}}",
            "{\"requestId\": \"550e8400-e29b-41d4-a716-446655440000\", \"attestation\": {\"platform\": \"WINDOWS\"}}",
            "{\"requestId\": \"550e8400-e29b-41d4-a716-446655440000\", \"attestation\": {\"platform\": \"android\"}}"
    })
    void 등록_요청_계약을_지키지_않으면_400을_반환한다(String body) {
        // given / when
        RestTestClient.ResponseSpec result = 등록한다(body);

        // then
        잘못된_요청이다(result);
        verifyNoInteractions(deviceService);
    }

    @Test
    void 재시도_창이_지난_등록은_409를_반환한다() {
        // given
        when(deviceService.save(REQUEST_ID, DevicePlatform.ANDROID))
                .thenThrow(new DeviceException(DeviceErrorCode.DEVICE_REGISTRATION_WINDOW_EXPIRED));

        // when
        RestTestClient.ResponseSpec result = 등록한다(기본_등록_본문());

        // then
        result.expectStatus().isEqualTo(409)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .json("""
                        {"code":"DEVICE-002","message":"기기 등록 재시도 시간이 지났습니다. 새 요청으로 등록해 주세요."}
                        """, JsonCompareMode.STRICT);
    }

    @Test
    void 리프레시_토큰으로_재발급하면_액세스_토큰만_반환한다() {
        // given
        when(deviceTokenService.reissueAccessToken(REFRESH_TOKEN))
                .thenReturn(AccessTokenResult.of(ACCESS_TOKEN, 1800L));

        // when
        RestTestClient.ResponseSpec result = 재발급한다("""
                {"refreshToken": "%s"}
                """.formatted(REFRESH_TOKEN));

        // then
        result.expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .json("""
                        {"accessToken":"%s","expiresIn":1800}
                        """.formatted(ACCESS_TOKEN), JsonCompareMode.STRICT);
        verify(deviceTokenService).reissueAccessToken(REFRESH_TOKEN);
    }

    @Test
    void 사용할_수_없는_리프레시_토큰이면_401을_반환한다() {
        // given
        when(deviceTokenService.reissueAccessToken(any()))
                .thenThrow(new DeviceException(DeviceErrorCode.DEVICE_REFRESH_TOKEN_INVALID));

        // when
        RestTestClient.ResponseSpec result = 재발급한다("""
                {"refreshToken": "garbage"}
                """);

        // then
        result.expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .json("""
                        {"code":"DEVICE-003","message":"인증 정보를 사용할 수 없습니다."}
                        """, JsonCompareMode.STRICT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"refreshToken\": null}",
            "{\"refreshToken\": \"\"}",
            "{\"refreshToken\": \"   \"}"
    })
    void 리프레시_토큰이_비어_있으면_401이_아니라_400을_반환한다(String body) {
        // given / when
        RestTestClient.ResponseSpec result = 재발급한다(body);

        // then
        잘못된_요청이다(result);
        verifyNoInteractions(deviceTokenService);
    }

    private RestTestClient.ResponseSpec 등록한다(String body) {
        return client.post()
                .uri(DEVICES_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private RestTestClient.ResponseSpec 재발급한다(String body) {
        return client.post()
                .uri(TOKENS_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private void 잘못된_요청이다(RestTestClient.ResponseSpec result) {
        result.expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .json("""
                        {"code":"COMMON-001","message":"요청 값이 올바르지 않습니다."}
                        """, JsonCompareMode.STRICT);
    }

    private String 기본_등록_본문() {
        return """
                {
                  "requestId": "550e8400-e29b-41d4-a716-446655440000",
                  "attestation": {"platform": "ANDROID", "token": null, "keyId": null}
                }
                """;
    }

    private String 기본_등록_응답() {
        return """
                {"accessToken":"%s","refreshToken":"%s","expiresIn":1800}
                """.formatted(ACCESS_TOKEN, REFRESH_TOKEN);
    }
}
