package com.pheeeew.report.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.report.presentation.dto.SighReportCreateRequest;
import com.pheeeew.report.presentation.dto.SighReportResponse;
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
import org.springframework.http.ResponseEntity;

@Tag(name = "신고", description = "한숨 신고 API")
public interface SighReportControllerApi {

    @Operation(
            summary = "한숨 신고",
            description = """
                    ### 인증

                    - `Authorization: Bearer <access token>` 헤더가 필요합니다.
                    - 인증한 기기가 신고자가 됩니다. 요청 본문에 기기 식별자를 담지 않습니다.

                    ### 신고 대상

                    - `sighId`에는 신고할 한숨의 ID를 담습니다. 1 이상의 값이어야 합니다.
                    - 존재하지 않는 한숨이면 404를 반환합니다.
                    - 삭제된 한숨도 신고할 수 있습니다. 신고 기록은 한숨 삭제와 무관하게 남습니다.

                    ### 중복 신고

                    - 같은 기기는 같은 한숨을 한 번만 신고할 수 있습니다.
                    - 이미 신고한 한숨을 다시 신고하면 새로 저장하지 않고 최초 신고를 그대로 반환합니다.
                    - 이때 응답의 `reason`은 요청에 담긴 값이 아니라 최초 신고에 저장된 사유입니다.

                    ### 신고 사유

                    - `reason`은 정해진 항목 없이 자유롭게 입력합니다.
                    - 길이 제한 200자는 보낸 값 그대로를 기준으로 검사합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "신고 최초 등록 성공",
                    content = @Content(schema = @Schema(implementation = SighReportResponse.class))
            ),
            @ApiResponse(
                    responseCode = "200",
                    description = "이미 신고한 한숨이라 최초 신고를 반환",
                    content = @Content(schema = @Schema(implementation = SighReportResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "한숨 식별자나 신고 사유가 올바르지 않음",
                    content = @Content(
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
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"AUTH-001","message":"인증이 필요합니다."}
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "신고 대상 한숨이 존재하지 않음",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"SIGH-002","message":"한숨을 찾을 수 없습니다."}
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "신고를 저장하지 못했거나 처리하지 못한 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<SighReportResponse> save(
            @Parameter(hidden = true) UUID devicePublicId,
            SighReportCreateRequest request
    );
}
