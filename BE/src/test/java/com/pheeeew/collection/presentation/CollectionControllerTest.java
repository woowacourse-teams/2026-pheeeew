package com.pheeeew.collection.presentation;

import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_카드_빌더;
import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_카드_이미지;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pheeeew.auth.fixture.AccessTokenFixture;
import com.pheeeew.auth.infra.security.AuthenticationErrorHandler;
import com.pheeeew.auth.infra.security.SecurityConfig;
import com.pheeeew.auth.presentation.config.AuthWebMvcConfig;
import com.pheeeew.auth.presentation.resolver.CurrentDeviceArgumentResolver;
import com.pheeeew.collection.application.CollectionClaimService;
import com.pheeeew.collection.application.CollectionQueryService;
import com.pheeeew.collection.application.dto.CollectionCardDetailResult;
import com.pheeeew.collection.application.dto.CollectionCardResult;
import com.pheeeew.collection.application.dto.CollectionHistoryResult;
import com.pheeeew.collection.application.dto.CollectionTodayResult;
import com.pheeeew.collection.exception.CollectionErrorCode;
import com.pheeeew.collection.exception.CollectionException;
import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.common.logging.RequestLogWriter;
import com.pheeeew.common.logging.RequestLoggingConfiguration;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.domain.EmotionState;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
        CurrentDeviceArgumentResolver.class, GlobalExceptionHandler.class, RequestLoggingConfiguration.class})
@WebMvcTest(controllers = CollectionController.class)
class CollectionControllerTest {

    private static final UUID DEVICE_PUBLIC_ID = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");
    private static final String URI = "/api/v1/collection-cards/today";

    @Autowired
    private RestTestClient client;

    @MockitoBean
    private CollectionQueryService service;

