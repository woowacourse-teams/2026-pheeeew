package com.pheeeew.emotion.presentation;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_UNAVAILABLE;
import static com.pheeeew.emotion.fixture.AudioUploadFixture.기본_업로드_빌더;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.pheeeew.emotion.application.AudioUploadService;
import com.pheeeew.emotion.application.AudioUrlIssuer.UploadUrl;
import com.pheeeew.emotion.application.dto.AudioUploadResult;
import com.pheeeew.emotion.exception.EmotionException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
@ImportAutoConfiguration({ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, AuthenticationErrorHandler.class, AuthWebMvcConfig.class,
        CurrentDeviceArgumentResolver.class, GlobalExceptionHandler.class})
@WebMvcTest(controllers = AudioUploadController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AppVersionMetricsFilter.class))
class AudioUploadControllerTest {

    private static final UUID DEVICE_ID = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");
    private static final String URI = "/api/v1/audio-uploads";
    private static final String BODY = "{\"contentType\":\"audio/mp4\",\"contentLength\":1024}";

    @Autowired
    private RestTestClient client;
    @MockitoBean
    private AudioUploadService service;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode("access-token")).thenReturn(AccessTokenFixture.액세스_토큰_클레임(DEVICE_ID));
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid token"));
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 5_242_880})
    void 토큰의_기기로_업로드를_준비하고_URL과_헤더를_캐시_없이_반환한다(long contentLength) {
        // given
        AudioUploadResult issued = AudioUploadResult.of(기본_업로드_빌더().build(), UploadUrl.of(
                "https://example.com/signed-put", Instant.parse("2026-09-28T04:05:00Z"),
                Map.of("content-type", List.of("audio/mp4"), "content-length", List.of(Long.toString(contentLength)),
                        "if-none-match", List.of("*"))));
        when(service.prepare(DEVICE_ID, "audio/mp4", contentLength)).thenReturn(issued);

        // when / then
        client.post().uri(URI + "?devicePublicId=" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("contentType", "audio/mp4", "contentLength", contentLength)).exchange()
                .expectStatus().isCreated().expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().json("""
                        {"uploadId":"%s","uploadUrl":"https://example.com/signed-put",
                         "expiresAt":"2026-09-28T04:05:00Z","headers":{"content-type":["audio/mp4"],
                         "content-length":["%s"],"if-none-match":["*"]}}
                        """.formatted(issued.uploadId(), contentLength), JsonCompareMode.STRICT);
        verify(service).prepare(DEVICE_ID, "audio/mp4", contentLength);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"contentType\":\"video/mp4\",\"contentLength\":1024}",
            "{\"contentType\":\"audio/mp4\",\"contentLength\":0}",
            "{\"contentType\":\"audio/mp4\",\"contentLength\":5242881}"})
    void 잘못된_메타데이터는_URL_발급_전에_거부한다(String body) {
        // when / then
        post(body, "access-token").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"invalid-token"})
    void 인증되지_않은_요청은_URL을_발급하지_않는다(String token) {
        // when / then
        post(BODY, token).expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("AUTH-001");
        verifyNoInteractions(service);
    }

    @Test
    void 저장소_발급_실패는_503으로_반환하고_내부_원인은_노출하지_않는다() {
        // given
        when(service.prepare(any(), any(), anyLong())).thenThrow(new EmotionException(
                EMOTION_AUDIO_UPLOAD_UNAVAILABLE, new IllegalStateException("private-storage-detail")));

        // when / then
        post(BODY, "access-token").expectStatus().isEqualTo(503).expectBody().json("""
                {"code":"EMOTION-006","message":"녹음 업로드를 확인할 수 없습니다."}
                """, JsonCompareMode.STRICT);
    }

    private RestTestClient.ResponseSpec post(String body, String token) {
        RestTestClient.RequestBodySpec request = client.post().uri(URI).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request.body(body).exchange();
    }
}
