package com.pheeeew.sigh.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.common.presentation.dto.CursorResponse;
import com.pheeeew.sigh.presentation.dto.SighCreateV2Request;
import com.pheeeew.sigh.presentation.dto.SighFeature;
import com.pheeeew.sigh.presentation.dto.SighListRequest;
import com.pheeeew.sigh.presentation.dto.SighV2Properties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.StringToClassMapItem;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springdoc.core.annotations.ParameterObject;

@Tag(name = "한숨 v2", description = "메모와 익명 닉네임을 포함한 한숨 등록과 목록·상세 조회 API")
public interface SighV2ControllerApi {

    @Operation(
            summary = "지도 바텀시트용 한숨 목록 조회",
            description = """
                    ### 첫 페이지

                    - WGS84 검색 영역을 네 좌표로 모두 전달하고 `cursor`는 전달하지 않습니다.
                    - 날짜변경선을 가로지르는 영역은 `minLongitude`를 `maxLongitude`보다 크게 전달합니다.
                    - 검색 영역의 경계를 포함하며 삭제되지 않은 최신 한숨을 조회합니다.

                    ### 다음 페이지

                    - 서버가 직전 응답에서 발급한 `nextCursor`만 수정하지 않고 전달합니다.
                    - 좌표와 `cursor`를 함께 전달하거나 모두 생략하면 조회할 수 없습니다.
                    - 커서에 첫 요청의 검색 영역과 스냅샷이 고정되어 이후 등록된 한숨은 섞이지 않습니다.

                    ### 조회 결과

                    - `createdAt DESC`, `id DESC` 순으로 페이지당 20건을 반환합니다.
                    - 현재 검색 영역의 최신 500건까지만 페이지로 조회할 수 있습니다.
                    - `geometry`는 저장된 최종 표시 위치이며 좌표는 `[longitude, latitude]` 순서입니다.
                    - 메모가 없는 경우 `properties.memo`는 `null`입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "한숨 목록 조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = """
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
                                      "nextCursor": "opaque-cursor"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "좌표, 요청 조합 또는 커서가 올바르지 않음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "잘못된 요청 조합",
                                            value = """
                                                    {"code":"COMMON-001","message":"요청 값이 올바르지 않습니다."}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "사용할 수 없는 커서",
                                            value = """
                                                    {"code":"SIGH-003","message":"한숨 목록 커서를 사용할 수 없습니다."}
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "한숨 목록 조회 처리 중 서버 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    CursorResponse<SighFeature<SighV2Properties>> findAll(
            @ParameterObject @Valid SighListRequest request
    );

