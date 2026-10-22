package com.pheeeew.emotion.presentation;

import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import com.pheeeew.emotion.application.dto.EmotionEmojiResult;
import com.pheeeew.emotion.application.AudioUrlIssuer.PlaybackUrl;
import com.pheeeew.emotion.application.EmotionCursorCodec;
import com.pheeeew.emotion.application.dto.EmotionCursor;
import com.pheeeew.emotion.domain.Audio;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.query.EmotionMapQueryService;
import com.pheeeew.emotion.application.query.EmotionQueryService;
import com.pheeeew.emotion.application.dto.EmotionDetailView;
import com.pheeeew.emotion.application.dto.EmotionPageView;
import com.pheeeew.emotion.application.dto.EmotionMapItemView;
import com.pheeeew.emotion.application.dto.EmotionMapPageView;
import com.pheeeew.emotion.application.dto.EmotionRegionMapItemView;
import com.pheeeew.emotion.application.dto.RegionEmotionSummary;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.exception.EmotionErrorCode;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.groups.application.dto.GroupStampResult;
import com.pheeeew.groups.exception.GroupErrorCode;
import com.pheeeew.groups.exception.GroupException;
import com.pheeeew.region.domain.Region;
import com.pheeeew.region.domain.RegionLevel;
import com.pheeeew.groups.domain.StampFrame;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
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
        controllers = {EmotionController.class, EmotionV4Controller.class},
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AppVersionMetricsFilter.class)
)
class EmotionControllerTest {

    private static final UUID DEVICE_PUBLIC_ID = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");
    private static final String EMOJI_URI = "/api/v1/emotions/42/emojis/HEART";
    private static final String EMOTION_URI = "/api/v1/emotions/42";
    private static final String V3_EMOTION_URI = "/api/v3/emotions/42";
    private static final String REGION_MAP_URI = "/api/v2/emotions/map/regions";
    private static final String REGION_BOUNDS_QUERY = "?minLongitude=127.8&minLatitude=37.2&maxLongitude=128.2&maxLatitude=37.8";
    private static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");

    @Autowired
    private RestTestClient client;

    @MockitoBean
    private EmotionCommandService emotionCommandService;

    @MockitoBean
    private EmotionQueryService emotionQueryService;

    @MockitoBean
    private EmotionMapQueryService emotionMapQueryService;

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
        when(emotionQueryService.findById(42L, DEVICE_PUBLIC_ID)).thenReturn(detailView(42L, true));

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
                            "groupStamp":null,
                            "groupId":null,
                            "isMine":true,
                            "nickname":"익명",
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

