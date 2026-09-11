package com.pheeeew.sigh.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.sigh.application.SighService;
import com.pheeeew.sigh.application.dto.SighMapItem;
import com.pheeeew.sigh.application.dto.SighMapResult;
import com.pheeeew.sigh.application.dto.SighResult;
import com.pheeeew.sigh.application.dto.SighSaveResult;
import com.pheeeew.sigh.application.dto.SighSearchBounds;
import com.pheeeew.sigh.exception.SighErrorCode;
import com.pheeeew.sigh.exception.SighException;
import com.pheeeew.sigh.presentation.dto.SighCreateV1Request;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
@WebMvcTest(SighV1Controller.class)
class SighV1ControllerTest {

    private static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");
    private static final UUID REQUEST_ID = UUID.fromString("5d1ad34e-1e20-4f20-a20e-3825a095fe6b");
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T10:30:00Z");
    private static final Instant NEXT_CREATED_AT = Instant.parse("2026-08-31T10:31:00Z");
    private static final SighSearchBounds BOUNDS = SighSearchBounds.of(127.10, 37.30, 127.20, 37.40);
    private static final SighSearchBounds DATE_LINE_BOUNDS = SighSearchBounds.of(170.0, -10.0, -170.0, 10.0);

    private final RestTestClient client;

    @MockitoBean
    private SighService sighService;

    @Autowired
    SighV1ControllerTest(RestTestClient client) {
        this.client = client;
    }

    @Test
    void 지도_영역의_한숨을_GeoJSON_FeatureCollection으로_반환한다() {
        // given
        when(sighService.findAllWithinBounds(BOUNDS))
                .thenReturn(SighMapResult.of(
                        List.of(
                                SighMapItem.of(2L, 127.1109, 37.3826, NEXT_CREATED_AT),
                                SighMapItem.of(1L, 127.1258, 37.3467, CREATED_AT)
                        ),
                        true
                ));

        // when
        RestTestClient.ResponseSpec result = 한숨을_조회한다(
                "/api/v1/sighs?minLongitude=127.10&minLatitude=37.30"
                        + "&maxLongitude=127.20&maxLatitude=37.40"
        );

        // then
        result.expectStatus().isOk()
                .expectHeader().contentType(GEO_JSON)
                .expectBody()
                .json("""
                        {
                          "type": "FeatureCollection",
                          "truncated": true,
                          "features": [
                            {
                              "type": "Feature",
                              "id": 2,
                              "geometry": {
                                "type": "Point",
                                "coordinates": [127.1109, 37.3826]
                              },
                              "properties": {
                                "createdAt": "2026-08-31T10:31:00Z"
                              }
                            },
                            {
                              "type": "Feature",
                              "id": 1,
                              "geometry": {
                                "type": "Point",
                                "coordinates": [127.1258, 37.3467]
                              },
                              "properties": {
                                "createdAt": "2026-08-31T10:30:00Z"
                              }
                            }
                          ]
                        }
                        """, JsonCompareMode.STRICT);
        verify(sighService).findAllWithinBounds(BOUNDS);
    }

