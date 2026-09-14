package com.pheeeew.report.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.common.presentation.dto.CursorResponse;
import com.pheeeew.report.presentation.dto.BlockCreateRequest;
import com.pheeeew.report.presentation.dto.DeviceBlockResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "사용자 차단", description = "한 사용자가 올린 한숨 전부를 내 지도와 목록에서 가리는 API")
public interface DeviceBlockControllerApi {

    @Operation(
            summary = "사용자 차단",
            description = """
                    ### 인증

                    - `Authorization: Bearer <access token>` 헤더가 필요합니다.
                    - 인증한 기기가 차단자가 됩니다. 요청 본문에 기기 식별자를 담지 않습니다.

                    ### 차단 대상 지정

                    - `sighId`에는 차단할 사용자가 올린 한숨의 ID를 담습니다. 서버가 그 한숨의 작성자를 찾아 차단합니다.
                    - 클라이언트는 작성자 식별자를 알 필요가 없고, 응답에도 담기지 않습니다.

                    ### 차단 범위

                    - 그 사용자가 올린 한숨이 지도 조회와 목록 조회에서 전부 사라집니다. 차단한 뒤에 올라오는 한숨도 사라집니다.
                    - 차단은 차단한 기기에서만 적용됩니다. 다른 사용자의 화면은 바뀌지 않습니다.
                    - 조회 요청에 `Authorization` 헤더를 함께 보내야 차단이 적용됩니다.

                    ### 차단할 수 없는 경우

                    - 자기 자신은 차단할 수 없습니다. 409 `BLOCK-002`를 반환합니다.
                    - 작성자를 알 수 없는 한숨이 있습니다. 이전 버전에서 등록했거나 `POST /api/v1/sighs`로 등록한 한숨에는 작성자 정보가 없습니다. 이때는 409 `BLOCK-003`을 반환합니다. 그 한숨은 `POST /api/v2/blocks/sighs`로 개별 차단할 수 있습니다.
                    - 존재하지 않는 한숨이면 404를 반환합니다. 삭제된 한숨의 작성자는 차단할 수 있습니다.

                    ### 중복 차단

                    - 같은 사용자를 한 번만 차단합니다. 이미 차단한 사용자를 다시 차단하면 새로 저장하지 않고 최초 차단을 200으로 반환합니다.
                    - 이때 응답의 `sighId`, `nickname`, `memo`는 요청에 담은 한숨이 아니라 최초 차단의 근거가 된 한숨입니다.

                    ### 한숨 차단과의 관계

                    - 두 차단은 독립적입니다. 같은 한숨을 개별 차단하고 그 작성자도 차단하면 두 기록이 따로 남습니다.
                    - 사용자 차단을 해제해도 개별 차단한 한숨은 계속 가려집니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "사용자 차단 최초 등록 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DeviceBlockResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "200",
                    description = "이미 차단한 사용자라 최초 차단을 반환",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DeviceBlockResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "한숨 식별자가 올바르지 않음",
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
                    description = "access token 이 없거나 사용할 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"AUTH-001","message":"인증이 필요합니다."}
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "차단 대상 한숨이 존재하지 않음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"SIGH-002","message":"한숨을 찾을 수 없습니다."}
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "자기 자신을 차단하려 했거나 작성자를 알 수 없는 한숨임",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "자기 자신 차단",
                                            value = """
                                                    {"code":"BLOCK-002","message":"자기 자신은 차단할 수 없습니다."}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "작성자를 알 수 없는 한숨",
                                            value = """
                                                    {"code":"BLOCK-003","message":"작성자를 알 수 없는 한숨은 사용자 차단을 할 수 없습니다."}
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "차단을 저장하지 못했거나 처리하지 못한 서버 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<DeviceBlockResponse> save(
            @Parameter(hidden = true) UUID devicePublicId,
            BlockCreateRequest request
    );

    @Operation(
            summary = "내가 차단한 사용자 목록 조회",
            description = """
                    ### 인증

                    - `Authorization: Bearer <access token>` 헤더가 필요합니다. 인증한 기기의 차단 목록만 반환합니다.

                    ### 항목

                    - 사용자는 익명이므로 차단 대상을 식별하는 값을 반환하지 않습니다. 대신 차단할 때 근거가 된 한숨을 함께 반환해 어떤 사용자를 차단했는지 알아볼 수 있게 합니다.
                    - `sighId`, `nickname`, `memo`는 그 근거 한숨의 값입니다. 그 사용자의 다른 한숨이나 최근 활동이 아닙니다.
                    - `createdAt`은 차단한 시각입니다.
                    - 해제에는 `blockId`를 사용합니다. `sighId`가 아닙니다.

                    ### 페이지

                    - 첫 페이지는 `cursor`를 전달하지 않고, 다음 페이지는 직전 응답의 `nextCursor`를 그대로 전달합니다.
                    - 최신 차단순으로 페이지당 50건을 반환합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "차단한 사용자 목록 조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = """
                                    {
                                      "items": [
                                        {
                                          "blockId": 7,
                                          "sighId": 42,
                                          "nickname": "날아가는 고라니",
                                          "memo": "오늘은 조금 지쳤다",
                                          "createdAt": "2026-09-14T02:44:00Z"
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
                    description = "커서를 사용할 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"BLOCK-004","message":"차단 목록 커서를 사용할 수 없습니다."}
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "access token 이 없거나 사용할 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"AUTH-001","message":"인증이 필요합니다."}
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "차단 목록을 조회하지 못한 서버 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    CursorResponse<DeviceBlockResponse> findAll(
            @Parameter(hidden = true) UUID devicePublicId,

            @Parameter(
                    description = "직전 응답이 발급한 커서입니다. 첫 페이지는 전달하지 않습니다.",
                    example = "MXwxMDA"
            )
            String cursor
    );

    @Operation(
            summary = "사용자 차단 해제",
            description = """
                    - 경로의 `blockId`는 `GET /api/v2/blocks/devices` 응답의 `blockId`입니다. 한숨 ID가 아닙니다.
                    - 내 차단이 아니거나 존재하지 않는 `blockId`를 보내도 204를 반환합니다. 같은 요청을 여러 번 보내도 결과가 같습니다.
                    - 그 사용자의 한숨을 개별 차단해 두었다면, 이 해제만으로는 다시 보이지 않습니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "사용자 차단 해제 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "차단 식별자 형식이 올바르지 않음",
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
                    description = "access token 이 없거나 사용할 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"AUTH-001","message":"인증이 필요합니다."}
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "차단을 해제하지 못한 서버 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<Void> delete(
            @Parameter(hidden = true) UUID devicePublicId,

            @Parameter(
                    description = "해제할 사용자 차단 ID. 한숨 ID가 아닙니다.",
                    example = "7",
                    schema = @Schema(minimum = "1")
            )
            Long blockId
    );
}
