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
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "실시간 프레스", description = "실시간 프레스 화면 상태 조회 API")
public interface PressStreamControllerApi {

    @Operation(
            summary = "실시간 프레스 화면 상태 구독 (SSE)",
            description = """
                    `text/event-stream` 으로 실시간 프레스 화면 상태를 받습니다.
                    폴링(`GET /api/v1/press-race/snapshot`, `GET /api/v2/press-race/snapshot`)과 함께 제공되며
                    클라이언트가 하나를 골라 연동합니다.

                    ### 인증과 요청

                    - `Authorization: Bearer <access token>` 헤더로 인증합니다. 토큰을 쿼리에 넣지 않습니다.
                    - `view`(생략 시 `PRESS`), 응답 필드와 의미, 순위 규칙은 폴링과 같습니다.
                    - 화면을 바꾸려면 기존 구독을 닫고 새 `view` 로 다시 구독합니다.

                    ### 이벤트

                    - `snapshot`: 데이터는 폴링 응답과 같은 JSON 입니다(아래 200 응답 스키마).
                      구독 직후 현재 상태를 한 번 보내고, 이후에는 내 화면 상태가 바뀔 때만 보냅니다.
                      `generatedAt` 만 바뀐 경우는 보내지 않습니다. 날짜 전환은 화면 변경입니다.
                    - heartbeat: 15초마다 `:heartbeat` 주석 줄을 보냅니다. 연결 유지용이며 무시합니다.
                    - 종료 이유 이벤트는 없습니다. 토큰 만료, 서버 종료(배포), 네트워크 종료 모두 연결이 닫히고, 앱은 다시 구독합니다.

                    ### 갱신 주기와 지연

                    - 서버가 1초마다(실험 초기값) 공통 순위를 한 번 만들고 구독마다 화면을 계산합니다.
                    - 다른 사람의 입력이 보이기까지 발행 주기와 그 주기의 처리 시간만큼 늦을 수 있습니다.
                    - 최초 `snapshot` 은 최대 1초 전에 만든 공통 순위일 수 있습니다. `generatedAt` 은 그 관찰 시각입니다.

                    ### 연결 유지 시간

                    - 구독에 쓴 access token 의 남은 수명이 지나면 서버가 연결을 닫습니다.
                      앱은 토큰을 갱신한 뒤 새 헤더로 다시 구독하고, 새 구독은 최신 상태부터 받습니다.

                    ### 동시 구독

                    - 같은 기기는 서버 한 대에서 최대 2개까지 동시에 구독할 수 있습니다.
                    - 넘으면 기존 연결은 유지하고 새 요청을 `429 PRESS_STREAM-001` 로 거절합니다.
                      즉시 반복하지 말고 0.5초부터 최대 5초까지 대기 시간을 늘리며 무작위 지연을 섞어 다시 시도합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "구독 시작. `snapshot` 이벤트 데이터의 형식",
                    content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                            schema = @Schema(implementation = PressSnapshotResponse.class))),
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
                                    """))),
            @ApiResponse(responseCode = "429", description = "같은 기기의 동시 구독 수 초과",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code": "PRESS_STREAM-001", "message": "같은 기기의 실시간 프레스 구독 수를 넘었습니다."}
                                    """)))
    })
    ResponseEntity<SseEmitter> subscribe(
            @Parameter(description = "화면. 생략하면 PRESS 입니다.", example = "PRESS")
            PressView view,

            @Parameter(hidden = true) UUID devicePublicId,

            @Parameter(hidden = true) Jwt jwt
    );
}
