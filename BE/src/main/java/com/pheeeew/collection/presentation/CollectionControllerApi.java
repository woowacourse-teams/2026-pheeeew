package com.pheeeew.collection.presentation;

import com.pheeeew.collection.presentation.dto.CollectionTodayResponse;
import com.pheeeew.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "컬렉션", description = "인증된 기기의 컬렉션 진행도 API")
public interface CollectionControllerApi {

    @Operation(
            summary = "오늘 컬렉션 진행도 조회",
            description = """
                    인증된 기기의 한국 시간 기준 오늘 진행도를 반환합니다. 날짜와 기기는 서버가 결정합니다.

                    - 다섯 감정을 FRUSTRATED, IRRITATED, EXHAUSTED, DISCOURAGED, ANGRY 순서로 반환합니다.
                    - 활동이 없으면 횟수는 0입니다. 스탬프 목표는 1회, 프레스 목표는 100회입니다.
                    - 프레스는 서버가 인정하여 저장한 횟수이며, 목표를 초과해도 실제 횟수를 유지합니다.
                    - 부족분은 목표에서 현재 횟수를 뺀 값이며 최소 0입니다.
                    - card는 발급된 카드 정보이며 미발급 상태이면 null입니다. 조회로 활동이나 카드를 생성하지 않습니다.

                    조회 중 날짜가 바뀌면 새 날짜로 다시 조회합니다. 날짜가 계속 바뀌어 503이 반환되면 재시도합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 정보 없음·유효하지 않음 또는 인증된 기기가 존재하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "COLLECTION-001: 조회 중 날짜 변경이 반복됨. 재시도 필요",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CollectionTodayResponse> findToday(
            @Parameter(hidden = true) UUID devicePublicId
    );
}
