package com.pheeeew.sigh.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.sigh.application.SighService;
import com.pheeeew.sigh.application.dto.SighListResult;
import com.pheeeew.sigh.application.dto.SighResult;
import com.pheeeew.sigh.application.dto.SighSaveResult;
import com.pheeeew.sigh.application.dto.SighSearchBounds;
import com.pheeeew.sigh.exception.SighErrorCode;
import com.pheeeew.sigh.exception.SighException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
@Import(GlobalExceptionHandler.class)
@WebMvcTest(SighV2Controller.class)
class SighV2ControllerTest {

    private static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");
    private static final Long SIGH_ID = 42L;
    private static final UUID REQUEST_ID = UUID.fromString("5d1ad34e-1e20-4f20-a20e-3825a095fe6b");
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T12:00:00Z");

    private final RestTestClient client;

    @MockitoBean
    private SighService sighService;

    @Autowired
    SighV2ControllerTest(RestTestClient client) {
        this.client = client;
    }

    @Test
    void 검색_영역으로_바텀시트_첫_페이지를_조회한다() {
        // given
        SighSearchBounds bounds = SighSearchBounds.of(126.9, 37.5, 127.1, 37.6);
        when(sighService.findFirstListPage(bounds))
                .thenReturn(SighListResult.of(
                        List.of(SighResult.of(
                                SIGH_ID,
                                126.9774,
                                37.5669,
                                CREATED_AT,
                                "오늘은 조금 지쳤다",
                                "날아가는 고라니"
                        )),
                        true,
                        "next-cursor"
                ));

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri("/api/v2/sighs?minLongitude=126.9&minLatitude=37.5&maxLongitude=127.1&maxLatitude=37.6")
                .accept(MediaType.APPLICATION_JSON)
                .exchange();

        // then
        result.expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .json("""
                        {
                          "items": [
                            {
                              "type": "Feature",
                              "id": 42,
                              "geometry": {
                                "type": "Point",
                                "coordinates": [126.9774, 37.5669]
                              },
                              "properties": {
                                "createdAt": "2026-09-01T12:00:00Z",
                                "memo": "오늘은 조금 지쳤다",
                                "nickname": "날아가는 고라니"
                              }
                            }
                          ],
                          "hasNext": true,
                          "nextCursor": "next-cursor"
                        }
                        """, JsonCompareMode.STRICT);
        verify(sighService).findFirstListPage(bounds);
    }

    @Test
    void 날짜변경선을_가로지르는_검색_영역으로_첫_페이지를_조회한다() {
        // given
        SighSearchBounds bounds = SighSearchBounds.of(170.0, -10.0, -170.0, 10.0);
        when(sighService.findFirstListPage(bounds))
                .thenReturn(SighListResult.of(List.of(), false, null));

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri("/api/v2/sighs?minLongitude=170&minLatitude=-10&maxLongitude=-170&maxLatitude=10")
                .exchange();

        // then
        result.expectStatus().isOk();
        verify(sighService).findFirstListPage(bounds);
    }