    @MockitoBean
    private CollectionClaimService claims;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode("access-token")).thenReturn(AccessTokenFixture.액세스_토큰_클레임(DEVICE_PUBLIC_ID));
    }

    @Test
    void 목록은_인증된_기기로_조회하고_빈_커서_응답을_반환한다() {
        // given
        when(service.findHistory(DEVICE_PUBLIC_ID, null, null, null))
                .thenReturn(CollectionHistoryResult.of(List.of(), false, null));

        // when / then
        client.get().uri("/api/v1/collection-cards")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").exchange()
                .expectStatus().isOk().expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().json("""
                        {"items":[],"hasNext":false,"nextCursor":null}
                        """, JsonCompareMode.STRICT);
        verify(service).findHistory(DEVICE_PUBLIC_ID, null, null, null);
    }

    @Test
    void 다음_페이지의_생략된_감정_필터는_커서와_함께_서비스에_전달한다() {
        // given
        when(service.findHistory(DEVICE_PUBLIC_ID, null, 50, "previous"))
                .thenReturn(CollectionHistoryResult.of(List.of(), false, null));

        // when / then
        client.get().uri("/api/v1/collection-cards?limit=50&cursor=previous")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").exchange().expectStatus().isOk();
        verify(service).findHistory(DEVICE_PUBLIC_ID, null, 50, "previous");
    }

    @Test
    void 목록_커서의_길이_제한을_초과하면_거절한다() {
        // when / then
        client.get().uri("/api/v1/collection-cards?cursor=" + "a".repeat(513))
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").exchange()
                .expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(service);
    }

    @Test
    void 목록을_요청한_인증_기기가_없으면_기존_401을_반환한다() {
        // given
        when(service.findHistory(DEVICE_PUBLIC_ID, null, null, null))
                .thenThrow(new DeviceException(DeviceErrorCode.DEVICE_NOT_FOUND));

        // when / then
        client.get().uri("/api/v1/collection-cards")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").exchange()
                .expectStatus().isUnauthorized().expectBody()
                .jsonPath("$.code").isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND.getCode());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "invalid-token")
    void 목록_조회는_유효한_기기_인증이_필요하다(String token) {
        // given
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid token"));
        var request = client.get().uri("/api/v1/collection-cards");
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        // when / then
        request.exchange().expectStatus().isUnauthorized().expectBody()
                .jsonPath("$.code").isEqualTo("AUTH-001");
        verifyNoInteractions(service);
    }

    @Test
    void 목록_조건을_전달하고_상세와_같은_카드_응답을_반환한다() {
        // given
        UUID cardId = UUID.fromString("20769cf2-1f8a-4b62-8dbe-5b594d5ffb5d");
        var item = CollectionCardDetailResult.of(cardId, LocalDate.of(2026, 10, 21),
                EmotionState.ANGRY, 3, 125, 기본_카드_이미지());
        when(service.findHistory(DEVICE_PUBLIC_ID, EmotionState.ANGRY, 1, "previous"))
                .thenReturn(CollectionHistoryResult.of(List.of(item), true, "next"));

        // when / then
        client.get().uri("/api/v1/collection-cards?emotion=ANGRY&limit=1&cursor=previous&devicePublicId=" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").exchange()
                .expectStatus().isOk().expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().json("""
                        {"items":[{"cardId":"20769cf2-1f8a-4b62-8dbe-5b594d5ffb5d","date":"2026-10-21","state":"ANGRY",
                         "stampCount":3,"pressCount":125,
                         "images":{"characterUrl":"https://assets.example/v1/characters/angry/01.png",
                                   "roundStampUrl":"https://assets.example/v1/stamps-round/angry/01.png",
                                   "straightStampUrl":"https://assets.example/v1/stamps-straight/angry/01.png"}}],
                         "hasNext":true,"nextCursor":"next"}
                        """, JsonCompareMode.STRICT);
        verify(service).findHistory(DEVICE_PUBLIC_ID, EmotionState.ANGRY, 1, "previous");
    }

    @ParameterizedTest
    @ValueSource(strings = {"emotion=UNKNOWN", "emotion=", "limit=0", "limit=-1", "limit=51", "limit=abc", "limit="})
    void 잘못된_목록_요청은_서비스_호출_전에_거절한다(String query) {
        // when / then
        client.get().uri("/api/v1/collection-cards?" + query)
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").exchange()
                .expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"COLLECTION_INVALID_CURSOR,400,COLLECTION-007", "COLLECTION_INVALID_LIMIT,400,COLLECTION-008",
            "COLLECTION_ASSETS_NOT_READY,503,COLLECTION-004"})
    void 목록_서비스_오류도_공통_응답으로_전달한다(CollectionErrorCode error, int status, String code) {
        // given
        when(service.findHistory(DEVICE_PUBLIC_ID, null, null, "invalid"))
                .thenThrow(new CollectionException(error));

        // when / then
        client.get().uri("/api/v1/collection-cards?cursor=invalid")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").exchange()
                .expectStatus().isEqualTo(status).expectBody()
                .jsonPath("$.code").isEqualTo(code)
                .jsonPath("$.message").isEqualTo(error.getMessage());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "invalid-token")
    void 상세_조회도_유효한_인증이_필요하다(String token) {
        // given
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid token"));
        var request = client.get().uri("/api/v1/collection-cards/" + UUID.randomUUID());
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        // when / then
        request.exchange().expectStatus().isUnauthorized().expectBody()
                .jsonPath("$.code").isEqualTo("AUTH-001");
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "today", "20769cf2-1f8a-4b62-8dbe-5b594d5ffb5d"})
    void 집계_누락은_500_오류와_서버_오류_로그로_남긴다(String path) {
        // given
        var failure = new CollectionException(CollectionErrorCode.COLLECTION_ACTIVITY_COUNTS_MISSING);
        if (path.isEmpty()) {
            when(service.findHistory(DEVICE_PUBLIC_ID, null, null, null)).thenThrow(failure);
        } else if (path.equals("today")) {
            when(service.findToday(DEVICE_PUBLIC_ID)).thenThrow(failure);
        } else {
            when(service.findDetail(DEVICE_PUBLIC_ID, UUID.fromString(path))).thenThrow(failure);
        }
        Logger logger = (Logger) LoggerFactory.getLogger(RequestLogWriter.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            // when / then
            client.get().uri("/api/v1/collection-cards" + (path.isEmpty() ? "" : "/" + path))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").exchange()
                    .expectStatus().isEqualTo(500).expectBody().json("""
                            {"code":"COLLECTION-006","message":"카드의 활동 횟수를 조회할 수 없습니다."}
                            """, JsonCompareMode.STRICT);
            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getKeyValuePairs()).anySatisfy(pair -> {
                    assertThat(pair.key).isEqualTo("errorCode");
                    assertThat(pair.value).isEqualTo("COLLECTION-006");
                });
                assertThat(event.getKeyValuePairs()).anySatisfy(pair -> {
                    assertThat(pair.key).isEqualTo("event");
                    assertThat(pair.value).isEqualTo("http_request_failed");
                });
                assertThat(event.getMDCPropertyMap()).containsKey("correlationId");
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void 없는_카드나_다른_기기_카드는_공통_404_계약으로_응답한다() {
        // given
        UUID cardId = UUID.randomUUID();
        when(service.findDetail(DEVICE_PUBLIC_ID, cardId))
                .thenThrow(new CollectionException(CollectionErrorCode.COLLECTION_CARD_NOT_FOUND));

        // when / then
        client.get().uri("/api/v1/collection-cards/" + cardId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").exchange()
                .expectStatus().isNotFound().expectBody().json("""
                        {"code":"COLLECTION-005","message":"카드를 찾을 수 없습니다."}
                        """, JsonCompareMode.STRICT);
    }

    @Test
    void 잘못된_카드_식별자는_400으로_거절한다() {
        // when / then
        client.get().uri("/api/v1/collection-cards/not-a-uuid")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").exchange()
                .expectStatus().isBadRequest();
        verifyNoInteractions(service);
    }

    @Test
    void 카드_상세는_현재_횟수와_고정_이미지만_캐시하지_않고_반환한다() {
        // given
        UUID cardId = UUID.fromString("20769cf2-1f8a-4b62-8dbe-5b594d5ffb5d");
        when(service.findDetail(DEVICE_PUBLIC_ID, cardId)).thenReturn(CollectionCardDetailResult.of(
                cardId, LocalDate.of(2026, 10, 21), EmotionState.ANGRY, 3, 125, 기본_카드_이미지()));

        // when
        var response = client.get().uri("/api/v1/collection-cards/" + cardId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token").exchange();

        // then
        response.expectStatus().isOk().expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().json("""
                        {"cardId":"20769cf2-1f8a-4b62-8dbe-5b594d5ffb5d","date":"2026-10-21","state":"ANGRY",
                         "stampCount":3,"pressCount":125,
                         "images":{"characterUrl":"https://assets.example/v1/characters/angry/01.png",
                                   "roundStampUrl":"https://assets.example/v1/stamps-round/angry/01.png",
                                   "straightStampUrl":"https://assets.example/v1/stamps-straight/angry/01.png"}}
                        """, JsonCompareMode.STRICT);
        verify(service).findDetail(DEVICE_PUBLIC_ID, cardId);
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

    @Test
    void 인증된_기기가_날짜와_감정을_지정해_카드를_수령한다() {
        // given
        var card = CollectionCardResult.of(기본_카드_빌더().build(), 기본_카드_이미지());
        when(claims.claim(DEVICE_PUBLIC_ID, LocalDate.of(2026, 10, 22), EmotionState.ANGRY)).thenReturn(card);

        // when
        var response = client.post().uri("/api/v1/collection-cards/claim")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"date":"2026-10-22","state":"ANGRY"}
                        """).exchange();

        // then
        response.expectStatus().isOk().expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().jsonPath("$.cardId").isEqualTo(card.cardId().toString())
                .jsonPath("$.date").isEqualTo("2026-10-22")
                .jsonPath("$.state").isEqualTo("ANGRY")
                .jsonPath("$.cycle").doesNotHaveJsonPath()
                .jsonPath("$.combinationIndex").doesNotHaveJsonPath()
                .jsonPath("$.images.characterUrl").isEqualTo("https://assets.example/v1/characters/angry/01.png")
                .jsonPath("$.images.roundStampUrl").isEqualTo("https://assets.example/v1/stamps-round/angry/01.png")
                .jsonPath("$.images.straightStampUrl").isEqualTo("https://assets.example/v1/stamps-straight/angry/01.png")
                .jsonPath("$.cardVersion").doesNotHaveJsonPath()
                .jsonPath("$.claimedAt").doesNotHaveJsonPath();
        verify(claims).claim(DEVICE_PUBLIC_ID, LocalDate.of(2026, 10, 22), EmotionState.ANGRY);
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "invalid-token")
    void 인증이_없거나_잘못되면_카드를_수령할_수_없다(String token) {
        // given
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid token"));
        var request = client.post().uri("/api/v1/collection-cards/claim")
                .contentType(MediaType.APPLICATION_JSON).body("""
                        {"date":"2026-10-22","state":"ANGRY"}
                        """);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        // when
        var response = request.exchange();

        // then
        response.expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("AUTH-001");
        verifyNoInteractions(claims);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"date\":\"2026-10-22\"}", "{\"state\":\"ANGRY\"}",
            "{\"date\":null,\"state\":null}", "{\"date\":\"2026-99-99\",\"state\":\"ANGRY\"}",
            "{\"date\":\"2026-10-22\",\"state\":\"UNKNOWN\"}"})
    void 날짜와_감정이_없거나_잘못된_수령_요청은_400을_반환한다(String body) {
        // given / when
        var response = client.post().uri("/api/v1/collection-cards/claim")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON).body(body).exchange();

        // then
        response.expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(claims);
    }

    @ParameterizedTest
    @CsvSource({"COLLECTION_CLAIM_DATE_INVALID,400,COLLECTION-002", "COLLECTION_REQUIREMENTS_NOT_MET,409,COLLECTION-003",
            "COLLECTION_ASSETS_NOT_READY,503,COLLECTION-004"})
    void 수령_거절_이유별_상태와_오류_코드를_반환한다(CollectionErrorCode errorCode, int status, String code) {
        // given
        when(claims.claim(DEVICE_PUBLIC_ID, LocalDate.of(2026, 10, 22), EmotionState.ANGRY))
                .thenThrow(new CollectionException(errorCode));

        // when
        var response = client.post().uri("/api/v1/collection-cards/claim")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON).body("""
                        {"date":"2026-10-22","state":"ANGRY"}
                        """).exchange();

        // then
        response.expectStatus().isEqualTo(status).expectBody().jsonPath("$.code").isEqualTo(code);
    }
    @Test
    void 오늘_조회는_수령한_카드와_이미지를_반환하고_내부_추첨_정보는_노출하지_않는다() {
        // given
        var card = CollectionCardResult.of(기본_카드_빌더().build(), 기본_카드_이미지());
        var items = Arrays.stream(EmotionState.values())
                .map(state -> CollectionTodayResult.Item.of(state, 1, 100,
                        state == EmotionState.ANGRY ? card : null)).toList();
        when(service.findToday(DEVICE_PUBLIC_ID)).thenReturn(CollectionTodayResult.of(card.date(), items));

        // when
        var response = client.get().uri(URI).header(HttpHeaders.AUTHORIZATION, "Bearer access-token").exchange();

        // then
        response.expectStatus().isOk().expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().jsonPath("$.items[4].card.cardId").isEqualTo(card.cardId().toString())
                .jsonPath("$.items[4].card.date").isEqualTo(card.date().toString())
                .jsonPath("$.items[4].card.state").isEqualTo("ANGRY")
                .jsonPath("$.items[4].card.images.characterUrl").isEqualTo(card.images().characterUrl())
                .jsonPath("$.items[4].card.images.roundStampUrl").isEqualTo(card.images().roundStampUrl())
                .jsonPath("$.items[4].card.images.straightStampUrl").isEqualTo(card.images().straightStampUrl())
                .jsonPath("$.items[4].card.cycle").doesNotHaveJsonPath()
                .jsonPath("$.items[4].card.combinationIndex").doesNotHaveJsonPath()
                .jsonPath("$.items[4].card.cardVersion").doesNotHaveJsonPath()
                .jsonPath("$.items[4].card.claimedAt").doesNotHaveJsonPath()
                .jsonPath("$.items[0].card").isEmpty();
        verifyNoInteractions(claims);
    }

    @Test
    void 오늘_카드의_이미지_주소를_구성하지_못하면_503을_반환한다() {
        // given
        when(service.findToday(DEVICE_PUBLIC_ID))
                .thenThrow(new CollectionException(CollectionErrorCode.COLLECTION_ASSETS_NOT_READY));

        // when / then
        client.get().uri(URI).header(HttpHeaders.AUTHORIZATION, "Bearer access-token").exchange()
                .expectStatus().isEqualTo(503).expectBody().jsonPath("$.code").isEqualTo("COLLECTION-004");
        verifyNoInteractions(claims);
    }

}
