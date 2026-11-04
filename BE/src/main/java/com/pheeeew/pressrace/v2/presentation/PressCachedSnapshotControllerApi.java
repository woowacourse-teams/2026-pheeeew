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
public interface PressCachedSnapshotControllerApi {

    @Operation(
            summary = "실시간 프레스 화면 상태 조회 (캐시 적용)",
            description = """
                    `GET /api/v1/press-race/snapshot`(기본 폴링)과 같은 화면 상태를 돌려주되,
                    서버가 만든 **공통 순위 스냅샷을 유효시간 동안 재사용**합니다.
                    두 API 는 함께 제공되며 클라이언트가 하나를 골라 주기적으로 호출합니다.

                    ### 기본 폴링과 같은 것

                    - 인증, `view`(생략 시 `PRESS`), 응답 필드와 의미가 같습니다.
                    - 순위는 점수 내림차순, 같은 점수는 `publicId` 오름차순이고 `rank` 는 점수만으로 정합니다(1, 2, 2, 4).
                    - `me`, `nearby` 는 요청한 기기 기준으로 요청마다 계산합니다. 다른 기기의 화면이 섞이지 않습니다.
                    - 입력은 `POST /api/v2/emotions/presses` 를 그대로 씁니다.

                    ### 기본 폴링과 다른 것

                    - 유효시간 안의 요청은 DB 를 다시 읽지 않고 같은 스냅샷으로 응답합니다. 기본 유효시간은 1초입니다(실험 후보).
                    - `generatedAt` 은 그 스냅샷을 만들 때 DB 를 관찰한 시각입니다. 요청 시각보다 최대 유효시간(과 스냅샷을 만드는 데 걸린 시간)만큼 이를 수 있습니다.
                    - `activeCount` 는 `generatedAt` 기준 최근 5초 안에 프레스가 반영된 서로 다른 기기 수입니다.
                      새 프레스가 없을 때 줄어드는 시점이 최대 유효시간만큼 늦을 수 있습니다.
                    - 다른 사람의 입력이 순위에 보이기까지 기본 폴링보다 최대 유효시간만큼 늦을 수 있습니다.
                    - KST 날짜는 서버 애플리케이션 시계로 판정합니다. 날짜가 바뀌면 전날 스냅샷을 쓰지 않고 새 날짜로 다시 만듭니다.
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