    @Operation(
            summary = "한숨 상세 조회",
            description = """
                    - 경로의 `id`로 삭제되지 않은 한숨을 조회합니다.
                    - 등록 시 저장된 최종 표시 위치, 생성 시각, 메모와 익명 닉네임을 반환합니다.
                    - 조회할 때 위치나 닉네임을 다시 생성하지 않습니다.
                    - 메모가 없는 경우 `memo`는 `null`입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "한숨 상세 조회 성공",
                    content = @Content(
                            mediaType = "application/geo+json",
                            schema = @Schema(
                                    allOf = SighFeature.class,
                                    properties = @StringToClassMapItem(
                                            key = "properties",
                                            value = SighV2Properties.class
                                    )
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "한숨 ID 형식이 올바르지 않거나 1보다 작음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"COMMON-001","message":"요청 값이 올바르지 않습니다."}
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "한숨을 찾을 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"SIGH-002","message":"한숨을 찾을 수 없습니다."}
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "한숨 상세 조회 처리 중 서버 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<SighFeature<SighV2Properties>> findById(
            @Parameter(
                    description = "조회할 한숨 ID",
                    example = "42",
                    schema = @Schema(minimum = "1")
            )
            @Min(value = 1, message = "한숨 ID는 1 이상이어야 합니다.")
            Long id
    );

    @Operation(
            summary = "메모와 익명 닉네임을 포함한 한숨 등록",
            description = """
                    ### 메모

                    - `memo`는 생략할 수 있습니다.
                    - 앞뒤 공백은 제거하며 빈 문자열과 공백만 있는 값은 `null`로 저장합니다.
                    - 정규화된 메모는 최대 50자입니다.

                    ### 익명 닉네임

                    - 닉네임은 클라이언트가 전달하지 않고 서버가 최초 등록 시 생성합니다.
                    - 닉네임 중복은 허용합니다.

                    ### 중복 요청

                    - 한 번의 등록 시도마다 새로운 `requestId`를 사용합니다.
                    - 같은 `requestId`로 재시도하면 좌표와 메모가 달라도 최초 등록 결과를 반환합니다.

                    ### 위치

                    - `latitude`와 `longitude`에는 실제 위치 주변 반경 300m 원 내부에서 클라이언트가 면적 균등하게 고른 근사 좌표를 WGS84로 전달합니다.
                    - 300m는 EPSG:5179 평면 거리 기준입니다. 격자 중심으로 정렬하거나 실제 위치를 전송하지 않습니다.
                    - 서버는 받은 근사 좌표 주변 반경 300m 원 내부에서 독립적으로 면적 균등 추첨한 최종 위치를 저장합니다.
                    - 클라이언트와 서버 이동을 합하면 실제 위치 기준 최대 600m까지 이동할 수 있습니다. 지표면 거리에는 투영 오차가 있습니다.
                    - 같은 등록의 재시도에는 같은 `requestId`와 이미 만든 근사 좌표를 재사용하고, 조회에서는 저장된 최종 위치를 그대로 사용합니다.
                    - 서버는 받은 좌표만으로 클라이언트의 무작위화 여부를 판별할 수 없습니다. 기존 격자 입력 앱은 새 계약으로 전환해야 합니다.
                    - 응답 좌표는 `[longitude, latitude]` 순서입니다.
                    - 최초 등록 성공 응답은 생성된 한숨의 상세 URI를 `Location` 헤더로 제공합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "한숨 최초 등록 성공",
                    headers = @Header(
                            name = "Location",
                            description = "생성된 한숨의 상세 조회 URI",
                            schema = @Schema(
                                    type = "string",
                                    format = "uri",
                                    example = "/api/v2/sighs/42"
                            )
                    ),
                    content = @Content(
                            mediaType = "application/geo+json",
                            schema = @Schema(
                                    allOf = SighFeature.class,
                                    properties = @StringToClassMapItem(
                                            key = "properties",
                                            value = SighV2Properties.class
                                    )
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "200",
                    description = "이미 등록된 requestId의 최초 한숨 반환",
                    content = @Content(
                            mediaType = "application/geo+json",
                            schema = @Schema(
                                    allOf = SighFeature.class,
                                    properties = @StringToClassMapItem(
                                            key = "properties",
                                            value = SighV2Properties.class
                                    )
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 본문, UUID, 좌표 범위 또는 메모 길이가 올바르지 않음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"COMMON-001","message":"요청 값이 올바르지 않습니다."}
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "한숨을 저장하지 못했거나 처리하지 못한 서버 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<SighFeature<SighV2Properties>> save(
            @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SighCreateV2Request.class),
                            examples = {
                                    @ExampleObject(
                                            name = "메모 포함",
                                            value = """
                                                    {
                                                      "requestId": "5d1ad34e-1e20-4f20-a20e-3825a095fe6b",
                                                      "latitude": 37.5657576255,
                                                      "longitude": 126.9774258201,
                                                      "memo": "오늘은 조금 지쳤다"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "메모 없음",
                                            value = """
                                                    {
                                                      "requestId": "5d1ad34e-1e20-4f20-a20e-3825a095fe6b",
                                                      "latitude": 37.5657576255,
                                                      "longitude": 126.9774258201
                                                    }
                                                    """
                                    )
                            }
                    )
            )
            @Valid
            SighCreateV2Request request
    );
}
