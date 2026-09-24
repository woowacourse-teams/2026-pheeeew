package com.pheeeew.emotion.presentation;

import static com.pheeeew.emotion.fixture.EmotionFixture.서울시청_좌표;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
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
import com.pheeeew.emotion.application.AudioPlaybackUrlIssuer.PlaybackUrl;
import com.pheeeew.emotion.domain.Audio;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.query.EmotionQueryService;
import com.pheeeew.emotion.application.query.dto.EmotionDetailView;
import com.pheeeew.emotion.application.query.dto.EmotionPageView;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
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
                            "contentType":"MEMO",
                            "audio":null,
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
    void 녹음_상세는_재생_URL과_만료_시각만_반환한다() {
        // given
        Emotion emotion = Emotion.builder().requestId(UUID.randomUUID()).location(서울시청_좌표())
                .state(EmotionState.FRUSTRATED).rotationDegrees(0).nickname("먼지구름").deviceId(1L)
                .audio(Audio.builder().objectKey("private/voice.m4a").build()).build();
        var playback = PlaybackUrl.of(
                "https://audio.example.test/signed", Instant.parse("2026-09-25T12:05:00Z"));
        when(emotionQueryService.findById(42L, DEVICE_PUBLIC_ID))
                .thenReturn(EmotionDetailView.of(emotion, List.of(), playback));

        // when / then
        request(HttpMethod.GET, EMOTION_URI, "access-token").expectStatus().isOk().expectBody()
                .jsonPath("$.properties.contentType").isEqualTo("AUDIO")
                .jsonPath("$.properties.audio.playbackUrl").isEqualTo(playback.playbackUrl())
                .jsonPath("$.properties.audio.expiresAt").isEqualTo("2026-09-25T12:05:00Z")
                .jsonPath("$.properties.audio.objectKey").doesNotExist();
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

    @ParameterizedTest
    @ValueSource(strings = {"\"contentType\":\"NONE\"", "\"contentType\":\"MEMO\",\"memo\":\"메모\"",
            "\"contentType\":\"AUDIO\",\"audioUploadId\":\"upload\""})
    void 등록_내용을_인증된_기기로_저장하고_식별자를_반환한다(String content) {
        Emotion saved = com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더().build();
        ReflectionTestUtils.setField(saved, "id", 42L);
        when(emotionCommandService.save(any(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any(), eq(DEVICE_PUBLIC_ID)))
                .thenReturn(saved);
        client.post().uri("/api/v1/emotions?devicePublicId=" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").contentType(MediaType.APPLICATION_JSON)
                .body(createBody(content)).exchange().expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.LOCATION, EMOTION_URI)
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().json("{\"id\":42}", JsonCompareMode.STRICT);
        verify(emotionCommandService).save(UUID.fromString("5d1ad34e-1e20-4f20-a20e-3825a095fe6b"),
                EmotionState.FRUSTRATED, 126.97, 37.56, 35.5,
                content.contains("MEMO") ? "메모" : null, content.contains("AUDIO") ? "upload" : null, DEVICE_PUBLIC_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"contentType\":\"NONE\",\"memo\":\"메모\"", "\"contentType\":\"AUDIO\"", "\"contentType\":null"})
    void 잘못된_내용_조합은_저장하지_않는다(String content) {
        client.post().uri("/api/v1/emotions").header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON).body(createBody(content)).exchange().expectStatus().isBadRequest();
        verifyNoInteractions(emotionCommandService);
    }

    @Test
    void 등록_필수값과_좌표_각도_메모_길이를_검증한다() {
        String valid = createBody("\"contentType\":\"NONE\"");
        for (String body : List.of(valid.replace("35.5", "360"), valid.replace("126.97", "181"),
                valid.replace("37.56", "-91"), valid.replace("\"FRUSTRATED\"", "null"),
                valid.replace("\"5d1ad34e-1e20-4f20-a20e-3825a095fe6b\"", "null"),
                createBody("\"contentType\":\"MEMO\",\"memo\":\"" + "가".repeat(201) + "\""))) {
            client.post().uri("/api/v1/emotions").header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                    .contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isBadRequest();
        }
        verifyNoInteractions(emotionCommandService);
    }

    @Test
    void 등록은_기기_인증이_필수다() {
        request(HttpMethod.POST, "/api/v1/emotions", null).expectStatus().isUnauthorized();
        request(HttpMethod.POST, "/api/v1/emotions", "invalid-token").expectStatus().isUnauthorized();
        verifyNoInteractions(emotionCommandService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMOTION_REQUEST_ID_CONFLICT", "EMOTION_AUDIO_UPLOAD_NOT_FOUND", "EMOTION_AUDIO_UPLOAD_NOT_READY",
            "EMOTION_AUDIO_UPLOAD_ALREADY_USED", "EMOTION_AUDIO_UPLOAD_UNAVAILABLE"})
    void 등록_실패_상태와_코드를_전달한다(String name) {
        EmotionErrorCode error = EmotionErrorCode.valueOf(name);
        when(emotionCommandService.save(any(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any(), any()))
                .thenThrow(new EmotionException(error));
        client.post().uri("/api/v1/emotions").header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON).body(createBody("\"contentType\":\"AUDIO\",\"audioUploadId\":\"upload\""))
                .exchange().expectStatus().isEqualTo(error.getStatus().value()).expectBody().jsonPath("$.code").isEqualTo(error.getCode());
    }

    @Test
    void 목록의_첫_페이지와_다음_페이지를_인증된_기기로_조회한다() {
        EmotionSearchBounds bounds = EmotionSearchBounds.of(126.9, 37.5, 127.1, 37.6);
        when(emotionQueryService.findFirstListPage(bounds, DEVICE_PUBLIC_ID))
                .thenReturn(EmotionPageView.of(List.of(detailView()), true, "next"));
        when(emotionQueryService.findNextListPage("next", DEVICE_PUBLIC_ID))
                .thenReturn(EmotionPageView.of(List.of(), false, null));

        request(HttpMethod.GET, "/api/v1/emotions?minLongitude=126.9&minLatitude=37.5&maxLongitude=127.1&maxLatitude=37.6", "access-token")
                .expectStatus().isOk().expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-cache, private")
                .expectBody().jsonPath("$.items[0].properties.emojis.length()").isEqualTo(6)
                .jsonPath("$.hasNext").isEqualTo(true).jsonPath("$.nextCursor").isEqualTo("next");
        request(HttpMethod.GET, "/api/v1/emotions?cursor=next", "access-token")
                .expectStatus().isOk().expectBody().jsonPath("$.items").isEmpty().jsonPath("$.hasNext").isEqualTo(false);
        verify(emotionQueryService).findFirstListPage(bounds, DEVICE_PUBLIC_ID);
        verify(emotionQueryService).findNextListPage("next", DEVICE_PUBLIC_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?minLongitude=126.9", "?cursor=next&minLongitude=126.9",
            "?minLongitude=126.9&minLatitude=37.5&maxLongitude=126.9&maxLatitude=37.6",
            "?minLongitude=181&minLatitude=37.5&maxLongitude=127.1&maxLatitude=37.6"})
    void 목록_영역과_커서_조합을_검증한다(String query) {
        request(HttpMethod.GET, "/api/v1/emotions" + query, "access-token").expectStatus().isBadRequest();
        verifyNoInteractions(emotionQueryService);
    }

    @Test
    void 목록은_기기_인증이_필수다() {
        request(HttpMethod.GET, "/api/v1/emotions?cursor=next", null).expectStatus().isUnauthorized();
        request(HttpMethod.GET, "/api/v1/emotions?cursor=next", "invalid-token").expectStatus().isUnauthorized();
        verifyNoInteractions(emotionQueryService);
    }

    private String createBody(String content) {
        return "{\"requestId\":\"5d1ad34e-1e20-4f20-a20e-3825a095fe6b\",\"state\":\"FRUSTRATED\","
                + "\"longitude\":126.97,\"latitude\":37.56,\"rotationDegrees\":35.5," + content + "}";
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
                Arguments.of(new EmotionException(EmotionErrorCode.EMOTION_NOT_VISIBLE), 404, "EMOTION-002"),
                Arguments.of(new EmotionException(EmotionErrorCode.EMOTION_AUDIO_PLAYBACK_UNAVAILABLE), 503, "EMOTION-009")
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
