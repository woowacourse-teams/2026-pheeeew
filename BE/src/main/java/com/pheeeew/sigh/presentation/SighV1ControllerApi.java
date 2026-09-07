package com.pheeeew.sigh.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.sigh.presentation.dto.SighCreateV1Request;
import com.pheeeew.sigh.presentation.dto.SighFeature;
import com.pheeeew.sigh.presentation.dto.SighMapRequest;
import com.pheeeew.sigh.presentation.dto.SighMapResponse;
import com.pheeeew.sigh.presentation.dto.SighV1Properties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.StringToClassMapItem;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springdoc.core.annotations.ParameterObject;

@Tag(name = "한숨", description = "한숨 등록과 지도 영역 조회 API")
public interface SighV1ControllerApi {

    @Operation(
            summary = "지도 영역 내 한숨 조회",
            description = """
                    ### 지도 영역

                    - WGS84 경계 상자를 `minLongitude`, `minLatitude`, `maxLongitude`, `maxLatitude`로 전달합니다.
                    - `minLongitude`가 `maxLongitude`보다 작으면 일반 영역을 조회합니다.
                    - `minLongitude`가 `maxLongitude`보다 크면 날짜변경선을 가로지르는 영역을 조회합니다.
                    - 두 경도가 같거나 `minLatitude`가 `maxLatitude`보다 크거나 같으면 조회할 수 없습니다.
                    - 경계선 위에 저장된 한숨도 조회 결과에 포함합니다.

                    ### 조회 결과

                    - DB에 저장된 최종 표시 위치를 최신순으로 최대 500건 반환합니다.
                    - 결과가 500건을 초과하면 `truncated`가 `true`입니다.
                    - 결과가 없으면 `truncated`가 `false`이고 `features`는 빈 배열입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "지도 영역 내 한숨 조회 성공",
                    content = @Content(
                            mediaType = "application/geo+json",
                            schema = @Schema(implementation = SighMapResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "필수 좌표가 없거나 좌표 범위 또는 조회할 영역의 너비나 높이가 올바르지 않음",
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
                    description = "한숨을 조회하지 못한 서버 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<SighMapResponse> findAllWithinBounds(
            @ParameterObject SighMapRequest request
    );

    @Operation(
            summary = "한숨 등록",
            description = """
                    ### 중복 요청

                    - 한 번의 등록 시도마다 새로운 `requestId`를 사용합니다.
                    - 같은 `requestId`로 재시도하면 전달 좌표와 무관하게 최초 등록 결과를 반환합니다.

                    ### 위치

                    - `latitude`와 `longitude`에는 실제 위치 주변 반경 300m 원 내부에서 클라이언트가 면적 균등하게 고른 근사 좌표를 WGS84로 전달합니다.
                    - 300m는 EPSG:5179 평면 거리 기준입니다. 격자 중심으로 정렬하거나 실제 위치를 전송하지 않습니다.
                    - 서버는 받은 근사 좌표 주변 반경 300m 원 내부에서 독립적으로 면적 균등 추첨한 최종 위치를 저장합니다.
                    - 클라이언트와 서버 이동을 합하면 실제 위치 기준 최대 600m까지 이동할 수 있습니다. 지표면 거리에는 투영 오차가 있습니다.
                    - 같은 등록의 재시도에는 같은 `requestId`와 이미 만든 근사 좌표를 재사용하고, 조회에서는 저장된 최종 위치를 그대로 사용합니다.
                    - 서버는 받은 좌표만으로 클라이언트의 무작위화 여부를 판별할 수 없습니다. 기존 격자 입력 앱은 새 계약으로 전환해야 합니다.
                    - 응답 좌표는 GeoJSON과 MapLibre가 사용하는 `[longitude, latitude]` 순서입니다.
                    - 상세 조회 API가 없으므로 `Location` 헤더는 제공하지 않습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "한숨 최초 등록 성공",
                    content = @Content(
                            mediaType = "application/geo+json",
                            schema = @Schema(
                                    allOf = SighFeature.class,
                                    properties = @StringToClassMapItem(
                                            key = "properties",
                                            value = SighV1Properties.class
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
                                            value = SighV1Properties.class
                                    )
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 본문, UUID 또는 좌표 범위가 올바르지 않음",
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
    ResponseEntity<SighFeature<SighV1Properties>> save(SighCreateV1Request request);
}