    @Test
    void 지도_영역에_한숨이_없으면_빈_FeatureCollection을_반환한다() {
        // given
        when(sighService.findAllWithinBounds(BOUNDS))
                .thenReturn(SighMapResult.of(List.of(), false));

        // when
        RestTestClient.ResponseSpec result = 한숨을_조회한다(
                "/api/v1/sighs?minLongitude=127.10&minLatitude=37.30"
                        + "&maxLongitude=127.20&maxLatitude=37.40"
        );

        // then
        result.expectStatus().isOk()
                .expectHeader().contentType(GEO_JSON)
                .expectBody()
                .json("""
                        {
                          "type": "FeatureCollection",
                          "truncated": false,
                          "features": []
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    void 날짜변경선을_가로지르는_지도_영역을_조회한다() {
        // given
        when(sighService.findAllWithinBounds(DATE_LINE_BOUNDS))
                .thenReturn(SighMapResult.of(List.of(), false));

        // when
        RestTestClient.ResponseSpec result = 한숨을_조회한다(
                "/api/v1/sighs?minLongitude=170&minLatitude=-10"
                        + "&maxLongitude=-170&maxLatitude=10"
        );

        // then
        result.expectStatus().isOk();
        verify(sighService).findAllWithinBounds(DATE_LINE_BOUNDS);
    }

    @ParameterizedTest
    @MethodSource("올바르지_않은_지도_영역들")
    void 지도_영역의_필수값_범위_또는_크기가_올바르지_않으면_400을_반환한다(String uri) {
        // given / when
        RestTestClient.ResponseSpec result = 한숨을_조회한다(uri);

        // then
        오류를_검증한다(result, 400, "COMMON-001", "요청 값이 올바르지 않습니다.");
        verifyNoInteractions(sighService);
    }

    @Test
    void 한숨을_최초_등록하면_201과_GeoJSON_Feature를_반환한다() {
        // given
        SighCreateV1Request request = 기본_요청();
        when(sighService.save(REQUEST_ID, 126.9780, 37.5664))
                .thenReturn(기본_저장_결과(true));

        // when
        RestTestClient.ResponseSpec result = 한숨을_등록한다(request);

        // then
        result.expectStatus().isCreated()
                .expectHeader().contentType(GEO_JSON)
                .expectHeader().doesNotExist(HttpHeaders.LOCATION)
                .expectBody()
                .json(기본_GeoJSON(), JsonCompareMode.STRICT);
        verify(sighService).save(REQUEST_ID, 126.9780, 37.5664);
    }

    @Test
    void application_json_응답을_요청해도_406_없이_한숨을_등록한다() {
        // given
        when(sighService.save(REQUEST_ID, 126.9780, 37.5664))
                .thenReturn(기본_저장_결과(true));

        // when
        RestTestClient.ResponseSpec result = client.post()
                .uri("/api/v1/sighs")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(기본_요청())
                .exchange();

        // then
        result.expectStatus().isCreated()
                .expectHeader().contentType(GEO_JSON);
        verify(sighService).save(REQUEST_ID, 126.9780, 37.5664);
    }

    @Test
    void 같은_requestId로_재시도하면_200과_최초_GeoJSON_Feature를_반환한다() {
        // given
        SighCreateV1Request request = new SighCreateV1Request(REQUEST_ID, 35.1796, 129.0756);
        when(sighService.save(REQUEST_ID, 129.0756, 35.1796))
                .thenReturn(기본_저장_결과(false));

        // when
        RestTestClient.ResponseSpec result = 한숨을_등록한다(request);

        // then
        result.expectStatus().isOk()
                .expectHeader().contentType(GEO_JSON)
                .expectBody()
                .json(기본_GeoJSON(), JsonCompareMode.STRICT);
        verify(sighService).save(REQUEST_ID, 129.0756, 35.1796);
    }

    @ParameterizedTest
    @MethodSource("올바르지_않은_요청들")
    void 필수값이_없거나_좌표_범위를_벗어나면_400을_반환한다(SighCreateV1Request request) {
        // given / when
        RestTestClient.ResponseSpec result = 한숨을_등록한다(request);

        // then
        오류를_검증한다(result, 400, "COMMON-001", "요청 값이 올바르지 않습니다.");
    }

    @Test
    void 요청_본문이_없으면_400을_반환한다() {
        // given / when
        RestTestClient.ResponseSpec result = client.post()
                .uri("/api/v1/sighs")
                .exchange();

        // then
        오류를_검증한다(result, 400, "COMMON-001", "요청 값이 올바르지 않습니다.");
    }

    @Test
    void requestId가_UUID_형식이_아니면_400을_반환한다() {
        // given / when
        RestTestClient.ResponseSpec result = client.post()
                .uri("/api/v1/sighs")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                          "requestId": "not-a-uuid",
                          "latitude": 37.5664,
                          "longitude": 126.9780
                        }
                        """)
                .exchange();

        // then
        오류를_검증한다(result, 400, "COMMON-001", "요청 값이 올바르지 않습니다.");
    }

