package com.pheeeew.collection.presentation;

import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_카드_빌더;
import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_카드_이미지;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.auth.fixture.AccessTokenFixture;
import com.pheeeew.auth.infra.security.AuthenticationErrorHandler;
import com.pheeeew.auth.infra.security.SecurityConfig;
import com.pheeeew.auth.presentation.config.AuthWebMvcConfig;
import com.pheeeew.auth.presentation.resolver.CurrentDeviceArgumentResolver;
import com.pheeeew.collection.application.CollectionClaimService;
import com.pheeeew.collection.application.CollectionQueryService;
import com.pheeeew.collection.application.dto.CollectionCardResult;
import com.pheeeew.collection.application.dto.CollectionCardDetailResult;
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
import org.junit.jupiter.params.provider.CsvSource;
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
    private CollectionClaimService claims;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode("access-token")).thenReturn(AccessTokenFixture.액세스_토큰_클레임(DEVICE_PUBLIC_ID));
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