    @Test
    void 서버가_발급한_커서만으로_바텀시트_다음_페이지를_조회한다() {
        // given
        when(sighService.findNextListPage("opaque-cursor"))
                .thenReturn(SighListResult.of(
                        List.of(SighResult.of(
                                SIGH_ID,
                                126.9774,
                                37.5669,
                                CREATED_AT,
                                null,
                                "날아가는 고라니"
                        )),
                        false,
                        null
                ));

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri("/api/v2/sighs?cursor=opaque-cursor")
                .exchange();

        // then
        result.expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .json("""
                        {
                          "items": [
                            {
                              "type": "Feature",
                              "id": 42,
                              "geometry": {
                                "type": "Point",
                                "coordinates": [126.9774, 37.5669]
                              },
                              "properties": {
                                "createdAt": "2026-09-01T12:00:00Z",
                                "memo": null,
                                "nickname": "날아가는 고라니"
                              }
                            }
                          ],
                          "hasNext": false,
                          "nextCursor": null
                        }
                        """, JsonCompareMode.STRICT);
        verify(sighService).findNextListPage("opaque-cursor");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v2/sighs",
            "/api/v2/sighs?minLongitude=126.9&minLatitude=37.5",
            "/api/v2/sighs?minLongitude=126.9&minLatitude=37.5&maxLongitude=127.1&maxLatitude=37.6&cursor=opaque-cursor",
            "/api/v2/sighs?minLongitude=126.9&minLatitude=37.6&maxLongitude=127.1&maxLatitude=37.5",
            "/api/v2/sighs?minLongitude=126.9&minLatitude=37.5&maxLongitude=126.9&maxLatitude=37.6",
            "/api/v2/sighs?minLongitude=-181&minLatitude=37.5&maxLongitude=127.1&maxLatitude=37.6"
    })
    void 첫_페이지와_다음_페이지_요청_계약을_지키지_않으면_400을_반환한다(String uri) {
        // given / when
        RestTestClient.ResponseSpec result = client.get()
                .uri(uri)
                .exchange();

        // then
        result.expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .json("""
                        {"code":"COMMON-001","message":"요청 값이 올바르지 않습니다."}
                        """, JsonCompareMode.STRICT);
        verifyNoInteractions(sighService);
    }

    @Test
    void 사용할_수_없는_커서는_400을_반환한다() {
        // given
        when(sighService.findNextListPage("invalid-cursor"))
                .thenThrow(new SighException(SighErrorCode.SIGH_INVALID_CURSOR));

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri("/api/v2/sighs?cursor=invalid-cursor")
                .exchange();

        // then
        result.expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .json("""
                        {"code":"SIGH-003","message":"한숨 목록 커서를 사용할 수 없습니다."}
                        """, JsonCompareMode.STRICT);
        verify(sighService).findNextListPage("invalid-cursor");
    }

    @Test
    void 메모가_있는_한숨을_최초_등록하면_201과_상세_URI와_메모와_닉네임을_반환한다() {
        // given
        when(sighService.save(REQUEST_ID, 126.9780, 37.5664, "  오늘은 조금 지쳤다  "))
                .thenReturn(기본_저장_결과("오늘은 조금 지쳤다", true));

        // when
        RestTestClient.ResponseSpec result = 한숨을_등록한다("""
                {
                  "requestId": "5d1ad34e-1e20-4f20-a20e-3825a095fe6b",
                  "latitude": 37.5664,
                  "longitude": 126.9780,
                  "memo": "  오늘은 조금 지쳤다  "
                }
                """);

        // then
        result.expectStatus().isCreated()
                .expectHeader().contentType(GEO_JSON)
                .expectHeader().valueEquals(HttpHeaders.LOCATION, "/api/v2/sighs/42")
                .expectBody()
                .json(기본_GeoJSON("\"오늘은 조금 지쳤다\""), JsonCompareMode.STRICT);
        verify(sighService).save(REQUEST_ID, 126.9780, 37.5664, "  오늘은 조금 지쳤다  ");
    }

    @Test
    void application_json_응답을_요청해도_406_없이_한숨을_등록한다() {
        // given
        when(sighService.save(REQUEST_ID, 126.9780, 37.5664, null))
                .thenReturn(기본_저장_결과(null, true));

        // when
        RestTestClient.ResponseSpec result = client.post()
                .uri("/api/v2/sighs")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body("""
                        {
                          "requestId": "5d1ad34e-1e20-4f20-a20e-3825a095fe6b",
                          "latitude": 37.5664,
                          "longitude": 126.9780
                        }
                        """)
                .exchange();

        // then
        result.expectStatus().isCreated()
                .expectHeader().contentType(GEO_JSON);
        verify(sighService).save(REQUEST_ID, 126.9780, 37.5664, null);
    }

    @Test
    void 메모를_생략하면_null로_등록하고_반환한다() {
        // given
        when(sighService.save(REQUEST_ID, 126.9780, 37.5664, null))
                .thenReturn(기본_저장_결과(null, true));

        // when
        RestTestClient.ResponseSpec result = 한숨을_등록한다("""
                {
                  "requestId": "5d1ad34e-1e20-4f20-a20e-3825a095fe6b",
                  "latitude": 37.5664,
                  "longitude": 126.9780
                }
                """);

        // then
        result.expectStatus().isCreated()
                .expectHeader().contentType(GEO_JSON)
                .expectBody()
                .json(기본_GeoJSON("null"), JsonCompareMode.STRICT);
        verify(sighService).save(REQUEST_ID, 126.9780, 37.5664, null);
    }

    @Test
    void 공백으로만_이루어진_메모는_null로_등록한다() {
        // given
        when(sighService.save(REQUEST_ID, 126.9780, 37.5664, "   "))
                .thenReturn(기본_저장_결과(null, true));

        // when
        RestTestClient.ResponseSpec result = 한숨을_등록한다("""
                {
                  "requestId": "5d1ad34e-1e20-4f20-a20e-3825a095fe6b",
                  "latitude": 37.5664,
                  "longitude": 126.9780,
                  "memo": "   "
                }
                """);

        // then
        result.expectStatus().isCreated();
        verify(sighService).save(REQUEST_ID, 126.9780, 37.5664, "   ");
    }

    @Test
    void 같은_requestId로_재시도하면_200과_최초_메모와_닉네임을_반환한다() {
        // given
        when(sighService.save(REQUEST_ID, 129.0756, 35.1796, "재시도 메모"))
                .thenReturn(기본_저장_결과("최초 메모", false));

        // when
        RestTestClient.ResponseSpec result = 한숨을_등록한다("""
                {
                  "requestId": "5d1ad34e-1e20-4f20-a20e-3825a095fe6b",
                  "latitude": 35.1796,
                  "longitude": 129.0756,
                  "memo": "재시도 메모"
                }
                """);

        // then
        result.expectStatus().isOk()
                .expectHeader().contentType(GEO_JSON)
                .expectHeader().doesNotExist(HttpHeaders.LOCATION)
                .expectBody()
                .json(기본_GeoJSON("\"최초 메모\""), JsonCompareMode.STRICT);
        verify(sighService).save(REQUEST_ID, 129.0756, 35.1796, "재시도 메모");
    }

    @Test
    void 앞뒤_공백을_제외한_메모가_50자이면_등록한다() {
        // given
        String memo = "가".repeat(50);
        String requestedMemo = "  " + memo + "  ";
        when(sighService.save(REQUEST_ID, 126.9780, 37.5664, requestedMemo))
                .thenReturn(기본_저장_결과(memo, true));

        // when
        RestTestClient.ResponseSpec result = 한숨을_등록한다("""
                {
                  "requestId": "5d1ad34e-1e20-4f20-a20e-3825a095fe6b",
                  "latitude": 37.5664,
                  "longitude": 126.9780,
                  "memo": "%s"
                }
                """.formatted(requestedMemo));

        // then
        result.expectStatus().isCreated();
        verify(sighService).save(REQUEST_ID, 126.9780, 37.5664, requestedMemo);
    }

    @Test
    void 정규화된_메모가_50자를_초과하면_400을_반환한다() {
        // given
        String overlongMemo = "가".repeat(51);

        // when
        RestTestClient.ResponseSpec result = 한숨을_등록한다("""
                {
                  "requestId": "5d1ad34e-1e20-4f20-a20e-3825a095fe6b",
                  "latitude": 37.5664,
                  "longitude": 126.9780,
                  "memo": "%s"
                }
                """.formatted(overlongMemo));

        // then
        result.expectStatus().isBadRequest()
                .expectBody()
                .json("""
                        {"code":"COMMON-001","message":"요청 값이 올바르지 않습니다."}
                        """, JsonCompareMode.STRICT);
        verifyNoInteractions(sighService);
    }

    @Test
    void application_json_응답을_요청해도_한숨_상세를_GeoJSON_Feature로_반환한다() {
        // given
        when(sighService.findById(SIGH_ID))
                .thenReturn(기본_상세_조회_결과("오늘은 조금 지쳤다"));

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri("/api/v2/sighs/{id}", SIGH_ID)
                .accept(MediaType.APPLICATION_JSON)
                .exchange();

        // then
        result.expectStatus().isOk()
                .expectHeader().contentType(GEO_JSON)
                .expectBody()
                .json(기본_GeoJSON("\"오늘은 조금 지쳤다\""), JsonCompareMode.STRICT);
        verify(sighService).findById(SIGH_ID);
    }

    @Test
    void 메모가_없는_한숨_상세는_memo를_null로_반환한다() {
        // given
        when(sighService.findById(SIGH_ID))
                .thenReturn(기본_상세_조회_결과(null));

        // when
        RestTestClient.ResponseSpec result = 한숨_상세를_조회한다(SIGH_ID.toString());

        // then
        result.expectStatus().isOk()
                .expectHeader().contentType(GEO_JSON)
                .expectBody()
                .json(기본_GeoJSON("null"), JsonCompareMode.STRICT);
        verify(sighService).findById(SIGH_ID);
    }

    @Test
    void 존재하지_않는_한숨_상세를_조회하면_404를_반환한다() {
        // given
        when(sighService.findById(SIGH_ID))
                .thenThrow(new SighException(SighErrorCode.SIGH_NOT_FOUND));

        // when
        RestTestClient.ResponseSpec result = 한숨_상세를_조회한다(SIGH_ID.toString());

        // then
        result.expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .json("""
                        {"code":"SIGH-002","message":"한숨을 찾을 수 없습니다."}
                        """, JsonCompareMode.STRICT);
        verify(sighService).findById(SIGH_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "0", "-1"})
    void 한숨_ID_형식이_올바르지_않거나_1보다_작으면_400을_반환한다(String id) {
        // given / when
        RestTestClient.ResponseSpec result = 한숨_상세를_조회한다(id);

        // then
        result.expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .json("""
                        {"code":"COMMON-001","message":"요청 값이 올바르지 않습니다."}
                        """, JsonCompareMode.STRICT);
        verifyNoInteractions(sighService);
    }

    private RestTestClient.ResponseSpec 한숨을_등록한다(String body) {
        return client.post()
                .uri("/api/v2/sighs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private RestTestClient.ResponseSpec 한숨_상세를_조회한다(String id) {
        return client.get()
                .uri("/api/v2/sighs/{id}", id)
                .exchange();
    }

    private SighSaveResult 기본_저장_결과(String memo, boolean created) {
        return SighSaveResult.of(
                SighResult.of(
                        42L,
                        126.9774,
                        37.5669,
                        CREATED_AT,
                        memo,
                        "날아가는 고라니"
                ),
                created
        );
    }

    private SighResult 기본_상세_조회_결과(String memo) {
        return SighResult.of(
                SIGH_ID,
                126.9774,
                37.5669,
                CREATED_AT,
                memo,
                "날아가는 고라니"
        );
    }

    private String 기본_GeoJSON(String memo) {
        return """
                {
                  "type": "Feature",
                  "id": 42,
                  "geometry": {
                    "type": "Point",
                    "coordinates": [126.9774, 37.5669]
                  },
                  "properties": {
                    "createdAt": "2026-09-01T12:00:00Z",
                    "memo": %s,
                    "nickname": "날아가는 고라니"
                  }
                }
                """.formatted(memo);
    }
}
