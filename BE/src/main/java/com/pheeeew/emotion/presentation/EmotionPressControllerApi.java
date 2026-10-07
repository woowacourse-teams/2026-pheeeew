package com.pheeeew.emotion.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.emotion.presentation.dto.EmotionPressRequest;
import com.pheeeew.emotion.presentation.dto.EmotionPressResponse;
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
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증할 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "서버의 지역 분류 준비가 완료되지 않음(EMOTION-013)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
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
}