    @ParameterizedTest
    @MethodSource("v3UpdateContents")
    void v4_수정은_그룹을_변경하지_않는_경로로_내용을_전달한다(
            String content, String memo, String uploadId, boolean keepAudio
    ) {
        // when / then
        client.put().uri("/api/v4/emotions/42").header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON).body("{\"state\":\"ANGRY\"," + content + "}")
                .exchange().expectStatus().isNoContent();
        verify(emotionCommandService).updateWithoutGroupChange(42L, DEVICE_PUBLIC_ID, EmotionState.ANGRY, memo, uploadId, keepAudio);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"11111111-1111-1111-1111-111111111111\"", "false", "{}", "[]"})
    void v4_등록과_수정은_본문의_groupId를_null도_포함하여_거부한다(String groupId) {
        // when / then
        for (var method : List.of(HttpMethod.POST, HttpMethod.PUT)) {
            String uri = method == HttpMethod.POST ? "/api/v4/emotions" : "/api/v4/emotions/42";
            client.method(method).uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(createBody("\"contentType\":\"MEMO\",\"memo\":\"메모\",\"groupId\":" + groupId))
                    .exchange().expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        }
        verifyNoInteractions(emotionCommandService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "\"true\"", "[]", "{}"})
    void v4도_익명_선택에_JSON_boolean만_허용한다(String anonymous) {
        // when / then
        client.post().uri("/api/v4/emotions").header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(createBody("\"contentType\":\"MEMO\",\"memo\":\"메모\",\"anonymous\":" + anonymous))
                .exchange().expectStatus().isBadRequest();
        verifyNoInteractions(emotionCommandService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"contentType\":\"NONE\"", "\"contentType\":\"MEMO\",\"memo\":\"   \"",
            "\"contentType\":\"AUDIO\",\"audioUploadId\":\" \""})
    void v4_등록과_수정도_잘못된_내용을_거부한다(String content) {
        // when / then
        for (var method : List.of(HttpMethod.POST, HttpMethod.PUT)) {
            client.method(method).uri(method == HttpMethod.POST ? "/api/v4/emotions" : "/api/v4/emotions/42")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").contentType(MediaType.APPLICATION_JSON)
                    .body(createBody(content)).exchange().expectStatus().isBadRequest();
        }
        verifyNoInteractions(emotionCommandService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"groupId=", "scope=GROUP"})
    void v4_등록과_수정도_쿼리의_그룹_입력을_거부한다(String query) {
        // when / then
        for (var method : List.of(HttpMethod.POST, HttpMethod.PUT)) {
            String uri = method == HttpMethod.POST ? "/api/v4/emotions" : "/api/v4/emotions/42";
            client.method(method).uri(uri + "?" + query).header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                    .contentType(MediaType.APPLICATION_JSON).body(createBody("\"contentType\":\"MEMO\",\"memo\":\"메모\""))
                    .exchange().expectStatus().isBadRequest();
        }
        verifyNoInteractions(emotionCommandService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "invalid"})
    void v4_수정도_잘못된_감정_ID를_거부한다(String id) {
        // when / then
        client.put().uri("/api/v4/emotions/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON).body("{\"state\":\"ANGRY\",\"contentType\":\"MEMO\",\"memo\":\"수정\"}")
                .exchange().expectStatus().isBadRequest();
        verifyNoInteractions(emotionCommandService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v4/emotions", "/api/v4/emotions/42"})
    void v4_등록과_수정도_인증이_필요하다(String uri) {
        // when / then
        var method = uri.endsWith("42") ? HttpMethod.PUT : HttpMethod.POST;
        request(method, uri, null).expectStatus().isUnauthorized();
        request(method, uri, "invalid-token").expectStatus().isUnauthorized();
        verifyNoInteractions(emotionCommandService);
    }

    @ParameterizedTest
    @MethodSource("v3AnonymousOptions")
    void v4는_그룹_없이_익명_선택을_전달하여_등록한다(String option, Boolean anonymous) {
        // given
        var saved = 기본_한숨_빌더().build();
        ReflectionTestUtils.setField(saved, "id", 42L);
        when(emotionCommandService.save(any(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any(), isNull(),
                eq(DEVICE_PUBLIC_ID), eq(anonymous))).thenReturn(saved);

        // when / then
        client.post().uri("/api/v4/emotions").header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON).body(createBody("\"contentType\":\"MEMO\",\"memo\":\"메모\"" + option))
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.LOCATION, "/api/v4/emotions/42")
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().json("{\"id\":42}", JsonCompareMode.STRICT);
        verify(emotionCommandService).save(UUID.fromString("5d1ad34e-1e20-4f20-a20e-3825a095fe6b"),
                EmotionState.FRUSTRATED, 126.97, 37.56, 35.5, "메모", null, null, DEVICE_PUBLIC_ID, anonymous);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALL", "MY"})
    void v4_핀은_다음_페이지에서_좌표_없이_필터와_커서를_전달한다(EmotionSearchScope scope) {
        // given
        var date = LocalDate.of(2026, 10, 1);
        when(emotionMapQueryService.findContentMapWithinBoundsV4(null, DEVICE_PUBLIC_ID, date, date,
                EmotionContentFilter.MEMO, scope, "v4-pin-next")).thenReturn(EmotionMapPageView.of(List.of(), false, null));

        // when / then
        request(HttpMethod.GET, "/api/v4/emotions/map?cursor=v4-pin-next&startDate=" + date + "&endDate=" + date
                + "&contentType=MEMO&scope=" + scope + "&devicePublicId=" + UUID.randomUUID(), "access-token")
                .expectStatus().isOk().expectBody().json("{\"items\":[],\"hasNext\":false,\"nextCursor\":null}", JsonCompareMode.STRICT);
        verify(emotionMapQueryService).findContentMapWithinBoundsV4(null, DEVICE_PUBLIC_ID, date, date,
                EmotionContentFilter.MEMO, scope, "v4-pin-next");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?minLongitude=127", "?cursor=next&minLongitude=127",
            "?minLongitude=127&minLatitude=NaN&maxLongitude=128&maxLatitude=38",
            "?minLongitude=127&minLatitude=37&maxLongitude=127&maxLatitude=38",
            "?minLongitude=127&minLatitude=37&maxLongitude=128&maxLatitude=38&startDate=2026-10-01",
            "?minLongitude=127&minLatitude=37&maxLongitude=128&maxLatitude=38&contentType=NONE"})
    void v4_핀은_잘못된_좌표와_페이지_필터를_거부한다(String query) {
        // when / then
        request(HttpMethod.GET, "/api/v4/emotions/map" + query, "access-token").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(emotionMapQueryService);
    }

    @Test
    void v4_핀도_잘못된_커서를_400으로_전달한다() {
        // given
        when(emotionMapQueryService.findContentMapWithinBoundsV4(null, DEVICE_PUBLIC_ID, null, null, null, null, "legacy"))
                .thenThrow(new EmotionException(EmotionErrorCode.EMOTION_INVALID_CURSOR));

        // when / then
        request(HttpMethod.GET, "/api/v4/emotions/map?cursor=legacy", "access-token").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo(EmotionErrorCode.EMOTION_INVALID_CURSOR.getCode());
    }

    @Test
    void v4_핀은_그룹_필드_없는_GeoJSON과_핀_커서를_반환한다() {
        // given
        var bounds = EmotionSearchBounds.of(127.8, 37.2, 128.2, 37.8);
        var group = com.pheeeew.groups.fixture.GroupFixture.기본_그룹_빌더().build();
        var stamp = com.pheeeew.groups.fixture.GroupFixture.기본_스탬프_빌더(group).build();
        var emotion = 기본_한숨_빌더().groupStamp(stamp).state(EmotionState.FRUSTRATED).build();
        ReflectionTestUtils.setField(emotion, "id", 42L);
        var pin = EmotionMapItemView.of(emotion, GroupStampResult.from(stamp));
        when(emotionMapQueryService.findContentMapWithinBoundsV4(bounds, DEVICE_PUBLIC_ID, null, null, null, null, null))
                .thenReturn(EmotionMapPageView.of(List.of(pin), true, "v4-pin-next"));

        // when / then
        request(HttpMethod.GET, "/api/v4/emotions/map" + REGION_BOUNDS_QUERY, "access-token").expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-cache, private")
                .expectBody().jsonPath("$.items[0].type").isEqualTo("Feature")
                .jsonPath("$.items[0].id").isEqualTo(42)
                .jsonPath("$.items[0].geometry.type").isEqualTo("Point")
                .jsonPath("$.items[0].properties.state").isEqualTo(emotion.getState().name())
                .jsonPath("$.items[0].properties.groupId").doesNotHaveJsonPath()
                .jsonPath("$.items[0].properties.groupStamp").doesNotHaveJsonPath()
                .jsonPath("$.nextCursor").isEqualTo("v4-pin-next");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALL", "MY"})
    void v4_목록은_필터와_커서를_인증된_기기로_전달한다(EmotionSearchScope scope) {
        // given
        var date = LocalDate.of(2026, 10, 1);
        when(emotionQueryService.findListWithoutBoundsV4(DEVICE_PUBLIC_ID, date, date, EmotionContentFilter.MEMO, scope, "v4-next"))
                .thenReturn(EmotionPageView.of(List.of(), false, null));

        // when / then
        request(HttpMethod.GET, "/api/v4/emotions?startDate=" + date + "&endDate=" + date
                + "&contentType=MEMO&scope=" + scope + "&cursor=v4-next&devicePublicId=" + UUID.randomUUID(), "access-token")
                .expectStatus().isOk().expectBody().json("{\"items\":[],\"hasNext\":false,\"nextCursor\":null}", JsonCompareMode.STRICT);
        verify(emotionQueryService).findListWithoutBoundsV4(DEVICE_PUBLIC_ID, date, date, EmotionContentFilter.MEMO, scope, "v4-next");
    }

    @ParameterizedTest
    @ValueSource(strings = {"startDate=2026-10-01", "startDate=2026-10-02&endDate=2026-10-01",
            "contentType=NONE", "scope=INVALID"})
    void v4_목록은_잘못된_필터를_서비스_호출_전에_거부한다(String query) {
        // when / then
        request(HttpMethod.GET, "/api/v4/emotions?" + query, "access-token").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(emotionQueryService);
    }

    @ParameterizedTest
    @CsvSource({"EMOTION_INVALID_CURSOR,400", "EMOTION_AUDIO_PLAYBACK_UNAVAILABLE,503"})
    void v4_목록도_커서와_녹음_실패를_공통_오류로_반환한다(EmotionErrorCode code, int status) {
        // given
        when(emotionQueryService.findListWithoutBoundsV4(DEVICE_PUBLIC_ID, null, null, null, null, "invalid"))
                .thenThrow(new EmotionException(code));

        // when / then
        request(HttpMethod.GET, "/api/v4/emotions?cursor=invalid", "access-token").expectStatus().isEqualTo(status)
                .expectBody().jsonPath("$.code").isEqualTo(code.getCode());
    }

    @Test
    void v4_목록은_그룹_정보_없는_감정과_다음_커서를_반환한다() {
        // given
        var group = com.pheeeew.groups.fixture.GroupFixture.기본_그룹_빌더().build();
        var stamp = com.pheeeew.groups.fixture.GroupFixture.기본_스탬프_빌더(group).build();
        var emotion = 기본_한숨_빌더().groupStamp(stamp).memo("기록").build();
        ReflectionTestUtils.setField(emotion, "id", 42L);
        var view = EmotionDetailView.of(emotion, List.of(), null, GroupStampResult.from(stamp));
        when(emotionQueryService.findListWithoutBoundsV4(DEVICE_PUBLIC_ID, null, null, null, null, null))
                .thenReturn(EmotionPageView.of(List.of(view), true, "v4-next"));

        // when / then
        request(HttpMethod.GET, "/api/v4/emotions", "access-token").expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-cache, private")
                .expectBody().jsonPath("$.items[0].id").isEqualTo(42)
                .jsonPath("$.items[0].properties.memo").isEqualTo("기록")
                .jsonPath("$.items[0].properties.groupId").doesNotHaveJsonPath()
                .jsonPath("$.items[0].properties.groupStamp").doesNotHaveJsonPath()
                .jsonPath("$.hasNext").isEqualTo(true)
                .jsonPath("$.nextCursor").isEqualTo("v4-next");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void v4_상세는_그룹_정보_없이_기존_감정_내용을_반환한다(boolean hasGroup) throws Exception {
        // given
        var group = com.pheeeew.groups.fixture.GroupFixture.기본_그룹_빌더().build();
        var stamp = com.pheeeew.groups.fixture.GroupFixture.기본_스탬프_빌더(group).build();
        var emotion = 기본_한숨_빌더().groupStamp(hasGroup ? stamp : null).memo("답답한 하루").deviceId(1L).build();
        ReflectionTestUtils.setField(emotion, "id", 42L);
        var grouped = EmotionDetailView.of(emotion, detailView(42L, true).emojis(), null,
                hasGroup ? GroupStampResult.from(stamp) : null, 1L);
        when(emotionQueryService.findById(42L, DEVICE_PUBLIC_ID)).thenReturn(grouped);

        // when / then: 그룹 필드 이외의 모든 응답 값이 기존 상세와 일치해야 한다.
        byte[] legacyBody = request(HttpMethod.GET, EMOTION_URI, "access-token").expectStatus().isOk()
                .expectBody().returnResult().getResponseBody();
        byte[] v4Body = request(HttpMethod.GET, "/api/v4/emotions/42", "access-token").expectStatus().isOk()
                .expectHeader().contentType(GEO_JSON)
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-cache, private")
                .expectBody().returnResult().getResponseBody();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var expected = mapper.readTree(legacyBody);
        ((com.fasterxml.jackson.databind.node.ObjectNode) expected.get("properties")).remove(List.of("groupId", "groupStamp"));
        assertThat(mapper.readTree(v4Body)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"SIDO,11,", "SIGUNGU,11010,11", "EMD,11010530,11010"})
    void v4_지역_요약은_그룹_없이_기존_지역_응답을_유지한다(
            RegionLevel level, String code, String parentCode
    ) {
        // given
        var bounds = EmotionSearchBounds.of(127.8, 37.2, 128.2, 37.8);
        var filter = EmotionSearchFilter.from(null);
        var region = Region.of(code, level, "검증용 지역", parentCode, 127, 38);
        var item = EmotionRegionMapItemView.of(region, RegionEmotionSummary.of(3L, EmotionState.ANGRY));
        when(emotionMapQueryService.findRegionMap(bounds, level, DEVICE_PUBLIC_ID, filter)).thenReturn(List.of(item));

        // when / then
        request(HttpMethod.GET, "/api/v4/emotions/map/regions" + REGION_BOUNDS_QUERY + "&level=" + level,
                "access-token").expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-cache, private")
                .expectBody().json("""
                        [{"type":"Feature","id":"%s",
                         "geometry":{"type":"Point","coordinates":[127.0,38.0]},
                         "properties":{"level":"%s","name":"검증용 지역","parentCode":%s,
                         "totalCount":3,"representativeState":"ANGRY"}}]
                        """.formatted(code, level, parentCode == null ? "null" : "\"" + parentCode + "\""), JsonCompareMode.STRICT);
        verify(emotionMapQueryService).findRegionMap(bounds, level, DEVICE_PUBLIC_ID, filter);
    }

    @ParameterizedTest
    @ValueSource(strings = {"groupId", "groupId=", "groupId=null",
            "groupId=11111111-1111-1111-1111-111111111111", "groupId=&groupId=invalid", "scope=GROUP"})
    void v4_조회는_그룹_입력을_값과_무관하게_400으로_거부한다(String query) {
        // when / then
        for (String uri : List.of("/api/v4/emotions?", "/api/v4/emotions/42?",
                "/api/v4/emotions/map" + REGION_BOUNDS_QUERY + "&",
                "/api/v4/emotions/map/regions" + REGION_BOUNDS_QUERY + "&level=SIDO&")) {
            request(HttpMethod.GET, uri + query, "access-token").expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        }
        verifyNoInteractions(emotionQueryService, emotionMapQueryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "invalid"})
    void v4_상세도_잘못된_감정_ID를_400으로_거부한다(String id) {
        // when / then
        request(HttpMethod.GET, "/api/v4/emotions/" + id, "access-token").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(emotionQueryService);
    }

    @ParameterizedTest
    @CsvSource({"SIDO,ALL,ALL", "SIGUNGU,MEMO,MY", "EMD,AUDIO,MY"})
    void v4_지역_요약도_필터와_인증된_기기를_유지한다(
            RegionLevel level, EmotionContentFilter contentType, EmotionSearchScope scope
    ) {
        // given
        var bounds = EmotionSearchBounds.of(170, 37, -170, 39);
        var date = LocalDate.of(2026, 10, 1);
        var filter = EmotionSearchFilter.of(date, date, contentType, scope, null);
        when(emotionMapQueryService.findRegionMap(bounds, level, DEVICE_PUBLIC_ID, filter)).thenReturn(List.of());

        // when / then
        request(HttpMethod.GET, "/api/v4/emotions/map/regions?minLongitude=170&minLatitude=37"
                + "&maxLongitude=-170&maxLatitude=39&level=" + level + "&startDate=" + date + "&endDate=" + date
                + "&contentType=" + contentType + "&scope=" + scope + "&devicePublicId=" + UUID.randomUUID(),
                "access-token").expectStatus().isOk().expectBody().json("[]", JsonCompareMode.STRICT);
        verify(emotionMapQueryService).findRegionMap(bounds, level, DEVICE_PUBLIC_ID, filter);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "&minLatitude=NaN", "&maxLongitude=181", "&minLongitude=128.2",
            "&minLatitude=37.8", "&level=CITY", "&startDate=2026-10-01",
            "&startDate=2026-10-02&endDate=2026-10-01", "&contentType=NONE", "&scope=INVALID"})
    void v4_지역_요약도_잘못된_검색_조건을_거부한다(String query) {
        // when / then
        var parameters = new HashMap<>(Map.of("minLongitude", "127.8", "minLatitude", "37.2",
                "maxLongitude", "128.2", "maxLatitude", "37.8", "level", "SIDO"));
        if (query.isEmpty()) {
            parameters.remove("minLatitude");
        } else {
            for (String entry : query.substring(1).split("&")) {
                String[] pair = entry.split("=", 2);
                parameters.put(pair[0], pair[1]);
            }
        }
        String queryString = parameters.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        request(HttpMethod.GET, "/api/v4/emotions/map/regions?" + queryString, "access-token").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(emotionQueryService, emotionMapQueryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v4/emotions", "/api/v4/emotions/42", "/api/v4/emotions/map", "/api/v4/emotions/map/regions"})
    void v4_조회도_인증이_필요하며_삭제는_허용하지_않는다(String uri) {
        // when / then
        request(HttpMethod.GET, uri, null).expectStatus().isUnauthorized();
        request(HttpMethod.GET, uri, "invalid-token").expectStatus().isUnauthorized();
        request(HttpMethod.DELETE, uri, "access-token").expectStatus().isForbidden();
        verifyNoInteractions(emotionQueryService, emotionMapQueryService, emotionCommandService);
    }

    @ParameterizedTest
    @MethodSource("detailFailures")
    void v4_상세도_기존_조회_오류를_전달한다(RuntimeException exception, int status, String code) {
        // given
        when(emotionQueryService.findById(42L, DEVICE_PUBLIC_ID)).thenThrow(exception);

        // when / then
        request(HttpMethod.GET, "/api/v4/emotions/42", "access-token").expectStatus().isEqualTo(status)
                .expectBody().jsonPath("$.code").isEqualTo(code);
    }

    @Test
    void v4_지역_요약도_준비_실패를_503으로_전달한다() {
        // given
        when(emotionMapQueryService.findRegionMap(EmotionSearchBounds.of(127.8, 37.2, 128.2, 37.8),
                RegionLevel.SIDO, DEVICE_PUBLIC_ID, EmotionSearchFilter.from(null)))
                .thenThrow(new EmotionException(EmotionErrorCode.EMOTION_REGION_DATA_UNAVAILABLE));

        // when / then
        request(HttpMethod.GET, "/api/v4/emotions/map/regions" + REGION_BOUNDS_QUERY + "&level=SIDO", "access-token")
                .expectStatus().isEqualTo(503).expectBody().jsonPath("$.code").isEqualTo("EMOTION-013");
    }

    @ParameterizedTest
    @CsvSource({
            "/api/v1/emotions/42,$.properties",
            "/api/v4/emotions/42,$.properties",
            "/api/v1/emotions?minLongitude=126&minLatitude=37&maxLongitude=128&maxLatitude=38,$.items[0].properties",
            "/api/v3/emotions,$.items[0].properties"
    })
    void 녹음_목록과_상세는_내용_유형과_메모와_재생_정보를_순서대로_반환한다(String uri, String path) {
        // given
        Emotion emotion = 기본_한숨_빌더()
                .state(EmotionState.FRUSTRATED).rotationDegrees(0).deviceId(1L)
                .audio(Audio.builder().objectKey("private/voice.m4a").build()).build();
        var playback = PlaybackUrl.of(
                "https://audio.example.test/signed", Instant.parse("2026-09-25T12:05:00Z"));
        var view = EmotionDetailView.of(emotion, List.of(), playback);
        when(emotionQueryService.findById(42L, DEVICE_PUBLIC_ID)).thenReturn(view);
        when(emotionQueryService.findListWithinBounds(any(), eq(DEVICE_PUBLIC_ID), any(), isNull()))
                .thenReturn(EmotionPageView.of(List.of(view), false, null));
        when(emotionQueryService.findListWithoutBounds(DEVICE_PUBLIC_ID, null, null, null, null, null, null))
                .thenReturn(EmotionPageView.of(List.of(view), false, null));

        // when / then
        request(HttpMethod.GET, uri, "access-token").expectStatus().isOk().expectBody()
                .jsonPath(path + ".contentType").isEqualTo("AUDIO")
                .jsonPath(path + ".audio.playbackUrl").isEqualTo(playback.playbackUrl())
                .jsonPath(path + ".audio.expiresAt").isEqualTo("2026-09-25T12:05:00Z")
                .jsonPath(path + ".audio.objectKey").doesNotExist()
                .consumeWith(result -> assertThat(new String(result.getResponseBody(), StandardCharsets.UTF_8))
                        .containsPattern("\"contentType\"\\s*:\\s*\"AUDIO\"\\s*,\\s*\"memo\"\\s*:\\s*null\\s*,\\s*\"audio\"\\s*:"));
    }

    @Test
    void 그룹_스탬프는_목록과_상세에서_같은_모양으로_반환한다() {
        // given
        Emotion emotion = com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더().build();
        var stamp = new GroupStampResult("모임", "#FFFFFF", "#000000", StampFrame.CIRCLE);
        EmotionDetailView view = EmotionDetailView.of(emotion, List.of(), null, stamp);
        when(emotionQueryService.findById(42L, DEVICE_PUBLIC_ID)).thenReturn(view);
        when(emotionQueryService.findListWithinBounds(any(), eq(DEVICE_PUBLIC_ID), any(), isNull()))
                .thenReturn(EmotionPageView.of(List.of(view), false, null));
        when(emotionQueryService.findListWithoutBounds(DEVICE_PUBLIC_ID, null, null, null, null, null, null))
                .thenReturn(EmotionPageView.of(List.of(view), false, null));

        // when / then
        request(HttpMethod.GET, EMOTION_URI, "access-token").expectStatus().isOk().expectBody()
                .jsonPath("$.properties.groupStamp.text").isEqualTo("모임")
                .jsonPath("$.properties.groupStamp.textColor").isEqualTo("#FFFFFF")
                .jsonPath("$.properties.groupStamp.backgroundColor").isEqualTo("#000000")
                .jsonPath("$.properties.groupStamp.frame").isEqualTo("CIRCLE");
        request(HttpMethod.GET, "/api/v1/emotions?minLongitude=126&minLatitude=37&maxLongitude=128&maxLatitude=38",
                "access-token").expectStatus().isOk().expectBody()
                .jsonPath("$.items[0].properties.groupStamp.text").isEqualTo("모임");
        request(HttpMethod.GET, "/api/v3/emotions", "access-token").expectStatus().isOk().expectBody()
                .jsonPath("$.items[0].properties.groupStamp.text").isEqualTo("모임");
    }

    @ParameterizedTest
    @MethodSource("updateContents")
    void 수정_내용과_그룹을_인증된_기기로_전달하고_204를_반환한다(
            String content, String memo, String uploadId, boolean keepAudio) {
        // given
        UUID groupId = UUID.randomUUID();
        String body = "{\"state\":\"ANGRY\",\"groupId\":\"" + groupId + "\"," + content + "}";

        // when / then
        client.put().uri(EMOTION_URI + "?devicePublicId=" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").contentType(MediaType.APPLICATION_JSON)
                .body(body).exchange().expectStatus().isNoContent().expectBody().isEmpty();
        verify(emotionCommandService).update(42L, DEVICE_PUBLIC_ID, EmotionState.ANGRY, memo, uploadId, keepAudio, groupId);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"state\":\"ANGRY\"}", "{\"contentType\":\"NONE\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"NONE\",\"memo\":\"메모\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"AUDIO\",\"memo\":\"메모\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"AUDIO\",\"audioUploadId\":\" \"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"MEMO\",\"audioUploadId\":\"upload\"}"
    })
    void 잘못된_수정_내용은_서비스를_호출하지_않는다(String body) {
        client.put().uri(EMOTION_URI).header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isBadRequest();
        verifyNoInteractions(emotionCommandService);
    }

    @ParameterizedTest
    @ValueSource(strings = {EMOTION_URI, V3_EMOTION_URI})
    void 수정_메모가_200자를_초과하면_거부한다(String uri) {
        client.put().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"state\":\"ANGRY\",\"contentType\":\"MEMO\",\"memo\":\"" + "가".repeat(201) + "\"}")
                .exchange().expectStatus().isBadRequest();
        verifyNoInteractions(emotionCommandService);
    }

    @Test
    void 삭제는_인증된_기기로_위임하고_204를_반환한다() {
        request(HttpMethod.DELETE, EMOTION_URI + "?devicePublicId=" + UUID.randomUUID(), "access-token")
                .expectStatus().isNoContent().expectBody().isEmpty();
        verify(emotionCommandService).delete(42L, DEVICE_PUBLIC_ID);
    }

    @ParameterizedTest
    @MethodSource("unauthenticatedRequests")
    void 수정과_삭제는_인증이_필요하다(HttpMethod method, String token) {
        request(method, EMOTION_URI, token).expectStatus().isUnauthorized();
        verifyNoInteractions(emotionCommandService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMOTION_NOT_VISIBLE", "EMOTION_AUDIO_REQUIRED", "EMOTION_AUDIO_UPLOAD_UNAVAILABLE"})
    void 수정_서비스의_실패_코드를_반환한다(String name) {
        EmotionErrorCode error = EmotionErrorCode.valueOf(name);
        doThrow(new EmotionException(error)).when(emotionCommandService)
                .update(42L, DEVICE_PUBLIC_ID, EmotionState.ANGRY, null, null, true, null);
        client.put().uri(EMOTION_URI).header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON).body("{\"state\":\"ANGRY\",\"contentType\":\"AUDIO\"}")
                .exchange().expectStatus().isEqualTo(error.getStatus().value())
                .expectBody().jsonPath("$.code").isEqualTo(error.getCode());
    }

    @ParameterizedTest
    @MethodSource("v3UpdateContents")
    void v3_수정은_내용과_그룹을_인증된_기기로_전달한다(String content, String memo, String uploadId, boolean keepAudio) {
        // given
        UUID groupId = UUID.randomUUID();
        String body = "{\"state\":\"ANGRY\",\"groupId\":\"" + groupId + "\"," + content
                + ",\"anonymous\":false,\"longitude\":0,\"latitude\":0,\"rotationDegrees\":0}";

        // when / then
        client.put().uri(V3_EMOTION_URI + "?devicePublicId=" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").contentType(MediaType.APPLICATION_JSON)
                .body(body).exchange().expectStatus().isNoContent().expectBody().isEmpty();
        verify(emotionCommandService).update(42L, DEVICE_PUBLIC_ID, EmotionState.ANGRY, memo, uploadId, keepAudio, groupId);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"state\":\"ANGRY\"}", "{\"contentType\":\"MEMO\",\"memo\":\"메모\"}",
            "{\"state\":\"ANGRY\",\"contentType\":null}",
            "{\"state\":\"ANGRY\",\"contentType\":\"NONE\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"MEMO\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"MEMO\",\"memo\":null}",
            "{\"state\":\"ANGRY\",\"contentType\":\"MEMO\",\"memo\":\"\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"MEMO\",\"memo\":\"   \"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"MEMO\",\"memo\":\"메모\",\"audioUploadId\":\"upload\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"AUDIO\",\"memo\":\"메모\"}",
            "{\"state\":\"ANGRY\",\"contentType\":\"AUDIO\",\"audioUploadId\":\" \"}"
    })
    void v3의_잘못된_수정_내용은_서비스_호출_전에_거부한다(String body) {
        // when / then
        client.put().uri(V3_EMOTION_URI).header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isBadRequest();
        verifyNoInteractions(emotionCommandService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v3/emotions/0", "/api/v3/emotions/-1"})
    void v3_수정의_감정_ID는_양수여야_한다(String uri) {
        // when / then
        client.put().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON).body("{\"state\":\"ANGRY\",\"contentType\":\"AUDIO\"}")
                .exchange().expectStatus().isBadRequest();
        verifyNoInteractions(emotionCommandService);
    }

    @Test
    void v3_수정은_기기_인증이_필수다() {
        // when / then
        request(HttpMethod.PUT, V3_EMOTION_URI, null).expectStatus().isUnauthorized();
        request(HttpMethod.PUT, V3_EMOTION_URI, "invalid-token").expectStatus().isUnauthorized();
        verifyNoInteractions(emotionCommandService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMOTION_NOT_VISIBLE", "EMOTION_AUDIO_REQUIRED", "EMOTION_AUDIO_UPLOAD_NOT_FOUND",
            "EMOTION_AUDIO_UPLOAD_NOT_READY", "EMOTION_AUDIO_UPLOAD_ALREADY_USED", "EMOTION_AUDIO_UPLOAD_UNAVAILABLE"})
    void v3_수정도_기존_권한과_녹음_실패_코드를_반환한다(String name) {
        // given
        EmotionErrorCode error = EmotionErrorCode.valueOf(name);
        doThrow(new EmotionException(error)).when(emotionCommandService)
                .update(42L, DEVICE_PUBLIC_ID, EmotionState.ANGRY, null, null, true, null);

        // when / then
        client.put().uri(V3_EMOTION_URI).header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON).body("{\"state\":\"ANGRY\",\"contentType\":\"AUDIO\"}")
                .exchange().expectStatus().isEqualTo(error.getStatus().value())
                .expectBody().jsonPath("$.code").isEqualTo(error.getCode());
    }

    @Test
    void 삭제_권한이_없으면_404를_반환한다() {
        doThrow(new EmotionException(EmotionErrorCode.EMOTION_NOT_VISIBLE))
                .when(emotionCommandService).delete(42L, DEVICE_PUBLIC_ID);
        request(HttpMethod.DELETE, EMOTION_URI, "access-token").expectStatus().isNotFound();
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
        when(emotionCommandService.save(any(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any(), any(), eq(DEVICE_PUBLIC_ID), any()))
                .thenReturn(saved);
        client.post().uri("/api/v1/emotions?devicePublicId=" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").contentType(MediaType.APPLICATION_JSON)
                .body(createBody(content)).exchange().expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.LOCATION, EMOTION_URI)
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().json("{\"id\":42}", JsonCompareMode.STRICT);
        verify(emotionCommandService).save(UUID.fromString("5d1ad34e-1e20-4f20-a20e-3825a095fe6b"),
                EmotionState.FRUSTRATED, 126.97, 37.56, 35.5,
                content.contains("MEMO") ? "메모" : null, content.contains("AUDIO") ? "upload" : null, null, DEVICE_PUBLIC_ID, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v2/emotions", "/api/v3/emotions"})
    void 내용_필수_등록은_NONE과_빈_메모_및_불일치_조합을_거부한다(String uri) {
        // given
        List<String> contents = List.of(
                "\"contentType\":\"NONE\"", "\"contentType\":null", "\"memo\":\"메모\"",
                "\"contentType\":\"MEMO\"", "\"contentType\":\"MEMO\",\"memo\":null",
                "\"contentType\":\"MEMO\",\"memo\":\"\"",
                "\"contentType\":\"MEMO\",\"memo\":\"  \u2003  \"",
                "\"contentType\":\"MEMO\",\"memo\":\"메모\",\"audioUploadId\":\"upload\"",
                "\"contentType\":\"AUDIO\"", "\"contentType\":\"AUDIO\",\"audioUploadId\":\" \"",
                "\"contentType\":\"AUDIO\",\"audioUploadId\":\"upload\",\"memo\":\"메모\"");

        // when / then
        for (String content : contents) {
            client.post().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                    .contentType(MediaType.APPLICATION_JSON).body(createBody(content))
                    .exchange().expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        }
        verifyNoInteractions(emotionCommandService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v2/emotions", "/api/v3/emotions"})
    void 내용_필수_등록은_완료된_녹음_식별자와_그룹을_전달한다(String uri) {
        // given
        UUID groupId = UUID.randomUUID();
        Emotion saved = 기본_한숨_빌더().build();
        ReflectionTestUtils.setField(saved, "id", 42L);
        when(emotionCommandService.save(any(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any(), any(),
                eq(DEVICE_PUBLIC_ID), any())).thenReturn(saved);

        // when / then
        client.post().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(createBody("\"contentType\":\"AUDIO\",\"audioUploadId\":\"upload\",\"groupId\":\"" + groupId + "\""))
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.LOCATION, EMOTION_URI)
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().json("{\"id\":42}", JsonCompareMode.STRICT);
        verify(emotionCommandService).save(UUID.fromString("5d1ad34e-1e20-4f20-a20e-3825a095fe6b"),
                EmotionState.FRUSTRATED, 126.97, 37.56, 35.5, null, "upload", groupId, DEVICE_PUBLIC_ID, null);
    }

    @Test
    void v2는_메모를_등록하고_익명_선택을_무시한다() {
        // given
        Emotion saved = 기본_한숨_빌더().build();
        ReflectionTestUtils.setField(saved, "id", 42L);
        when(emotionCommandService.save(any(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any(), any(),
                eq(DEVICE_PUBLIC_ID), eq(null))).thenReturn(saved);

        // when / then
        client.post().uri("/api/v2/emotions?devicePublicId=" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").contentType(MediaType.APPLICATION_JSON)
                .body(createBody("\"contentType\":\"MEMO\",\"memo\":\"  메모  \" ,\"anonymous\":false"))
                .exchange().expectStatus().isOk();
        verify(emotionCommandService).save(UUID.fromString("5d1ad34e-1e20-4f20-a20e-3825a095fe6b"),
                EmotionState.FRUSTRATED, 126.97, 37.56, 35.5, "  메모  ", null, null, DEVICE_PUBLIC_ID, null);
    }

    @Test
    void 선택한_그룹_ID를_등록_요청에_전달한다() {
        // given
        UUID groupId = UUID.randomUUID();
        Emotion saved = com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더().build();
        ReflectionTestUtils.setField(saved, "id", 42L);
        when(emotionCommandService.save(any(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any(),
                eq(groupId), eq(DEVICE_PUBLIC_ID), any())).thenReturn(saved);

        // when / then
        client.post().uri("/api/v1/emotions").header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(createBody("\"contentType\":\"NONE\",\"groupId\":\"" + groupId + "\""))
                .exchange().expectStatus().isOk();
        verify(emotionCommandService).save(UUID.fromString("5d1ad34e-1e20-4f20-a20e-3825a095fe6b"),
                EmotionState.FRUSTRATED, 126.97, 37.56, 35.5, null, null, groupId, DEVICE_PUBLIC_ID, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "true", "false", "0", "1", "\"false\"", "\"true\"", "\"invalid\"", "[]", "{}"})
    void v1은_익명_선택을_무시하고_인증된_기기로_익명_등록한다(String anonymous) {
        // given
        Emotion saved = 기본_한숨_빌더().build();
        ReflectionTestUtils.setField(saved, "id", 42L);
        when(emotionCommandService.save(any(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any(), any(),
                eq(DEVICE_PUBLIC_ID), eq(null))).thenReturn(saved);

        // when / then
        client.post().uri("/api/v1/emotions?devicePublicId=" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").contentType(MediaType.APPLICATION_JSON)
                .body(createBody("\"contentType\":\"NONE\",\"anonymous\":" + anonymous))
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.LOCATION, EMOTION_URI)
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().json("{\"id\":42}", JsonCompareMode.STRICT);
        verify(emotionCommandService).save(UUID.fromString("5d1ad34e-1e20-4f20-a20e-3825a095fe6b"),
                EmotionState.FRUSTRATED, 126.97, 37.56, 35.5, null, null, null, DEVICE_PUBLIC_ID, null);
    }

    @ParameterizedTest
    @MethodSource("v3AnonymousOptions")
    void v3의_익명_선택과_등록_응답을_전달한다(String option, Boolean anonymous) {
        // given
        Emotion saved = 기본_한숨_빌더().build();
        ReflectionTestUtils.setField(saved, "id", 42L);
        when(emotionCommandService.save(any(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any(), any(),
                eq(DEVICE_PUBLIC_ID), eq(anonymous))).thenReturn(saved);

        // when / then
        client.post().uri("/api/v3/emotions?devicePublicId=" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON).body(createBody("\"contentType\":\"MEMO\",\"memo\":\"메모\"" + option))
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.LOCATION, EMOTION_URI)
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().json("{\"id\":42}", JsonCompareMode.STRICT);
        verify(emotionCommandService).save(UUID.fromString("5d1ad34e-1e20-4f20-a20e-3825a095fe6b"),
                EmotionState.FRUSTRATED, 126.97, 37.56, 35.5, "메모", null, null, DEVICE_PUBLIC_ID, anonymous);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "\"false\"", "\"true\"", "\"invalid\"", "[]", "{}"})
    void v3의_익명_선택은_JSON_boolean만_허용한다(String anonymous) {
        // when / then
        client.post().uri("/api/v3/emotions").header(HttpHeaders.AUTHORIZATION, "Bearer access-token").contentType(MediaType.APPLICATION_JSON)
                .body(createBody("\"contentType\":\"MEMO\",\"memo\":\"메모\",\"anonymous\":" + anonymous))
                .exchange().expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(emotionCommandService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"contentType\":\"NONE\",\"memo\":\"메모\"", "\"contentType\":\"AUDIO\"", "\"contentType\":null"})
    void v3에서도_잘못된_내용_조합은_저장하지_않는다(String content) {
        // when / then
        client.post().uri("/api/v3/emotions").header(HttpHeaders.AUTHORIZATION, "Bearer access-token").contentType(MediaType.APPLICATION_JSON)
                .body(createBody(content)).exchange().expectStatus().isBadRequest();
        verifyNoInteractions(emotionCommandService);
    }

    @Test
    void v3에서_닉네임_없는_기명_등록은_409를_반환한다() {
        // given
        when(emotionCommandService.save(any(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any(), any(),
                eq(DEVICE_PUBLIC_ID), eq(false))).thenThrow(new DeviceException(DeviceErrorCode.DEVICE_NICKNAME_REQUIRED));

        // when / then
        client.post().uri("/api/v3/emotions").header(HttpHeaders.AUTHORIZATION, "Bearer access-token").contentType(MediaType.APPLICATION_JSON)
                .body(createBody("\"contentType\":\"MEMO\",\"memo\":\"메모\",\"anonymous\":false"))
                .exchange().expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("DEVICE-010");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"contentType\":\"NONE\",\"memo\":\"메모\"", "\"contentType\":\"AUDIO\"", "\"contentType\":null"})
    void 잘못된_내용_조합은_저장하지_않는다(String content) {
        client.post().uri("/api/v1/emotions").header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON).body(createBody(content)).exchange().expectStatus().isBadRequest();
        verifyNoInteractions(emotionCommandService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/emotions", "/api/v2/emotions", "/api/v3/emotions", "/api/v4/emotions"})
    void 등록_필수값과_좌표_각도_메모_길이를_검증한다(String uri) {
        String valid = createBody("\"contentType\":\"MEMO\",\"memo\":\"메모\"");
        for (String body : List.of(valid.replace("35.5", "360"), valid.replace("126.97", "181"),
                valid.replace("37.56", "-91"), valid.replace("\"FRUSTRATED\"", "null"),
                valid.replace("\"5d1ad34e-1e20-4f20-a20e-3825a095fe6b\"", "null"),
                createBody("\"contentType\":\"MEMO\",\"memo\":\"" + "가".repeat(201) + "\""))) {
            client.post().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                    .contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isBadRequest();
        }
        verifyNoInteractions(emotionCommandService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/emotions", "/api/v2/emotions", "/api/v3/emotions"})
    void 등록은_기기_인증이_필수다(String uri) {
        request(HttpMethod.POST, uri, null).expectStatus().isUnauthorized();
        request(HttpMethod.POST, uri, "invalid-token").expectStatus().isUnauthorized();
        verifyNoInteractions(emotionCommandService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMOTION_REQUEST_ID_CONFLICT", "EMOTION_AUDIO_UPLOAD_NOT_FOUND", "EMOTION_AUDIO_UPLOAD_NOT_READY",
            "EMOTION_AUDIO_UPLOAD_ALREADY_USED", "EMOTION_AUDIO_UPLOAD_UNAVAILABLE"})
    void 등록_실패_상태와_코드를_전달한다(String name) {
        EmotionErrorCode error = EmotionErrorCode.valueOf(name);
        when(emotionCommandService.save(any(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any(), any(), any(), any()))
                .thenThrow(new EmotionException(error));
        client.post().uri("/api/v1/emotions").header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON).body(createBody("\"contentType\":\"AUDIO\",\"audioUploadId\":\"upload\""))
                .exchange().expectStatus().isEqualTo(error.getStatus().value()).expectBody().jsonPath("$.code").isEqualTo(error.getCode());
    }

    @ParameterizedTest
    @MethodSource("listWithoutBoundsRequests")
    void 좌표_없는_목록은_그룹과_커서를_인증된_기기로_조회하고_기존_응답을_유지한다(String query, UUID groupId, String cursor) {
        // given
        when(emotionQueryService.findListWithoutBounds(DEVICE_PUBLIC_ID, groupId == null ? null : List.of(groupId), null, null, null, null, cursor))
                .thenReturn(EmotionPageView.of(List.of(detailView(42L, true), detailView(43L, false)), true, "next-page"));
        String separator = query.isEmpty() ? "?" : "&";

        // when / then: 조회 기기는 쿼리 값으로 바꿀 수 없다.
        request(HttpMethod.GET, "/api/v3/emotions" + query + separator + "devicePublicId=" + UUID.randomUUID(), "access-token")
                .expectStatus().isOk().expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-cache, private")
                .expectBody().jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.items[0].type").isEqualTo("Feature")
                .jsonPath("$.items[0].geometry.coordinates[0]").isEqualTo(126.9774)
                .jsonPath("$.items[0].geometry.coordinates[1]").isEqualTo(37.5669)
                .jsonPath("$.items[0].properties.contentType").isEqualTo("MEMO")
                .jsonPath("$.items[0].properties.nickname").isEqualTo("익명")
                .jsonPath("$.items[0].properties.emojis.length()").isEqualTo(6)
                .jsonPath("$.items[0].properties.emojis[0].selected").isEqualTo(true)
                .jsonPath("$.items[0].properties.isMine").isEqualTo(true)
                .jsonPath("$.items[1].properties.isMine").isEqualTo(false)
                .jsonPath("$.hasNext").isEqualTo(true).jsonPath("$.nextCursor").isEqualTo("next-page");
        verify(emotionQueryService).findListWithoutBounds(DEVICE_PUBLIC_ID, groupId == null ? null : List.of(groupId), null, null, null, null, cursor);
    }

    @Test
    void 좌표_없는_목록에_좌표를_전달해도_조회_조건에_포함하지_않고_빈_결과도_그대로_반환한다() {
        // given
        when(emotionQueryService.findListWithoutBounds(DEVICE_PUBLIC_ID, null, null, null, null, null, null))
                .thenReturn(EmotionPageView.of(List.of(), false, null));

        // when / then
        request(HttpMethod.GET, "/api/v3/emotions?minLongitude=invalid&minLatitude=37&maxLongitude=128&maxLatitude=38", "access-token")
                .expectStatus().isOk().expectBody()
                .json("{\"items\":[],\"hasNext\":false,\"nextCursor\":null}", JsonCompareMode.STRICT);
        verify(emotionQueryService).findListWithoutBounds(DEVICE_PUBLIC_ID, null, null, null, null, null, null);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "invalid-token")
    void 좌표_없는_목록도_유효한_기기_인증이_필요하다(String token) {
        request(HttpMethod.GET, "/api/v3/emotions", token).expectStatus().isUnauthorized();
        verifyNoInteractions(emotionQueryService);
    }

    @Test
    void 좌표_없는_목록의_허용_범위는_정확한_GET_경로로_제한한다() {
        request(HttpMethod.PUT, "/api/v3/emotions", "access-token").expectStatus().isForbidden();
        request(HttpMethod.DELETE, "/api/v3/emotions", "access-token").expectStatus().isForbidden();
        request(HttpMethod.GET, "/api/v3/emotions/42", "access-token").expectStatus().isForbidden();
        verifyNoInteractions(emotionQueryService);
        verifyNoInteractions(emotionCommandService);
    }

    @Test
    void 좌표_없는_목록의_잘못된_그룹_ID는_전체_조회로_처리하지_않는다() {
        request(HttpMethod.GET, "/api/v3/emotions?groupId=invalid", "access-token").expectStatus().isBadRequest();
        verifyNoInteractions(emotionQueryService);
    }

    @ParameterizedTest
    @CsvSource({"ALL,ALL", "MEMO,GROUP", "AUDIO,MY"})
    void 목록_필터는_타입을_변환하고_인증된_기기로_전달한다(EmotionContentFilter contentType, EmotionSearchScope scope) {
        // given
        UUID groupId = scope == EmotionSearchScope.GROUP ? UUID.randomUUID() : null;
        LocalDate date = LocalDate.of(2026, 10, 1);
        when(emotionQueryService.findListWithoutBounds(DEVICE_PUBLIC_ID, groupId == null ? null : List.of(groupId), date, date, contentType, scope, null))
                .thenReturn(EmotionPageView.of(List.of(), false, null));
        String query = "?startDate=2026-10-01&endDate=2026-10-01&contentType=" + contentType + "&scope=" + scope
                + (groupId == null ? "" : "&groupId=" + groupId) + "&devicePublicId=" + UUID.randomUUID();

        // when / then
        request(HttpMethod.GET, "/api/v3/emotions" + query, "access-token").expectStatus().isOk();
        verify(emotionQueryService).findListWithoutBounds(DEVICE_PUBLIC_ID, groupId == null ? null : List.of(groupId), date, date, contentType, scope, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "startDate=2026-10-01", "endDate=2026-10-01", "startDate=2026-10-02&endDate=2026-10-01",
            "startDate=2026-02-30&endDate=2026-03-01", "startDate=invalid&endDate=2026-10-01",
            "startDate=0000-01-01&endDate=2026-10-01", "startDate=-0001-01-01&endDate=2026-10-01",
            "startDate=2026-10-01&endDate=+10000-01-01", "startDate=2026-10-01&endDate=+999999998-01-01",
            "contentType=NONE", "contentType=invalid", "scope=invalid", "scope=GROUP",
            "scope=ALL&groupId=11111111-1111-4111-8111-111111111111",
            "scope=MY&groupId=11111111-1111-4111-8111-111111111111",
            "cursor=next&startDate=2026-10-01",
            "cursor=next&scope=MY&groupId=11111111-1111-4111-8111-111111111111"
    })
    void 잘못된_목록_필터는_조회_전에_400을_반환한다(String query) {
        client.get().uri(URI.create("/api/v3/emotions?" + query.replace("+", "%2B")))
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").exchange().expectStatus().isBadRequest();
        verifyNoInteractions(emotionQueryService);
    }

    @Test
    void 목록_날짜의_지원_범위_양끝을_허용한다() {
        LocalDate start = LocalDate.of(1, 1, 1);
        LocalDate end = LocalDate.of(9999, 12, 31);
        when(emotionQueryService.findListWithoutBounds(DEVICE_PUBLIC_ID, null, start, end, null, null, null))
                .thenReturn(EmotionPageView.of(List.of(), false, null));

        request(HttpMethod.GET, "/api/v3/emotions?startDate=0001-01-01&endDate=9999-12-31", "access-token")
                .expectStatus().isOk();
        verify(emotionQueryService).findListWithoutBounds(DEVICE_PUBLIC_ID, null, start, end, null, null, null);
    }

    @Test
    void 그룹_커서의_그룹_ID와_나머지_필터는_생략한_채_서비스에_전달한다() {
        when(emotionQueryService.findListWithoutBounds(DEVICE_PUBLIC_ID, null, null, null, null, EmotionSearchScope.GROUP, "next"))
                .thenReturn(EmotionPageView.of(List.of(), false, null));

        request(HttpMethod.GET, "/api/v3/emotions?cursor=next&scope=GROUP", "access-token").expectStatus().isOk();
        verify(emotionQueryService).findListWithoutBounds(DEVICE_PUBLIC_ID, null, null, null, null, EmotionSearchScope.GROUP, "next");
    }

    @Test
    void 목록_커서의_명시적_필터_불일치는_400으로_반환한다() {
        when(emotionQueryService.findListWithoutBounds(DEVICE_PUBLIC_ID, null, null, null, EmotionContentFilter.ALL, null, "next"))
                .thenThrow(new EmotionException(EmotionErrorCode.EMOTION_INVALID_CURSOR));

        request(HttpMethod.GET, "/api/v3/emotions?cursor=next&contentType=ALL", "access-token")
                .expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("EMOTION-008");
    }

    @Test
    void 목록_커서의_그룹이_유효하지_않으면_404로_반환한다() {
        when(emotionQueryService.findListWithoutBounds(DEVICE_PUBLIC_ID, null, null, null, null, null, "next"))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_NOT_FOUND));

        request(HttpMethod.GET, "/api/v3/emotions?cursor=next", "access-token")
                .expectStatus().isNotFound().expectBody().jsonPath("$.code").isEqualTo(GroupErrorCode.GROUP_NOT_FOUND.getCode());
    }

    @ParameterizedTest
    @CsvSource({"EMOTION_INVALID_CURSOR,400", "EMOTION_AUDIO_PLAYBACK_UNAVAILABLE,503"})
    void 좌표_없는_목록의_커서와_재생_오류는_기존_오류_응답으로_반환한다(EmotionErrorCode error, int status) {
        // given
        UUID groupId = UUID.randomUUID();
        when(emotionQueryService.findListWithoutBounds(DEVICE_PUBLIC_ID, groupId == null ? null : List.of(groupId), null, null, null, null, "cursor"))
                .thenThrow(new EmotionException(error));

        // when / then
        request(HttpMethod.GET, "/api/v3/emotions?groupId=" + groupId + "&cursor=cursor", "access-token")
                .expectStatus().isEqualTo(status).expectBody().jsonPath("$.code").isEqualTo(error.getCode());
    }

    @Test
    void 목록의_첫_페이지와_다음_페이지를_인증된_기기로_조회한다() {
        EmotionSearchBounds bounds = EmotionSearchBounds.of(126.9, 37.5, 127.1, 37.6);
        when(emotionQueryService.findListWithinBounds(bounds, DEVICE_PUBLIC_ID, null, null))
                .thenReturn(EmotionPageView.of(List.of(detailView(42L, true), detailView(43L, false)), true, "next"));
        when(emotionQueryService.findListWithinBounds(null, DEVICE_PUBLIC_ID, null, "next"))
                .thenReturn(EmotionPageView.of(List.of(), false, null));

        request(HttpMethod.GET, "/api/v1/emotions?minLongitude=126.9&minLatitude=37.5&maxLongitude=127.1&maxLatitude=37.6", "access-token")
                .expectStatus().isOk().expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-cache, private")
                .expectBody().jsonPath("$.items[0].properties.emojis.length()").isEqualTo(6)
                .jsonPath("$.items[0].properties.isMine").isEqualTo(true)
                .jsonPath("$.items[1].properties.isMine").isEqualTo(false)
                .jsonPath("$.hasNext").isEqualTo(true).jsonPath("$.nextCursor").isEqualTo("next");
        request(HttpMethod.GET, "/api/v1/emotions?cursor=next", "access-token")
                .expectStatus().isOk().expectBody().jsonPath("$.items").isEmpty().jsonPath("$.hasNext").isEqualTo(false);
        verify(emotionQueryService).findListWithinBounds(bounds, DEVICE_PUBLIC_ID, null, null);
        verify(emotionQueryService).findListWithinBounds(null, DEVICE_PUBLIC_ID, null, "next");
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

    @Test
    void 목록의_그룹_필터를_전달한다() {
        // given
        UUID groupId = UUID.randomUUID();
        var bounds = EmotionSearchBounds.of(126, 37, 128, 38);
        when(emotionQueryService.findListWithinBounds(bounds, DEVICE_PUBLIC_ID, groupId, null))
                .thenReturn(EmotionPageView.of(List.of(), false, null));

        // when / then
        request(HttpMethod.GET, "/api/v1/emotions?minLongitude=126&minLatitude=37&maxLongitude=128&maxLatitude=38&groupId=" + groupId,
                "access-token").expectStatus().isOk();
        verify(emotionQueryService).findListWithinBounds(bounds, DEVICE_PUBLIC_ID, groupId, null);
    }

    @Test
    void 다음_페이지에서_그룹_조건을_다시_지정할_수_없다() {
        request(HttpMethod.GET, "/api/v1/emotions?cursor=next&groupId=" + UUID.randomUUID(), "access-token")
                .expectStatus().isBadRequest();
        verifyNoInteractions(emotionQueryService);
    }

    @ParameterizedTest
    @CsvSource({"map,false", "map,true", "list,false", "list,true", "regions,false"})
    void 반복한_groupId를_단일_키의_목록으로_받아_첫_페이지와_다음_페이지에_전달한다(String api, boolean next) {
        // given
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<UUID> groups = List.of(b, a, b);
        var bounds = EmotionSearchBounds.of(126, 37, 128, 38);
        String cursor = next ? "next" : null;
        String query = "&groupId=" + b + "&groupId=" + a + "&groupId=" + b;
        if (api.equals("map")) {
            when(emotionMapQueryService.findContentMapWithinBounds(next ? null : bounds, DEVICE_PUBLIC_ID,
                    groups, null, null, null, null, cursor)).thenReturn(EmotionMapPageView.of(List.of(), false, null));
        } else if (api.equals("list")) {
            when(emotionQueryService.findListWithoutBounds(DEVICE_PUBLIC_ID, groups, null, null, null, null, cursor))
                    .thenReturn(EmotionPageView.of(List.of(), false, null));
        } else {
            when(emotionMapQueryService.findRegionMap(bounds, RegionLevel.SIDO, DEVICE_PUBLIC_ID, EmotionSearchFilter.from(groups)))
                    .thenReturn(List.of());
        }
        String uri = switch (api) {
            case "map" -> "/api/v2/emotions/map?" + (next ? "cursor=next" : "minLongitude=126&minLatitude=37&maxLongitude=128&maxLatitude=38");
            case "list" -> "/api/v3/emotions?" + (next ? "cursor=next" : "");
            default -> "/api/v2/emotions/map/regions?minLongitude=126&minLatitude=37&maxLongitude=128&maxLatitude=38&level=SIDO";
        };

        // when / then
        request(HttpMethod.GET, uri + query, "access-token").expectStatus().isOk();
        if (api.equals("map")) {
            verify(emotionMapQueryService).findContentMapWithinBounds(next ? null : bounds, DEVICE_PUBLIC_ID,
                    groups, null, null, null, null, cursor);
        } else if (api.equals("list")) {
            verify(emotionQueryService).findListWithoutBounds(DEVICE_PUBLIC_ID, groups, null, null, null, null, cursor);
        } else {
            verify(emotionMapQueryService).findRegionMap(bounds, RegionLevel.SIDO, DEVICE_PUBLIC_ID, EmotionSearchFilter.from(groups));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 긴_그룹_선택과_2048자를_넘는_다음_커서를_HTTP로_전달한다(boolean map) {
        // given
        List<UUID> groups = LongStream.range(0, 100).mapToObj(id -> new UUID(0, id)).toList();
        var bounds = EmotionSearchBounds.of(126, 37, 128, 38);
        var filter = EmotionSearchFilter.from(groups);
        Instant snapshot = Instant.parse("2026-10-08T00:00:00Z");
        var cursor = map ? EmotionCursor.initialWithinBounds(bounds, snapshot, filter)
                : EmotionCursor.initialWithoutBounds(snapshot, filter);
        String encoded = EmotionCursorCodec.encode(cursor);
        String query = groups.stream().map(id -> "groupId=" + id).collect(Collectors.joining("&"));
        String uri = map ? "/api/v2/emotions/map" : "/api/v3/emotions";
        String firstQuery = map ? "minLongitude=126&minLatitude=37&maxLongitude=128&maxLatitude=38&" + query : query;
        if (map) {
            when(emotionMapQueryService.findContentMapWithinBounds(bounds, DEVICE_PUBLIC_ID, groups, null, null, null, null, null))
                    .thenReturn(EmotionMapPageView.of(List.of(), true, encoded));
            when(emotionMapQueryService.findContentMapWithinBounds(null, DEVICE_PUBLIC_ID, null, null, null, null, null, encoded))
                    .thenReturn(EmotionMapPageView.of(List.of(), false, null));
        } else {
            when(emotionQueryService.findListWithoutBounds(DEVICE_PUBLIC_ID, groups, null, null, null, null, null))
                    .thenReturn(EmotionPageView.of(List.of(), true, encoded));
            when(emotionQueryService.findListWithoutBounds(DEVICE_PUBLIC_ID, null, null, null, null, null, encoded))
                    .thenReturn(EmotionPageView.of(List.of(), false, null));
        }

        // when / then
        assertThat(query.length()).isGreaterThan(2_048);
        assertThat(encoded.length()).isGreaterThan(2_048);
        request(HttpMethod.GET, uri + "?" + firstQuery, "access-token").expectStatus().isOk()
                .expectBody().jsonPath("$.nextCursor").isEqualTo(encoded);
        request(HttpMethod.GET, uri + "?cursor=" + encoded, "access-token").expectStatus().isOk()
                .expectBody().jsonPath("$.hasNext").isEqualTo(false);
        if (map) {
            verify(emotionMapQueryService).findContentMapWithinBounds(null, DEVICE_PUBLIC_ID, null, null, null, null, null, encoded);
        } else {
            verify(emotionQueryService).findListWithoutBounds(DEVICE_PUBLIC_ID, null, null, null, null, null, encoded);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"groupId=", "groupId=%20", "groupId=11111111-1111-1111-1111-111111111111&groupId=",
            "groupId=11111111-1111-1111-1111-111111111111&groupId=invalid"})
    void 빈_값이나_잘못된_원소가_있는_그룹_목록은_조회_전에_거부한다(String query) {
        // given / when / then
        for (String uri : List.of("/api/v2/emotions/map?minLongitude=126&minLatitude=37&maxLongitude=128&maxLatitude=38&",
                "/api/v3/emotions?", "/api/v2/emotions/map/regions?minLongitude=126&minLatitude=37&maxLongitude=128&maxLatitude=38&level=SIDO&")) {
            request(HttpMethod.GET, uri + query, "access-token").expectStatus().isBadRequest();
        }
        verifyNoInteractions(emotionQueryService, emotionMapQueryService);
    }

    @Test
    void v2_지도는_내용_있는_지도_경로에_그룹과_커서를_전달한다() {
        // given
        UUID groupId = UUID.randomUUID();
        var bounds = EmotionSearchBounds.of(126, 37, 128, 38);
        var item = new EmotionMapItemView(42L, 126.9774, 37.5669,
                Instant.parse("2026-09-24T12:00:00Z"), EmotionState.FRUSTRATED, 35.5, null, groupId);
        when(emotionMapQueryService.findContentMapWithinBounds(bounds, DEVICE_PUBLIC_ID, groupId == null ? null : List.of(groupId), null, null, null, null, null))
                .thenReturn(EmotionMapPageView.of(List.of(item), true, "next-map"));
        when(emotionMapQueryService.findContentMapWithinBounds(null, DEVICE_PUBLIC_ID, null, null, null, null, null, "next-map"))
                .thenReturn(EmotionMapPageView.of(List.of(), false, null));

        // when / then
        request(HttpMethod.GET, "/api/v2/emotions/map?minLongitude=126&minLatitude=37&maxLongitude=128&maxLatitude=38&groupId=" + groupId,
                "access-token").expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-cache, private")
                .expectBody().jsonPath("$.items[0].id").isEqualTo(42)
                .jsonPath("$.nextCursor").isEqualTo("next-map");
        request(HttpMethod.GET, "/api/v2/emotions/map?cursor=next-map", "access-token")
                .expectStatus().isOk().expectBody().jsonPath("$.items").isEmpty();
        verify(emotionMapQueryService).findContentMapWithinBounds(bounds, DEVICE_PUBLIC_ID, groupId == null ? null : List.of(groupId), null, null, null, null, null);
        verify(emotionMapQueryService).findContentMapWithinBounds(null, DEVICE_PUBLIC_ID, null, null, null, null, null, "next-map");
        org.mockito.Mockito.verifyNoMoreInteractions(emotionQueryService, emotionMapQueryService);
    }

    @ParameterizedTest
    @CsvSource({"ALL,ALL", "MEMO,GROUP", "AUDIO,MY"})
    void v2_지도_필터를_인증된_기기로_전달하고_다음_페이지에도_명시할_수_있다(
            EmotionContentFilter contentType, EmotionSearchScope scope
    ) {
        // given
        UUID groupId = scope == EmotionSearchScope.GROUP ? UUID.randomUUID() : null;
        var bounds = EmotionSearchBounds.of(170, -10, -170, 10);
        LocalDate date = LocalDate.of(2026, 10, 1);
        when(emotionMapQueryService.findContentMapWithinBounds(bounds, DEVICE_PUBLIC_ID, groupId == null ? null : List.of(groupId), date, date, contentType, scope, null))
                .thenReturn(EmotionMapPageView.of(List.of(), true, "next-map"));
        when(emotionMapQueryService.findContentMapWithinBounds(null, DEVICE_PUBLIC_ID, groupId == null ? null : List.of(groupId), date, date, contentType, scope, "next-map"))
                .thenReturn(EmotionMapPageView.of(List.of(), false, null));
        String filters = "&startDate=2026-10-01&endDate=2026-10-01&contentType=" + contentType + "&scope=" + scope
                + (groupId == null ? "" : "&groupId=" + groupId) + "&devicePublicId=" + UUID.randomUUID();

        // when / then
        request(HttpMethod.GET, "/api/v2/emotions/map?minLongitude=170&minLatitude=-10&maxLongitude=-170&maxLatitude=10" + filters,
                "access-token").expectStatus().isOk();
        request(HttpMethod.GET, "/api/v2/emotions/map?cursor=next-map" + filters, "access-token").expectStatus().isOk();
        verify(emotionMapQueryService).findContentMapWithinBounds(bounds, DEVICE_PUBLIC_ID, groupId == null ? null : List.of(groupId), date, date, contentType, scope, null);
        verify(emotionMapQueryService).findContentMapWithinBounds(null, DEVICE_PUBLIC_ID, groupId == null ? null : List.of(groupId), date, date, contentType, scope, "next-map");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "startDate=2026-10-01", "endDate=2026-10-01", "startDate=2026-10-02&endDate=2026-10-01",
            "startDate=2026-02-30&endDate=2026-03-01", "startDate=invalid&endDate=2026-10-01",
            "startDate=0000-01-01&endDate=2026-10-01", "startDate=2026-10-01&endDate=+10000-01-01",
            "contentType=NONE", "contentType=invalid", "scope=invalid", "scope=GROUP", "groupId=invalid",
            "scope=ALL&groupId=11111111-1111-4111-8111-111111111111",
            "scope=MY&groupId=11111111-1111-4111-8111-111111111111",
            "cursor=next&startDate=2026-10-01",
            "cursor=next&scope=MY&groupId=11111111-1111-4111-8111-111111111111"
    })
    void 잘못된_v2_지도_필터는_조회_전에_400을_반환한다(String query) {
        String bounds = query.startsWith("cursor=") ? "" : "minLongitude=126&minLatitude=37&maxLongitude=128&maxLatitude=38&";
        client.get().uri(URI.create("/api/v2/emotions/map?" + bounds + query.replace("+", "%2B")))
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").exchange().expectStatus().isBadRequest();
        verifyNoInteractions(emotionMapQueryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "minLongitude=126&minLatitude=37&maxLongitude=128",
            "minLongitude=126&minLatitude=37&maxLongitude=126&maxLatitude=38",
            "minLongitude=126&minLatitude=38&maxLongitude=128&maxLatitude=37",
            "minLongitude=181&minLatitude=37&maxLongitude=128&maxLatitude=38",
            "minLongitude=126&minLatitude=-91&maxLongitude=128&maxLatitude=38",
            "cursor=next&minLongitude=126",
            "cursor=next&minLongitude=126&minLatitude=37&maxLongitude=128&maxLatitude=38"
    })
    void v2_지도의_영역과_커서_조합이_잘못되면_조회하지_않는다(String query) {
        request(HttpMethod.GET, "/api/v2/emotions/map?" + query, "access-token").expectStatus().isBadRequest();
        verifyNoInteractions(emotionMapQueryService);
    }

    @Test
    void v2_지도_날짜의_지원_범위_양끝을_허용한다() {
        var bounds = EmotionSearchBounds.of(126, 37, 128, 38);
        LocalDate start = LocalDate.of(1, 1, 1);
        LocalDate end = LocalDate.of(9999, 12, 31);
        when(emotionMapQueryService.findContentMapWithinBounds(bounds, DEVICE_PUBLIC_ID, null, start, end, null, null, null))
                .thenReturn(EmotionMapPageView.of(List.of(), false, null));

        request(HttpMethod.GET, "/api/v2/emotions/map?minLongitude=126&minLatitude=37&maxLongitude=128&maxLatitude=38"
                + "&startDate=0001-01-01&endDate=9999-12-31", "access-token").expectStatus().isOk();
        verify(emotionMapQueryService).findContentMapWithinBounds(bounds, DEVICE_PUBLIC_ID, null, start, end, null, null, null);
    }

    @Test
    void v2_지도_그룹_커서의_생략한_필터는_서비스에_null로_전달한다() {
        when(emotionMapQueryService.findContentMapWithinBounds(null, DEVICE_PUBLIC_ID, null, null, null, null, EmotionSearchScope.GROUP, "next"))
                .thenReturn(EmotionMapPageView.of(List.of(), false, null));

        request(HttpMethod.GET, "/api/v2/emotions/map?cursor=next&scope=GROUP", "access-token").expectStatus().isOk();
        verify(emotionMapQueryService).findContentMapWithinBounds(null, DEVICE_PUBLIC_ID, null, null, null, null, EmotionSearchScope.GROUP, "next");
    }

    @Test
    void v2_지도_커서의_명시적_필터_불일치는_400으로_반환한다() {
        when(emotionMapQueryService.findContentMapWithinBounds(null, DEVICE_PUBLIC_ID, null, null, null, EmotionContentFilter.ALL, null, "next"))
                .thenThrow(new EmotionException(EmotionErrorCode.EMOTION_INVALID_CURSOR));

        request(HttpMethod.GET, "/api/v2/emotions/map?cursor=next&contentType=ALL", "access-token")
                .expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("EMOTION-008");
    }

    @Test
    void v2_지도_커서의_그룹이_유효하지_않으면_404로_반환한다() {
        when(emotionMapQueryService.findContentMapWithinBounds(null, DEVICE_PUBLIC_ID, null, null, null, null, null, "next"))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_NOT_FOUND));

        request(HttpMethod.GET, "/api/v2/emotions/map?cursor=next", "access-token")
                .expectStatus().isNotFound().expectBody().jsonPath("$.code").isEqualTo(GroupErrorCode.GROUP_NOT_FOUND.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v2/emotions/map", "/api/v2/emotions/map/regions"})
    void v2_지도도_인증과_유효한_검색_조건이_필요하다(String uri) {
        // when / then
        request(HttpMethod.GET, uri, null).expectStatus().isUnauthorized();
        request(HttpMethod.GET, uri, "invalid-token").expectStatus().isUnauthorized();
        request(HttpMethod.GET, uri, "access-token").expectStatus().isBadRequest();
        request(HttpMethod.POST, uri, "access-token").expectStatus().isForbidden();
        verifyNoInteractions(emotionQueryService, emotionMapQueryService);
    }

    @Test
    void 지도는_표시_정보만_반환하고_목록이나_단건_조회를_호출하지_않는다() {
        // given
        var bounds = EmotionSearchBounds.of(126, 37, 128, 38);
        var item = new EmotionMapItemView(42L, 126.9774, 37.5669,
                Instant.parse("2026-09-24T12:00:00Z"), EmotionState.FRUSTRATED, 35.5, null, null);
        when(emotionMapQueryService.findMapWithinBounds(bounds, DEVICE_PUBLIC_ID, null, null))
                .thenReturn(EmotionMapPageView.of(List.of(item), true, "next-map"));

        // when / then
        request(HttpMethod.GET, "/api/v1/emotions/map?minLongitude=126&minLatitude=37&maxLongitude=128&maxLatitude=38",
                "access-token").expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-cache, private")
                .expectBody().json("""
                        {"items":[{"type":"Feature","id":42,
                          "geometry":{"type":"Point","coordinates":[126.9774,37.5669]},
                          "properties":{"createdAt":"2026-09-24T12:00:00Z","state":"FRUSTRATED",
                          "rotationDegrees":35.5,"groupStamp":null,"groupId":null}}],
                          "hasNext":true,"nextCursor":"next-map"}
                        """, JsonCompareMode.STRICT);
        verify(emotionMapQueryService).findMapWithinBounds(bounds, DEVICE_PUBLIC_ID, null, null);
        org.mockito.Mockito.verifyNoMoreInteractions(emotionQueryService, emotionMapQueryService);
    }

    @Test
    void 지도_다음_페이지는_지도_조회에_커서를_전달한다() {
        when(emotionMapQueryService.findMapWithinBounds(null, DEVICE_PUBLIC_ID, null, "next-map"))
                .thenReturn(EmotionMapPageView.of(List.of(), false, null));
        request(HttpMethod.GET, "/api/v1/emotions/map?cursor=next-map", "access-token").expectStatus().isOk()
                .expectBody().jsonPath("$.items").isEmpty();
        verify(emotionMapQueryService).findMapWithinBounds(null, DEVICE_PUBLIC_ID, null, "next-map");
    }

    @Test
    void 지도도_그룹_필터를_전달한다() {
        UUID groupId = UUID.randomUUID();
        var bounds = EmotionSearchBounds.of(126, 37, 128, 38);
        when(emotionMapQueryService.findMapWithinBounds(bounds, DEVICE_PUBLIC_ID, groupId, null))
                .thenReturn(EmotionMapPageView.of(List.of(), false, null));
        request(HttpMethod.GET, "/api/v1/emotions/map?minLongitude=126&minLatitude=37&maxLongitude=128&maxLatitude=38&groupId=" + groupId,
                "access-token").expectStatus().isOk();
        verify(emotionMapQueryService).findMapWithinBounds(bounds, DEVICE_PUBLIC_ID, groupId, null);
    }

    @Test
    void 지도_조회도_인증과_유효한_검색_조건이_필요하다() {
        request(HttpMethod.GET, "/api/v1/emotions/map?minLongitude=126&minLatitude=37&maxLongitude=128&maxLatitude=38", null)
                .expectStatus().isUnauthorized();
        request(HttpMethod.GET, "/api/v1/emotions/map", "access-token").expectStatus().isBadRequest();
        request(HttpMethod.GET, "/api/v1/emotions/map?cursor=next&groupId=" + UUID.randomUUID(), "access-token")
                .expectStatus().isBadRequest();
        verifyNoInteractions(emotionQueryService, emotionMapQueryService);
    }

    @ParameterizedTest
    @CsvSource({"SIDO,11,", "SIGUNGU,11010,11", "EMD,11010530,11010"})
    void 지역_요약은_계층과_그룹을_전달하고_화면_밖_표시점도_완전한_응답으로_반환한다(RegionLevel level, String code, String parentCode) {
        // given: 실제 SGIS 표시점·개수가 아닌 HTTP 계약 검증용 값이다.
        UUID groupId = UUID.randomUUID();
        var bounds = EmotionSearchBounds.of(127.8, 37.2, 128.2, 37.8);
        var region = Region.of(code, level, "검증용 지역", parentCode, 127, 38);
        var item = EmotionRegionMapItemView.of(region, RegionEmotionSummary.of(3L, EmotionState.ANGRY));
        when(emotionMapQueryService.findRegionMap(bounds, level, DEVICE_PUBLIC_ID, EmotionSearchFilter.from(groupId == null ? null : List.of(groupId)))).thenReturn(List.of(item));

        // when / then
        request(HttpMethod.GET, REGION_MAP_URI + REGION_BOUNDS_QUERY + "&level=" + level + "&groupId=" + groupId,
                "access-token").expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-cache, private")
                .expectBody().json("""
                        [{"type":"Feature","id":"%s",
                         "geometry":{"type":"Point","coordinates":[127.0,38.0]},
                         "properties":{"level":"%s","name":"검증용 지역","parentCode":%s,
                         "totalCount":3,"representativeState":"ANGRY"}}]
                        """.formatted(code, level, parentCode == null ? "null" : "\"" + parentCode + "\""), JsonCompareMode.STRICT);
        verify(emotionMapQueryService).findRegionMap(bounds, level, DEVICE_PUBLIC_ID, EmotionSearchFilter.from(groupId == null ? null : List.of(groupId)));
        org.mockito.Mockito.verifyNoMoreInteractions(emotionQueryService, emotionMapQueryService);
    }

    @Test
    void 지역_요약은_날짜변경선과_그룹_생략을_허용하고_빈_결과도_정상_응답한다() {
        // given
        var bounds = EmotionSearchBounds.of(170, 37, -170, 39);
        when(emotionMapQueryService.findRegionMap(bounds, RegionLevel.EMD, DEVICE_PUBLIC_ID, EmotionSearchFilter.from(null))).thenReturn(List.of());

        // when / then
        request(HttpMethod.GET, REGION_MAP_URI + "?minLongitude=170&minLatitude=37&maxLongitude=-170&maxLatitude=39&level=EMD",
                "access-token").expectStatus().isOk()
                .expectBody().json("[]", JsonCompareMode.STRICT);
        verify(emotionMapQueryService).findRegionMap(bounds, RegionLevel.EMD, DEVICE_PUBLIC_ID, EmotionSearchFilter.from(null));
    }

    @ParameterizedTest
    @CsvSource({
            "minLongitude,", "minLatitude,", "maxLongitude,", "maxLatitude,", "level,",
            "level,CITY", "groupId,invalid", "minLongitude,181", "maxLongitude,-181",
            "minLatitude,-91", "maxLatitude,91", "minLongitude,NaN", "maxLongitude,Infinity",
            "minLatitude,NaN", "maxLatitude,Infinity", "minLongitude,128.2", "minLatitude,37.8", "minLatitude,39"
    })
    void 지역_요약의_입력_오류는_집계_호출_전에_400으로_거부한다(String field, String value) {
        // given
        var parameters = new HashMap<>(Map.of("minLongitude", "127.8", "minLatitude", "37.2",
                "maxLongitude", "128.2", "maxLatitude", "37.8", "level", "EMD"));
        if (value == null) {
            parameters.remove(field);
        } else {
            parameters.put(field, value);
        }
        String query = parameters.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));

        // when / then
        request(HttpMethod.GET, REGION_MAP_URI + "?" + query, "access-token").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(emotionQueryService, emotionMapQueryService);
    }

    @ParameterizedTest
    @CsvSource({"SIDO,ALL,ALL", "SIDO,MEMO,MY", "SIDO,AUDIO,GROUP",
            "SIGUNGU,ALL,ALL", "SIGUNGU,MEMO,MY", "SIGUNGU,AUDIO,GROUP",
            "EMD,ALL,ALL", "EMD,MEMO,MY", "EMD,AUDIO,GROUP"})
    void 지역_요약_필터와_인증된_기기를_서비스로_전달한다(
            RegionLevel level, EmotionContentFilter contentType, EmotionSearchScope scope
    ) {
        // given
        UUID groupId = scope == EmotionSearchScope.GROUP ? UUID.randomUUID() : null;
        LocalDate date = LocalDate.of(2026, 10, 1);
        var filter = EmotionSearchFilter.of(date, date, contentType, scope, groupId == null ? null : List.of(groupId));
        var bounds = EmotionSearchBounds.of(127.8, 37.2, 128.2, 37.8);
        when(emotionMapQueryService.findRegionMap(bounds, level, DEVICE_PUBLIC_ID, filter)).thenReturn(List.of());

        // when / then: 요청 파라미터의 기기 ID로 인증 기기를 대체할 수 없다.
        request(HttpMethod.GET, REGION_MAP_URI + REGION_BOUNDS_QUERY + "&level=" + level + "&startDate=" + date
                + "&endDate=" + date + "&contentType=" + contentType + "&scope=" + scope
                + (groupId == null ? "" : "&groupId=" + groupId) + "&devicePublicId=" + UUID.randomUUID(),
                "access-token").expectStatus().isOk().expectBody().json("[]", JsonCompareMode.STRICT);
        verify(emotionMapQueryService).findRegionMap(bounds, level, DEVICE_PUBLIC_ID, filter);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "startDate=2026-10-01", "endDate=2026-10-01",
            "startDate=2026-10-02&endDate=2026-10-01", "startDate=0000-01-01&endDate=2026-10-01",
            "startDate=2026-10-01&endDate=10000-01-01", "startDate=invalid&endDate=2026-10-01",
            "contentType=NONE", "scope=INVALID", "scope=GROUP",
            "scope=MY&groupId=11111111-1111-1111-1111-111111111111",
            "scope=ALL&groupId=11111111-1111-1111-1111-111111111111"
    })
    void 잘못된_지역_필터는_집계_호출_전에_400으로_거부한다(String query) {
        // when / then
        request(HttpMethod.GET, REGION_MAP_URI + REGION_BOUNDS_QUERY + "&level=EMD&" + query, "access-token")
                .expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(emotionQueryService, emotionMapQueryService);
    }

    @Test
    void 지역_요약의_그룹_없음_오류를_404로_전달한다() {
        // given
        UUID groupId = UUID.randomUUID();
        var filter = EmotionSearchFilter.from(groupId == null ? null : List.of(groupId));
        when(emotionMapQueryService.findRegionMap(EmotionSearchBounds.of(127.8, 37.2, 128.2, 37.8),
                RegionLevel.EMD, DEVICE_PUBLIC_ID, filter)).thenThrow(new GroupException(GroupErrorCode.GROUP_NOT_FOUND));

        // when / then
        request(HttpMethod.GET, REGION_MAP_URI + REGION_BOUNDS_QUERY + "&level=EMD&groupId=" + groupId, "access-token")
                .expectStatus().isNotFound().expectBody().jsonPath("$.code").isEqualTo("GROUP-001");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"invalid-token"})
    void 지역_요약은_유효한_인증_없이_조회할_수_없다(String token) {
        // given / when / then
        request(HttpMethod.GET, REGION_MAP_URI + REGION_BOUNDS_QUERY + "&level=EMD", token)
                .expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("AUTH-001");
        verifyNoInteractions(emotionQueryService, emotionMapQueryService);
    }

    @Test
    void 이전_v1_지역_요약_경로는_허용하지_않는다() {
        // when / then
        request(HttpMethod.GET, "/api/v1/emotions/map/regions" + REGION_BOUNDS_QUERY + "&level=EMD", "access-token")
                .expectStatus().isForbidden();
        verifyNoInteractions(emotionQueryService, emotionMapQueryService);
    }

    @Test
    void 지역_요약의_GET_외_메서드와_추가_하위_경로는_허용하지_않는다() {
        // given / when / then
        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            request(method, REGION_MAP_URI, "access-token").expectStatus().isForbidden();
        }
        request(HttpMethod.GET, REGION_MAP_URI + "/extra", "access-token").expectStatus().isForbidden();
        verifyNoInteractions(emotionQueryService, emotionMapQueryService);
    }

    @Test
    void 지역_요약_준비_오류는_빈_응답_대신_503으로_전달한다() {
        // given
        when(emotionMapQueryService.findRegionMap(EmotionSearchBounds.of(127.8, 37.2, 128.2, 37.8), RegionLevel.EMD, DEVICE_PUBLIC_ID,
                EmotionSearchFilter.from(null)))
                .thenThrow(new EmotionException(EmotionErrorCode.EMOTION_REGION_DATA_UNAVAILABLE));

        // when / then
        request(HttpMethod.GET, REGION_MAP_URI + REGION_BOUNDS_QUERY + "&level=EMD", "access-token")
                .expectStatus().isEqualTo(503).expectBody().jsonPath("$.code").isEqualTo("EMOTION-013");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SIDO", "SIGUNGU"})
    void 전국_요약도_기존_bbox_필수값과_좌표_범위를_검증한다(String level) {
        // when / then
        for (String query : List.of("?level=" + level,
                REGION_BOUNDS_QUERY.replace("127.8", "181") + "&level=" + level,
                REGION_BOUNDS_QUERY.replace("37.2", "NaN") + "&level=" + level)) {
            request(HttpMethod.GET, REGION_MAP_URI + query, "access-token")
                    .expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        }
        verifyNoInteractions(emotionQueryService, emotionMapQueryService);
    }

    @Test
    void 별도_v3_지역_요약_경로는_공개하지_않는다() {
        // when / then
        request(HttpMethod.GET, "/api/v3/emotions/map/regions" + REGION_BOUNDS_QUERY + "&level=SIDO", "access-token")
                .expectStatus().isForbidden();
        verifyNoInteractions(emotionQueryService, emotionMapQueryService);
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

    private EmotionDetailView detailView(Long emotionId, boolean isMine) {
        Emotion emotion = 기본_한숨_빌더()
                .state(EmotionState.FRUSTRATED)
                .rotationDegrees(35.5)
                .memo("답답한 하루")
                .deviceId(isMine ? 1L : 2L)
                .build();
        ReflectionTestUtils.setField(emotion, "id", emotionId);
        ReflectionTestUtils.setField(emotion, "createdAt", Instant.parse("2026-09-24T12:00:00Z"));

        return EmotionDetailView.of(emotion, List.of(
                EmotionEmojiResult.of(EmojiType.HEART, 2, true),
                EmotionEmojiResult.of(EmojiType.LAUGH, 0, false),
                EmotionEmojiResult.of(EmojiType.CRY, 0, false),
                EmotionEmojiResult.of(EmojiType.DIZZY, 0, false),
                EmotionEmojiResult.of(EmojiType.RAGE, 0, false),
                EmotionEmojiResult.of(EmojiType.SKULL, 0, false)
        ), null, null, 1L);
    }

    private static Stream<Arguments> listWithoutBoundsRequests() {
        UUID groupId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        return Stream.of(
                Arguments.of("", null, null),
                Arguments.of("?groupId=" + groupId, groupId, null),
                Arguments.of("?cursor=next", null, "next"),
                Arguments.of("?groupId=" + groupId + "&cursor=next", groupId, "next"));
    }

    private static Stream<Arguments> v3AnonymousOptions() {
        return Stream.of(Arguments.of("", null), Arguments.of(",\"anonymous\":null", null),
                Arguments.of(",\"anonymous\":true", true), Arguments.of(",\"anonymous\":false", false));
    }

    private static Stream<Arguments> updateContents() {
        return Stream.of(
                Arguments.of("\"contentType\":\"NONE\"", null, null, false),
                Arguments.of("\"contentType\":\"MEMO\",\"memo\":\"메모\"", "메모", null, false),
                Arguments.of("\"contentType\":\"AUDIO\",\"audioUploadId\":\"upload\"", null, "upload", false),
                Arguments.of("\"contentType\":\"AUDIO\"", null, null, true)
        );
    }

    private static Stream<Arguments> v3UpdateContents() {
        return Stream.of(
                Arguments.of("\"contentType\":\"MEMO\",\"memo\":\" 메모 \"", " 메모 ", null, false),
                Arguments.of("\"contentType\":\"AUDIO\",\"audioUploadId\":\"upload\"", null, "upload", false),
                Arguments.of("\"contentType\":\"AUDIO\"", null, null, true),
                Arguments.of("\"contentType\":\"AUDIO\",\"audioUploadId\":null", null, null, true)
        );
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
