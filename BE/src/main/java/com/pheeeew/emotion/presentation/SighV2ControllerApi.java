package com.pheeeew.emotion.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.common.presentation.dto.CursorResponse;
import com.pheeeew.emotion.presentation.dto.SighCreateV2Request;
import com.pheeeew.emotion.presentation.dto.SighFeature;
import com.pheeeew.emotion.presentation.dto.SighLikeRequest;
import com.pheeeew.emotion.presentation.dto.SighLikeResponse;
import com.pheeeew.emotion.presentation.dto.SighListRequest;
import com.pheeeew.emotion.presentation.dto.SighV2Properties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.StringToClassMapItem;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "한숨 v2", description = "메모와 익명 닉네임을 포함한 한숨 등록, 목록·상세 조회 및 좋아요 API")
public interface SighV2ControllerApi {

    @Operation(
            summary = "지도 바텀시트용 한숨 목록 조회",
            security = @SecurityRequirement(name = "bearerAuth"),
            description = """
                    ### 인증

                    - `Authorization: Bearer <access token>` 헤더가 필요합니다.
                    - 토큰에 해당하는 기기가 등록되어 있어야 하며, 다음 페이지에서도 확인합니다.

                    ### 첫 페이지

                    - WGS84 검색 영역을 네 좌표로 모두 전달하고 `cursor`는 전달하지 않습니다.
                    - 날짜변경선을 가로지르는 영역은 `minLongitude`를 `maxLongitude`보다 크게 전달합니다.
                    - 검색 영역의 경계를 포함하며 삭제되지 않은 최신 한숨을 조회합니다.

                    ### 조회 기간

                    - 매 요청 시 한국 시간(`Asia/Seoul`) 기준 오늘(0일)부터 13일 전까지 총 14일 범위에서 조회합니다.
                    - 13일 전 자정은 포함하고, 첫 페이지 조회 시각인 스냅샷 시각은 포함하지 않습니다.
                    - 기간이 지난 한숨은 목록에서 제외하며, 상세 조회 시 `410 / SIGH-004`를 반환합니다. 데이터는 삭제하지 않습니다.

                    ### 다음 페이지

                    - 서버가 직전 응답에서 발급한 `nextCursor`만 수정하지 않고 전달합니다.
                    - 좌표와 `cursor`를 함께 전달하거나 모두 생략하면 조회할 수 없습니다.
                    - 커서에 첫 요청의 검색 영역과 스냅샷이 고정되어 이후 등록된 한숨은 섞이지 않습니다.
                    - 자정 이후에도 같은 커서로 이어서 조회할 수 있으며, 현재 조회 기간 밖의 한숨은 이후 응답에서 제외합니다.
                    - 정상 커서라도 현재 조회 기간에 남은 한숨이 없으면 `200`과 빈 `items`, `hasNext: false`, `nextCursor: null`을 반환합니다.
                    - 형식이 잘못되었거나 스냅샷이 현재보다 미래인 커서는 `400 / SIGH-003`으로 거부합니다.

                    ### 조회 결과

                    - `createdAt DESC`, `id DESC` 순으로 페이지당 20건을 반환합니다.
                    - 조회 기간과 검색 영역에 해당하는 최신 500건까지만 페이지로 조회할 수 있습니다.
                    - `geometry`는 저장된 최종 표시 위치이며 좌표는 `[longitude, latitude]` 순서입니다.
                    - 메모가 없는 경우 `properties.memo`는 `null`입니다.
                    - 각 항목의 `properties.liked`는 인증된 기기의 좋아요 여부, `properties.likeCount`는 전체 좋아요 수입니다.
                    - 좋아요 정보는 각 페이지를 조회하는 시점의 값이며, 첫 페이지의 스냅샷 시각에 고정되지 않습니다.

                    ### 차단

                    - 인증한 기기가 차단한 한숨과 차단한 사용자의 한숨은 제외하고 반환합니다.
                    - 작성자 정보가 없는 한숨(`POST /api/v1/sighs`로 등록한 한숨)은 사용자 차단의 영향을 받지 않습니다. 개별 차단으로만 가릴 수 있습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "한숨 목록 조회 성공",
                    headers = @Header(name = "Cache-Control", description = "개인별 조회 응답을 캐시에 저장하지 않습니다.",
                            schema = @Schema(type = "string", example = "no-store")),
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
                                            "nickname": "날아가는 고라니",
                                            "liked": true,
                                            "likeCount": 12
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
                    responseCode = "401",
                    description = "인증할 수 없음. `AUTH-001`은 access token이 없거나 만료된 경우로 `POST /api/v2/devices/tokens`로 갱신한 뒤 재시도합니다. `DEVICE-004`는 토큰은 유효하지만 그 기기가 서버에 없는 경우로 **갱신해도 해결되지 않으며 기기를 다시 등록해야 합니다.**",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "인증 실패", value = """
                                            {"code":"AUTH-001","message":"인증이 필요합니다."}
                                            """),
                                    @ExampleObject(name = "등록되지 않은 기기", value = """
                                            {"code":"DEVICE-004","message":"인증 정보를 사용할 수 없습니다."}
                                            """)
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
    ResponseEntity<CursorResponse<SighFeature<SighV2Properties>>> findAll(
            @ParameterObject @Valid SighListRequest request,
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(
            summary = "한숨 상세 조회",
            security = @SecurityRequirement(name = "bearerAuth"),
            description = """
                    ### 인증

                    - `Authorization: Bearer <access token>` 헤더가 필요합니다.
                    - 토큰에 해당하는 기기가 등록되어 있어야 합니다.

                    ### 조회 결과

                    - 경로의 `id`로 삭제되지 않은 한숨을 조회합니다.
                    - 등록 시 저장된 최종 표시 위치, 생성 시각, 메모와 익명 닉네임을 반환합니다.
                    - `properties.liked`는 인증된 기기의 좋아요 여부, `properties.likeCount`는 전체 좋아요 수입니다.
                    - 전체 좋아요가 없으면 `liked`는 `false`, `likeCount`는 `0`입니다.
                    - 조회할 때 위치나 닉네임을 다시 생성하지 않습니다.
                    - 메모가 없는 경우 `memo`는 `null`입니다.

                    ### 조회 기간 만료

                    - 매 요청 시 한국 시간(`Asia/Seoul`) 기준 13일 전 자정부터 조회할 수 있으며, 시작 경계는 포함합니다.
                    - 삭제되지 않았지만 기간이 지난 한숨은 `410 / SIGH-004`를 반환합니다. 없거나 삭제된 한숨은 기존 `404 / SIGH-002`를 반환합니다.
                    - 지도나 목록에서 이미 받은 별도 선택 시 이 API로 재조회해야 최신 만료 여부를 확인할 수 있습니다.
                    - 클라이언트는 `SIGH-004`를 받으면 "별의 힘이 다해서 소멸했습니다"와 같은 안내 후 해당 별을 현재 화면과 목록에서 제거합니다.
                    - 응답의 `message`가 아닌 `code`로 분기하며, 네트워크 장애나 다른 오류를 소멸로 안내하지 않습니다.
                    - 만료는 조회 불가를 뜻하며 데이터를 삭제하지 않습니다. 이미 전달된 내용을 서버가 회수하거나 열린 화면을 자동으로 닫지는 않습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "한숨 상세 조회 성공",
                    headers = @Header(name = "Cache-Control", description = "개인별 조회 응답을 캐시에 저장하지 않습니다.",
                            schema = @Schema(type = "string", example = "no-store")),
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
                    responseCode = "401",
                    description = "인증할 수 없음. `AUTH-001`은 access token이 없거나 만료된 경우로 `POST /api/v2/devices/tokens`로 갱신한 뒤 재시도합니다. `DEVICE-004`는 토큰은 유효하지만 그 기기가 서버에 없는 경우로 **갱신해도 해결되지 않으며 기기를 다시 등록해야 합니다.**",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "인증 실패", value = """
                                            {"code":"AUTH-001","message":"인증이 필요합니다."}
                                            """),
                                    @ExampleObject(name = "등록되지 않은 기기", value = """
                                            {"code":"DEVICE-004","message":"인증 정보를 사용할 수 없습니다."}
                                            """)
                            }
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
                    responseCode = "410",
                    description = "조회 기간 만료. 클라이언트는 SIGH-004를 소멸 안내로 처리하고 해당 별을 화면과 목록에서 제거합니다.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"SIGH-004","message":"한숨의 조회 기간이 지났습니다."}
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
            Long id,
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(
            summary = "메모와 익명 닉네임을 포함한 한숨 등록",
            description = """
                    ### 인증

                    - `Authorization: Bearer <access token>` 헤더가 필요합니다.
                    - 신규 등록과 재요청 모두 토큰에 해당하는 기기가 등록되어 있어야 합니다.
                    - 인증한 기기를 이 한숨의 작성자로 저장합니다. 응답에는 작성자 정보를 담지 않습니다.

                    ### 메모

                    - `memo`는 생략할 수 있습니다.
                    - 앞뒤 공백은 제거하며 빈 문자열과 공백만 있는 값은 `null`로 저장합니다.
                    - 정규화된 메모는 최대 50자입니다.

                    ### 익명 닉네임

                    - 닉네임은 클라이언트가 전달하지 않고 서버가 최초 등록 시 생성합니다.
                    - 닉네임 중복은 허용합니다.

                    ### 중복 요청

                    - 한 번의 등록 시도마다 새로운 `requestId`를 사용합니다.
                    - 같은 `requestId`로 재시도하면 좌표와 메모가 달라도 최초 등록 내용과 위치를 반환합니다.
                    - 같은 `requestId`를 다른 기기가 보내도 최초 등록 결과를 반환하며, 작성자는 최초 등록한 기기로 유지됩니다.
                    - 신규 생성의 `properties.liked`는 `false`, `properties.likeCount`는 `0`입니다.
                    - 재시도 응답은 요청한 기기의 현재 좋아요 여부와 전체 좋아요 수를 반환합니다.
                    - 삭제된 한숨도 같은 `requestId`로 재시도하면 기존 등록 결과를 반환합니다.

                    ### 위치

                    - `latitude`와 `longitude`에는 실제 위치 주변 반경 300m 원 내부에서 클라이언트가 면적 균등하게 고른 근사 좌표를 WGS84로 전달합니다.
                    - 300m는 EPSG:5179 평면 거리 기준입니다. 격자 중심으로 정렬하거나 실제 위치를 전송하지 않습니다.
                    - 서버는 받은 근사 좌표 주변 반경 300m 원 내부에서 독립적으로 면적 균등 추첨한 최종 위치를 저장합니다.
                    - 클라이언트와 서버 이동을 합하면 실제 위치 기준 최대 600m까지 이동할 수 있습니다. 지표면 거리에는 투영 오차가 있습니다.
                    - 같은 등록의 재시도에는 같은 `requestId`와 이미 만든 근사 좌표를 재사용하고, 조회에서는 저장된 최종 위치를 그대로 사용합니다.
                    - 서버는 받은 좌표만으로 클라이언트의 무작위화 여부를 판별할 수 없습니다. 기존 격자 입력 앱은 새 계약으로 전환해야 합니다.
                    - 응답 좌표는 `[longitude, latitude]` 순서입니다.
                    - 최초 등록 성공 응답은 생성된 한숨의 상세 URI를 `Location` 헤더로 제공합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
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
                    responseCode = "401",
                    description = "인증할 수 없음. `AUTH-001`은 access token이 없거나 만료된 경우로 `POST /api/v2/devices/tokens`로 갱신한 뒤 재시도합니다. `DEVICE-004`는 토큰은 유효하지만 그 기기가 서버에 없는 경우로 **갱신해도 해결되지 않으며 기기를 다시 등록해야 합니다.**",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "인증 실패", value = """
                                            {"code":"AUTH-001","message":"인증이 필요합니다."}
                                            """),
                                    @ExampleObject(name = "등록되지 않은 기기", value = """
                                            {"code":"DEVICE-004","message":"인증 정보를 사용할 수 없습니다."}
                                            """)
                            }
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
            SighCreateV2Request request,
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(summary = "한숨 좋아요 상태 변경", description = """
            `Authorization: Bearer <access token>` 헤더가 필요하며, 인증된 기기의 좋아요만 변경합니다.

            `liked: true`는 좋아요 추가, `liked: false`는 취소입니다. 이미 원하는 상태라면 그대로 유지합니다.
            네트워크 재시도에는 같은 `liked` 값을 보내며, `requestId`와 기기 식별자는 보내지 않습니다.
            존재하지 않거나 삭제된 한숨은 추가와 취소 모두 404를 반환합니다.

            응답에는 처리 후 내 좋아요 여부와 전체 좋아요 수가 담깁니다.
            다른 기기의 이후 변경에 따라 다음 조회의 좋아요 수는 달라질 수 있습니다.
            """, security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요청한 상태 반영 또는 기존 상태 유지",
                    content = @Content(schema = @Schema(implementation = SighLikeResponse.class))),
            @ApiResponse(responseCode = "400", description = "한숨 ID가 1 미만이거나 liked가 누락 또는 유효하지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401",
                    description = "인증할 수 없음. `AUTH-001`은 access token이 없거나 만료된 경우로 "
                            + "`POST /api/v2/devices/tokens`로 갱신한 뒤 재시도합니다. "
                            + "`DEVICE-004`는 토큰은 유효하지만 그 기기가 서버에 없는 경우로 "
                            + "**갱신해도 해결되지 않으며 기기를 다시 등록해야 합니다.**",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "한숨이 없거나 삭제됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "동시 변경 충돌이 해소되지 않거나 처리 중 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    SighLikeResponse update(
            @Parameter(description = "좋아요 상태를 변경할 한숨 ID", example = "42", schema = @Schema(minimum = "1"))
            @Min(value = 1, message = "한숨 ID는 1 이상이어야 합니다.") Long sighId,
            @Parameter(hidden = true) UUID devicePublicId,
            @RequestBody(required = true, content = @Content(examples = {
                    @ExampleObject(name = "좋아요 추가", value = "{\"liked\":true}"),
                    @ExampleObject(name = "좋아요 취소", value = "{\"liked\":false}")
            }))
            @Valid SighLikeRequest request
    );
}
