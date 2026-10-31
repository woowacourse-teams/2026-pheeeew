package com.pheeeew.common.config;

import static org.mockito.Mockito.verifyNoInteractions;

import com.pheeeew.device.application.DeviceChallengeService;
import com.pheeeew.device.application.DeviceService;
import com.pheeeew.device.application.DeviceTokenService;
import com.pheeeew.device.presentation.DeviceController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
@WebMvcTest(
        controllers = DeviceController.class
)
@Import(MessageConverterConfig.class)
class MessageConverterConfigTest {

    private static final String DEVICES_URI = "/api/v2/devices";
    private static final String TOKENS_URI = "/api/v2/devices/tokens";
    private static final byte[] CBOR_본문 = {(byte) 0xA1, 0x61, 0x61, 0x01};

    private final RestTestClient client;

    @MockitoBean
    private DeviceService deviceService;

    @MockitoBean
    private DeviceTokenService deviceTokenService;

    @MockitoBean
    private DeviceChallengeService deviceChallengeService;

    @Autowired
    MessageConverterConfigTest(RestTestClient client) {
        this.client = client;
    }

    @Test
    void 인증_없이_열린_기기_등록이_CBOR_본문을_해석하지_않는다() {
        // when
        RestTestClient.ResponseSpec result = client.post()
                .uri(DEVICES_URI)
                .contentType(MediaType.APPLICATION_CBOR)
                .body(CBOR_본문)
                .exchange();

        // then
        result.expectStatus().isEqualTo(415);
        verifyNoInteractions(deviceService);
    }

    @Test
    void 토큰_갱신도_CBOR_본문을_해석하지_않는다() {
        // when
        RestTestClient.ResponseSpec result = client.post()
                .uri(TOKENS_URI)
                .contentType(MediaType.APPLICATION_CBOR)
                .body(CBOR_본문)
                .exchange();

        // then
        result.expectStatus().isEqualTo(415);
        verifyNoInteractions(deviceTokenService);
    }

    @Test
    void JSON_본문은_그대로_컨트롤러까지_간다() {
        // when
        RestTestClient.ResponseSpec result = client.post()
                .uri(DEVICES_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .exchange();

        // then
        result.expectStatus().isBadRequest();
    }
}
