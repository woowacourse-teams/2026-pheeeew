package com.pheeeew.emotion.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.emotion.presentation.dto.EmotionPressDailyResponse;
import com.pheeeew.emotion.presentation.dto.EmotionPressRequest;
import com.pheeeew.emotion.presentation.dto.EmotionPressResponse;
import com.pheeeew.emotion.presentation.dto.EmotionPressTotalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.MediaType;

@Tag(name = "감정 프레스", description = "현재 위치의 읍면동 단위 개인 감정 버튼 API")
public interface EmotionPressControllerApi {

    @Operation(
            summary = "위치 기반 감정 버튼 누르기",
            description = """
                    현재 위치의 읍면동에 감정 버튼을 누릅니다. 이 집계는 **기기별**이고,
                    같은 기록이 **내가 속한 모든 그룹의 `GET /api/v3/groups/{groupId}` 응답에서
                    `weeklyEmotionPressCount` 와 `weeklyEmotionPressRank` 에 더해집니다.**

                    - `longitude`, `latitude`, `counts` 를 모두 보냅니다. 요청 형식은 하나뿐이고
                      단일 감정(`state`)만 보내는 형식은 받지 않습니다.
                    - 좌표는 **읍면동을 고르는 데에만** 쓰고 **서버에 저장하지 않습니다.** 응답의 `regionCode`
                      외에 좌표가 남지 않으며 지도에 핀도 찍히지 않습니다.
                    - 읍면동은 SGIS 2025년 2분기 경계로 판정합니다. 경계에 포함되면 그 읍면동이고,
                      포함되지 않으면 경계에서 **1km 이내의 가장 가까운** 읍면동에 배정합니다.
                    - 배정할 읍면동이 없으면 `EMOTION-014` 와 400 입니다. 한 건도 집계하지 않습니다.
                    - 서버의 지역 분류 준비가 완료되지 않으면 `EMOTION-013` 과 503 입니다.
                    - `counts` 는 감정별 누른 횟수입니다. 예: `{"ANGRY":9,"EXHAUSTED":3}`.
                      **일정 주기로 모아 보내는 쪽을 권장합니다.** 버튼마다 요청을 보내지 않습니다.
                    - 값이 `0` 인 감정은 **누르지 않은 것으로 보고 넘깁니다.** 모든 값이 `0` 이거나 빈 객체여도
                      400 이 아니라 **오늘 집계만 돌려줍니다.** 값이 음수이거나 `null` 이면 400 입니다.
                    - 상한을 넘겨도 **거절하지 않고 넘친 만큼만 버립니다.** 감정 하나당 `30`,
                      요청 전체 합 `100` 까지 반영하며 전체 합이 넘치면 감정 이름 오름차순으로 채웁니다.
                    - 누른 뒤 **이 기기가 그 읍면동에서 오늘 누른 집계**를 바로 돌려줍니다.
                      지역 전체의 합이 아니라 내 기기의 집계입니다. 누르지 않은 감정도 `0` 으로 내려와
                      다섯 감정이 항상 모두 있습니다.
                    - **이 응답의 `counts` 는 `GET /api/v2/emotions/presses/me` 와 다른 값입니다.**
                      이쪽은 **그 읍면동 한정**이고, 그쪽은 **전 지역 합**입니다. 오늘 여러 읍면동에서
                      눌렀다면 그쪽 값이 더 큽니다. 화면에 "오늘 내가 누른 수" 를 보여줄 때는 그쪽을 씁니다.
                    - 하루 경계는 **KST 00:00** 입니다. 날이 바뀌면 집계가 다시 `0` 부터 쌓입니다.
                    - 취소는 없습니다. 지도에 핀이 찍히지 않으므로 그룹 **스탬프** 점수와 순위에는 영향이 없고,
                      `GET /api/v3/groups/{groupId}` 의 `weeklyEmotionPressCount`,
                      `weeklyEmotionPressRank` 에만 더해집니다.
                      그룹 화면 감정 버튼이 쌓는 `GET /api/v2/groups/{groupId}` 의 `weeklyPressRank` 와
                      `GET /api/v2/groups/press-rankings` 에는 더해지지 않습니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "이 기기의 해당 읍면동 오늘 집계"),
            @ApiResponse(responseCode = "400",
                    description = "좌표·counts 가 올바르지 않거나 배정할 읍면동이 없음(EMOTION-014)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "요청 값 오류", value = """
                                            {"code": "COMMON-001", "message": "요청 값이 올바르지 않습니다."}
                                            """),
                                    @ExampleObject(name = "서비스 범위 밖", value = """
                                            {"code": "EMOTION-014", "message": "이 위치에서는 기록할 수 없습니다. 다른 위치를 선택해 주세요."}
                                            """)
                            })),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code": "DEVICE-004", "message": "인증 정보를 사용할 수 없습니다."}
                                    """))),
            @ApiResponse(responseCode = "503", description = "서버의 지역 분류 준비가 완료되지 않음(EMOTION-013)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code": "EMOTION-013", "message": "지역 분류 자료를 사용할 수 없습니다."}
                                    """)))
    })
    EmotionPressResponse press(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = EmotionPressRequest.class),
                            examples = @ExampleObject(
                                    name = "묶음",
                                    value = """
                                            {"longitude":126.9774,"latitude":37.5669,"counts":{"ANGRY":9,"EXHAUSTED":3}}
                                            """
                            )
                    )
            )
            @Valid EmotionPressRequest request,
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(
            summary = "내 일별 감정 프레스 집계",
            description = """
                    요청한 기기가 그날 누른 감정별 횟수와 총합을 돌려줍니다.

                    - **지역을 구분하지 않고 모두 합칩니다.** 그날 어느 읍면동에서 눌렀든 더합니다.
                    - `POST /api/v2/emotions/presses` 응답의 `counts` 와 **다른 값입니다.**
                      그 응답은 **그 읍면동 한정**이고, 이 응답은 **전 지역 합**입니다.
                      여러 지역에서 눌렀다면 이 값이 더 큽니다.
                    - 누르지 않은 감정도 `0` 으로 내려와 다섯 감정이 항상 모두 있습니다.
                    - 그날 한 번도 누르지 않았으면 다섯 감정이 모두 `0` 이고 `total` 이 `0` 입니다.
                    - `regionCode` 를 담지 않습니다. 지역을 넘나든 합이라 하나로 특정할 수 없습니다.

                    `daysAgo` 로 며칠 전인지 고릅니다. `0` 이 오늘, `1` 이 어제입니다.
                    **하루 경계는 KST 00:00** 이고, 서버가 해석한 날짜를 응답의 `pressDate` 로 알려줍니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "daysAgo 값이 올바르지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code": "COMMON-001", "message": "요청 값이 올바르지 않습니다."}
                                    """))),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code": "DEVICE-004", "message": "인증 정보를 사용할 수 없습니다."}
                                    """)))
    })
    EmotionPressDailyResponse findMyDailyPresses(
            @Min(value = 0, message = "며칠 전인지는 0 이상이어야 합니다.")
            @Max(value = 365, message = "며칠 전인지는 365 이하여야 합니다.")
            int daysAgo,

            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(
            summary = "전체 사용자 일별 감정 프레스 총합",
            description = """
                    그날 전체 사용자가 누른 감정 프레스의 총합을 돌려줍니다.

                    - **총합 하나만 내려줍니다.** 감정별로 나누지 않습니다.
                    - 기기와 지역을 구분하지 않습니다.
                    - 그날 아무도 누르지 않았으면 `0` 입니다.

                    `daysAgo` 규칙과 `pressDate` 는 `GET /api/v2/emotions/presses/me` 와 같습니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "daysAgo 값이 올바르지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code": "COMMON-001", "message": "요청 값이 올바르지 않습니다."}
                                    """))),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code": "DEVICE-004", "message": "인증 정보를 사용할 수 없습니다."}
                                    """)))
    })
    EmotionPressTotalResponse findDailyTotal(
            @Min(value = 0, message = "며칠 전인지는 0 이상이어야 합니다.")
            @Max(value = 365, message = "며칠 전인지는 365 이하여야 합니다.")
            int daysAgo
    );
}
