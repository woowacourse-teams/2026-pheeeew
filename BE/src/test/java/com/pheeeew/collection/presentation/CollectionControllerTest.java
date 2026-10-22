package com.pheeeew.collection.presentation;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pheeeew.auth.fixture.AccessTokenFixture;
import com.pheeeew.auth.infra.security.AuthenticationErrorHandler;
import com.pheeeew.auth.infra.security.SecurityConfig;
import com.pheeeew.auth.presentation.config.AuthWebMvcConfig;
import com.pheeeew.auth.presentation.resolver.CurrentDeviceArgumentResolver;
import com.pheeeew.collection.application.CollectionQueryService;
import com.pheeeew.collection.application.dto.CollectionTodayResult;
import com.pheeeew.collection.exception.CollectionErrorCode;
import com.pheeeew.collection.exception.CollectionException;
import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.domain.EmotionState;
import java.time.LocalDate;
import java.util.Arrays;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
@ImportAutoConfiguration({ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, AuthenticationErrorHandler.class, AuthWebMvcConfig.class,
        CurrentDeviceArgumentResolver.class, GlobalExceptionHandler.class})
@WebMvcTest(controllers = CollectionController.class)
class CollectionControllerTest {

    private static final UUID DEVICE_PUBLIC_ID = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");
    private static final String URI = "/api/v1/collection-cards/today";

    @Autowired
    private RestTestClient client;

    @MockitoBean
    private CollectionQueryService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode("access-token")).thenReturn(AccessTokenFixture.액세스_토큰_클레임(DEVICE_PUBLIC_ID));
    }

    @Test
    void 인증된_기기의_다섯_진행도와_미발급_카드의_null을_캐시하지_않고_반환한다() {
        // given
        var items = Arrays.stream(EmotionState.values())
                .map(state -> CollectionTodayResult.Item.of(state, 0, state == EmotionState.ANGRY ? 70 : 0))
                .toList();
        when(service.findToday(DEVICE_PUBLIC_ID)).thenReturn(CollectionTodayResult.of(LocalDate.of(2026, 10, 22), items));

        // when
        var response = client.get().uri(URI).header(HttpHeaders.AUTHORIZATION, "Bearer access-token").exchange();

        // then
        response.expectStatus().isOk().expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().json("""
                        {
                          "date":"2026-10-22",
                          "items":[
                            {"state":"FRUSTRATED","stamps":{"count":0,"target":1,"remaining":1},
                             "presses":{"count":0,"target":100,"remaining":100},"card":null},
                            {"state":"IRRITATED","stamps":{"count":0,"target":1,"remaining":1},
                             "presses":{"count":0,"target":100,"remaining":100},"card":null},
                            {"state":"EXHAUSTED","stamps":{"count":0,"target":1,"remaining":1},
                             "presses":{"count":0,"target":100,"remaining":100},"card":null},
                            {"state":"DISCOURAGED","stamps":{"count":0,"target":1,"remaining":1},
                             "presses":{"count":0,"target":100,"remaining":100},"card":null},
                            {"state":"ANGRY","stamps":{"count":0,"target":1,"remaining":1},
                             "presses":{"count":70,"target":100,"remaining":30},"card":null}
                          ]
                        }
                        """, JsonCompareMode.STRICT);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "invalid-token")
    void 인증이_없거나_잘못되면_진행도를_조회할_수_없다(String token) {
        // given
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid token"));
        var request = client.get().uri(URI);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        // when
        var response = request.exchange();

        // then
        response.expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("AUTH-001");
        verifyNoInteractions(service);
    }

    @Test
    void 토큰의_기기가_없으면_기존_기기_인증_오류를_반환한다() {
        // given
        when(service.findToday(DEVICE_PUBLIC_ID)).thenThrow(new DeviceException(DeviceErrorCode.DEVICE_NOT_FOUND));

        // when
        var response = client.get().uri(URI).header(HttpHeaders.AUTHORIZATION, "Bearer access-token").exchange();

        // then
        response.expectStatus().isUnauthorized().expectBody()
                .jsonPath("$.code").isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND.getCode());
    }

    @Test
    void 날짜_변경으로_조회하지_못하면_503과_재시도_안내를_반환한다() {
        // given
        when(service.findToday(DEVICE_PUBLIC_ID)).thenThrow(new CollectionException(CollectionErrorCode.COLLECTION_DATE_CHANGED));

        // when
        var response = client.get().uri(URI).header(HttpHeaders.AUTHORIZATION, "Bearer access-token").exchange();

        // then
        response.expectStatus().isEqualTo(503).expectBody().json("""
                {"code":"COLLECTION-001","message":"조회 중 날짜가 변경되었습니다. 잠시 후 다시 시도해 주세요."}
                """, JsonCompareMode.STRICT);
    }
}
