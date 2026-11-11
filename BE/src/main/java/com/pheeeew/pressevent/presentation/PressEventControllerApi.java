package com.pheeeew.pressevent.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.pressevent.presentation.dto.PressEventAttemptRequest;
import com.pheeeew.pressevent.presentation.dto.PressEventAttemptResponse;
import com.pheeeew.pressevent.presentation.dto.PressEventRankingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;

@Tag(name = "프레스 이벤트", description = "20초 프레스 이벤트 결과 제출과 순위 조회")
public interface PressEventControllerApi {

    @Operation(summary = "프레스 이벤트 결과 제출", security = @SecurityRequirement(name = "bearerAuth"), description = """
            다섯 감정의 횟수를 합산하여 이번 도전의 점수를 계산하고 일반 프레스 집계에도 반영합니다.
            이벤트 중 입력한 프레스는 이 API에만 전송합니다.

            네트워크 재시도에는 같은 requestId와 횟수를 사용합니다.
            성공한 도전의 재시도에는 최초 결과와 replayed=true를 반환합니다.
            같은 requestId로 감정별 횟수를 바꾸면 409를 반환합니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "도전 점수, 본인의 최고 점수와 순위"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 식별자, 형식 또는 횟수",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "기기 인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "이벤트를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이벤트 제출 기간 외 요청 또는 기존 도전과 다른 횟수",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "기기별 최근 20초 이벤트 프레스 한도 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "프레스 저장 일시 중단",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    PressEventAttemptResponse submit(
            @Parameter(description = "프레스 이벤트 UUID") UUID eventId,
            @Valid PressEventAttemptRequest request,
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(summary = "프레스 이벤트 순위 조회", security = @SecurityRequirement(name = "bearerAuth"), description = """
            현재 1위 카드, 참가자 순위 페이지, 내 최고 점수와 순위를 반환합니다.
            공동 순위는 점수만으로 계산하고 동점 목록은 최고 점수 달성 시각과 공개 UUID 순으로 정렬합니다.
            다음 페이지는 응답의 nextCursor로 요청합니다. 종료된 이벤트의 순위도 조회할 수 있습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "1위 카드, 순위 페이지와 본인 순위"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 식별자, 커서 또는 페이지 크기",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "기기 인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "이벤트를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    PressEventRankingResponse findRanking(
            @Parameter(description = "프레스 이벤트 UUID") UUID eventId,
            @Parameter(description = "이 이벤트 순위에서 받은 nextCursor. 첫 페이지에서는 생략합니다.") String cursor,
            @Min(1) @Max(100) @Parameter(description = "페이지 크기 1~100. 기본값은 20입니다.") int limit,
            @Parameter(hidden = true) UUID devicePublicId
    );
}
