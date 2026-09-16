package com.pheeeew.report.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.common.presentation.dto.CursorResponse;
import com.pheeeew.report.presentation.dto.BlockCreateRequest;
import com.pheeeew.report.presentation.dto.SighBlockResponse;
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

@Tag(name = "한숨 차단", description = "한숨 하나를 내 지도와 목록에서 가리는 API")
public interface SighBlockControllerApi {

    @Operation(
            summary = "한숨 차단",
            description = """
                    ### 인증

                    - `Authorization: Bearer <access token>` 헤더가 필요합니다.
                    - 인증한 기기가 차단자가 됩니다. 요청 본문에 기기 식별자를 담지 않습니다.

                    ### 차단 범위

                    - 차단은 차단한 기기에서만 적용됩니다. 다른 사용자의 화면은 바뀌지 않습니다.
                    - 차단한 한숨은 지도 조회와 목록 조회에서 사라집니다.
                    - 조회 요청에 `Authorization` 헤더를 함께 보내야 차단이 적용됩니다. 헤더가 없으면 차단 전과 같은 결과를 받습니다.
                    - 신고와는 독립적입니다. 차단해도 서버에 신고가 접수되지 않습니다.

                    ### 차단 대상

                    - `sighId`에는 가릴 한숨의 ID를 담습니다. 1 이상이어야 합니다.
                    - 존재하지 않는 한숨이면 404를 반환합니다.
                    - 삭제된 한숨도 차단할 수 있습니다.
                    - 자기가 등록한 한숨도 차단할 수 있습니다.

                    ### 중복 차단

                    - 같은 기기는 같은 한숨을 한 번만 차단합니다.
                    - 이미 차단한 한숨을 다시 차단하면 새로 저장하지 않고 최초 차단을 200으로 반환합니다.

                    ### 앱 재설치

                    - 차단 목록은 기기 자격증명에 묶여 있습니다. 앱을 삭제하고 다시 설치하면 새 기기가 되어 차단 목록을 이어받을 수 없습니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "한숨 차단 최초 등록 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SighBlockResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "200",
                    description = "이미 차단한 한숨이라 최초 차단을 반환",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SighBlockResponse.class)
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
                    description = "인증할 수 없음. `AUTH-001`은 access token이 없거나 만료된 경우로 `POST /api/v2/devices/tokens`로 갱신한 뒤 재시도합니다. `DEVICE-004`는 토큰은 유효하지만 그 기기가 서버에 없는 경우로 **갱신해도 해결되지 않으며 기기를 다시 등록해야 합니다.**",
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
                    responseCode = "500",
                    description = "차단을 저장하지 못했거나 처리하지 못한 서버 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<SighBlockResponse> save(
            @Parameter(hidden = true) UUID devicePublicId,
            BlockCreateRequest request
    );

    @Operation(
            summary = "내가 차단한 한숨 목록 조회",
            description = """
                    ### 인증

                    - `Authorization: Bearer <access token>` 헤더가 필요합니다. 인증한 기기의 차단 목록만 반환합니다.

                    ### 페이지

                    - 첫 페이지는 `cursor`를 전달하지 않습니다.
                    - 다음 페이지는 직전 응답의 `nextCursor`를 수정하지 않고 그대로 전달합니다.
                    - 최신 차단순(차단한 시각의 역순)으로 페이지당 50건을 반환합니다.

                    ### 항목

                    - `createdAt`은 차단한 시각입니다. 한숨을 등록한 시각이 아닙니다.
                    - `nickname`과 `memo`는 차단한 한숨의 값입니다. 작성자를 식별하는 값이 아닙니다.
                    - 삭제된 한숨도 목록에 남습니다.
                    - `sighId`를 그대로 `DELETE /api/v2/blocks/sighs/{sighId}` 에 사용합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "차단한 한숨 목록 조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = """
                                    {
                                      "items": [
                                        {
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
                    description = "인증할 수 없음. `AUTH-001`은 access token이 없거나 만료된 경우로 `POST /api/v2/devices/tokens`로 갱신한 뒤 재시도합니다. `DEVICE-004`는 토큰은 유효하지만 그 기기가 서버에 없는 경우로 **갱신해도 해결되지 않으며 기기를 다시 등록해야 합니다.**",
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
    CursorResponse<SighBlockResponse> findAll(
            @Parameter(hidden = true) UUID devicePublicId,

            @Parameter(
                    description = "직전 응답이 발급한 커서입니다. 첫 페이지는 전달하지 않습니다.",
                    example = "MXwxMDA"
            )
            String cursor
    );

    @Operation(
            summary = "한숨 차단 해제",
            description = """
                    - 경로의 `sighId`에 대한 내 차단만 해제합니다. 다른 기기의 차단은 남습니다.
                    - 차단하지 않은 한숨을 해제해도 204를 반환합니다. 같은 요청을 여러 번 보내도 결과가 같습니다.
                    - 그 한숨의 작성자를 사용자 차단으로도 차단해 두었다면, 이 해제만으로는 다시 보이지 않습니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "한숨 차단 해제 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "한숨 식별자 형식이 올바르지 않음",
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
                    description = "차단을 해제할 한숨 ID",
                    example = "42",
                    schema = @Schema(minimum = "1")
            )
            Long sighId
    );
}