    @Test
    void 한숨_도메인_예외는_정의된_상태와_코드로_반환한다() {
        // given
        when(sighService.save(REQUEST_ID, 126.9780, 37.5664))
                .thenThrow(new SighException(SighErrorCode.SIGH_SAVE_FAILED, new IllegalStateException()));

        // when
        RestTestClient.ResponseSpec result = 한숨을_등록한다(기본_요청());

        // then
        오류를_검증한다(result, 500, "SIGH-001", "한숨을 저장하지 못했습니다.");
    }

    @Test
    void 예상하지_못한_예외는_내부_메시지를_노출하지_않는다() {
        // given
        when(sighService.save(REQUEST_ID, 126.9780, 37.5664))
                .thenThrow(new IllegalStateException("외부에 노출되면 안 되는 메시지"));

        // when
        RestTestClient.ResponseSpec result = 한숨을_등록한다(기본_요청());

        // then
        오류를_검증한다(result, 500, "COMMON-002", "서버 내부 오류가 발생했습니다.");
    }

    private RestTestClient.ResponseSpec 한숨을_조회한다(String uri) {
        return client.get()
                .uri(uri)
                .exchange();
    }

    private void 오류를_검증한다(
            RestTestClient.ResponseSpec result,
            int status,
            String code,
            String message
    ) {
        result.expectStatus().isEqualTo(status)
                .expectBody()
                .json("""
                        {"code":"%s","message":"%s"}
                        """.formatted(code, message), JsonCompareMode.STRICT);
    }

    private SighCreateV1Request 기본_요청() {
        return new SighCreateV1Request(REQUEST_ID, 37.5664, 126.9780);
    }

    private SighSaveResult 기본_저장_결과(boolean created) {
        return SighSaveResult.of(
                SighResult.of(
                        42L,
                        126.9774,
                        37.5669,
                        CREATED_AT,
                        null,
                        "외로운 회사원"
                ),
                created
        );
    }

    private RestTestClient.ResponseSpec 한숨을_등록한다(SighCreateV1Request request) {
        return client.post()
                .uri("/api/v1/sighs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    private String 기본_GeoJSON() {
        return """
                {
                  "type": "Feature",
                  "id": 42,
                  "geometry": {
                    "type": "Point",
                    "coordinates": [126.9774, 37.5669]
                  },
                  "properties": {
                    "createdAt": "2026-08-31T10:30:00Z"
                  }
                }
                """;
    }

    private static Stream<String> 올바르지_않은_지도_영역들() {
        return Stream.of(
                "/api/v1/sighs?minLongitude=127.10&minLatitude=37.30&maxLongitude=127.20",
                "/api/v1/sighs?minLongitude=-180.01&minLatitude=37.30"
                        + "&maxLongitude=127.20&maxLatitude=37.40",
                "/api/v1/sighs?minLongitude=127.10&minLatitude=37.30"
                        + "&maxLongitude=127.10&maxLatitude=37.40",
                "/api/v1/sighs?minLongitude=127.10&minLatitude=37.40"
                        + "&maxLongitude=127.20&maxLatitude=37.40"
        );
    }

    private static Stream<Arguments> 올바르지_않은_요청들() {
        return Stream.of(
                Arguments.of(new SighCreateV1Request(null, 37.5664, 126.9780)),
                Arguments.of(new SighCreateV1Request(REQUEST_ID, null, 126.9780)),
                Arguments.of(new SighCreateV1Request(REQUEST_ID, 90.0001, 126.9780)),
                Arguments.of(new SighCreateV1Request(REQUEST_ID, 37.5664, -180.0001))
        );
    }
}
