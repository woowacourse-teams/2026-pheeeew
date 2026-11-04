package com.pheeeew.pressrace.v2.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.pressrace.v2.domain.PressView;
import com.pheeeew.pressrace.v2.presentation.dto.PressSnapshotResponse;
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

@Tag(name = "실시간 프레스", description = "실시간 프레스 화면 상태 조회 API")
public interface PressSnapshotControllerApi {

    @Operation(
            summary = "실시간 프레스 화면 상태 조회",
            description = """
                    실시간 프레스 화면의 현재 순위와 최근 활동 인원을 돌려줍니다.
                    클라이언트가 화면을 보는 동안 **주기적으로 호출**해 최신 상태로 덮어씁니다.

                    ### 입력과의 관계

                    - 프레스 입력은 `POST /api/v2/emotions/presses` 를 그대로 씁니다. 이 API 는 저장하지 않습니다.
                    - 내 감정별 개수는 그 응답의 `counts` 로 확인합니다.
                    - 누른 결과의 순위는 이 API 로 확인합니다. 누른다고 순위가 반드시 오르지는 않습니다.

                    ### 순위

                    - 오늘(KST) 점수가 양수인 기기만 참가자입니다.
                    - 표시 순서는 점수 내림차순, 같은 점수는 `publicId` 오름차순입니다.
                    - `rank` 는 점수만으로 정한 공동 순위입니다. 예: 1, 2, 2, 4.
                    - `view` 가 `PRESS` 이면 `top100` 은 빈 목록이고, `ALL` 이면 첫 100명을 담습니다.
                      공동 100위를 넘겨 늘리지 않습니다.

                    ### 활동 인원

                    - `activeCount` 는 최근 5초 안에 프레스가 반영된 **서로 다른 기기 수**입니다. 접속자 수가 아닙니다.
                    - 기기 단위이므로 실제 사람 수와 다를 수 있습니다. 한 기기가 여러 감정을 눌러도 1로 셉니다.
                    - 새 프레스가 없으면 시간만 지나도 줄어듭니다.

                    ### 응답 시점

                    - 요청마다 DB 의 현재 상태를 읽습니다. `generatedAt` 은 그 관찰 시각입니다.
                    - 이전 응답과 내용이 같아도 같은 형식으로 내려옵니다.
                    - WebSocket(`/api/v1/press-race`)의 `snapshotVersion`, `baseline`, `overtakes` 는 없습니다.
                    - WebSocket 경쟁의 ON/OFF 와 무관하게 항상 오늘 상태를 돌려줍니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "view 값이 올바르지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code": "COMMON-001", "message": "요청 값이 올바르지 않습니다."}
                                    """))),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code": "AUTH-001", "message": "인증이 필요합니다."}
                                    """)))
    })
    PressSnapshotResponse findSnapshot(
            @Parameter(description = "화면. 생략하면 PRESS 입니다.", example = "PRESS")
            PressView view,

            @Parameter(hidden = true) UUID devicePublicId
    );
}
