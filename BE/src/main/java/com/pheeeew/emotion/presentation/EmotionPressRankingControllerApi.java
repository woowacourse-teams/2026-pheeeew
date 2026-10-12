package com.pheeeew.emotion.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.presentation.dto.EmotionPressRankingResponse;
import com.pheeeew.emotion.presentation.dto.EmotionStatePressRankingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.MediaType;

@Tag(name = "감정 프레스 랭킹", description = "기기별 개인 감정 프레스의 전국 주간 순위를 봅니다.")
public interface EmotionPressRankingControllerApi {

    @Operation(
            summary = "개인 전국 주간 프레스 랭킹",
            description = """
                    기기별 개인 감정 프레스를 **전국 기준**으로 주 단위 합산해 순위를 돌려줍니다.

                    - **지역별 순위가 아닙니다.** 전국 하나뿐입니다.
                    - **감정을 구분하지 않습니다.** 다섯 감정을 모두 합친 수가 `pressCount` 입니다.
                      감정 하나만 보려면 `GET /api/v2/emotions/press-rankings/states/{state}` 를 씁니다.
                    - 점수는 `POST /api/v2/emotions/presses` 로 쌓은 개인 프레스입니다.
                      그룹 화면 감정 버튼(`GET /api/v2/groups/press-rankings`)과는 **다른 원천**이라
                      같은 주라도 값이 다릅니다.
                    - **로그인한 누구나 볼 수 있습니다.** 어느 그룹에도 속하지 않아도 됩니다.

                    `items` 는 **상위 100명**입니다. 이 수는 고정이고 요청으로 바꿀 수 없습니다.

                    - 점수 내림차순입니다. 그 주에 한 번도 누르지 않은 기기는 담기지 않습니다.
                    - 동점은 **공동 순위**입니다. 1, 2, 2, 4 로 매깁니다.
                    - 그 주에 아무도 누르지 않았으면 빈 배열입니다.

                    `me` 는 **요청한 기기의 순위**입니다.

                    - **상위 100명 안에 있어도 `me` 를 함께 내려줍니다.** `items` 에서 자신을 찾을 필요가 없습니다.
                    - **100위 밖이어도 실제 순위를 내려줍니다.** 전국 전체를 매긴 뒤 자른 값이라
                      `items` 와 같은 공동 순위 규칙입니다.
                    - 그 주에 한 번도 누르지 않았으면 **`null`** 이고 `items` 에도 나오지 않습니다.
                    - 기기 식별자는 응답에 담지 않습니다.

                    **프레스 수와 닉네임은 전원 공개입니다.** 랭킹 제외나 익명 표시 선택이 없습니다.
                    `POST /api/v3/emotions` 의 `anonymous` 로 **지도에 익명으로 남긴 감정**과는 별개 기록이라,
                    익명으로 감정을 남긴 기기도 이 순위표에는 닉네임으로 나옵니다.
                    닉네임을 정하지 않은 기기는 `익명` 으로 보냅니다.

                    주는 **월요일 00:00 KST** 에 바뀝니다. `GET /api/v2/groups/rankings` 와 같은 경계입니다.
                    `weeksAgo` 로 몇 주 전인지 고릅니다. `0` 이 이번 주, `1` 이 지난주이며 `520` 까지 갈 수 있습니다.
                    서버가 해석한 구간을 `startAt`, `endAt` 으로 알려주고 `endAt` 은 포함하지 않습니다.

                    **순위를 저장해 두지 않고 요청할 때마다 다시 셉니다.** 지난주 결과가 고정되기를 기대하면 안 됩니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "weeksAgo 값이 범위를 벗어남",
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
    EmotionPressRankingResponse findPressRanking(
            @Min(value = 0, message = "몇 주 전인지는 0 이상이어야 합니다.")
            @Max(value = 520, message = "몇 주 전인지는 520 이하여야 합니다.")
            int weeksAgo,

            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(
            summary = "감정별 개인 전국 주간 프레스 랭킹",
            description = """
                    **감정 하나만** 골라 기기별 개인 감정 프레스를 전국 기준으로 주 단위 합산해 순위를 돌려줍니다.

                    `GET /api/v2/emotions/press-rankings` 와 집계 규칙이 같고 세는 대상만 다릅니다.
                    그쪽은 다섯 감정을 모두 합치고, 이쪽은 `state` 로 고른 감정만 셉니다.
                    **두 순위는 섞이지 않으며 같은 주라도 순서와 값이 다릅니다.**

                    `state` 는 `FRUSTRATED`, `IRRITATED`, `EXHAUSTED`, `DISCOURAGED`, `ANGRY` 중 하나입니다.
                    목록에 없는 값을 보내면 400 입니다.

                    `items` 는 **상위 100명**입니다. 이 수는 고정이고 요청으로 바꿀 수 없습니다.

                    - 그 감정의 프레스 합 내림차순입니다. 그 주에 **그 감정을** 한 번도 누르지 않은 기기는 담기지 않습니다.
                    - 동점은 **공동 순위**입니다. 1, 2, 2, 4 로 매깁니다.
                    - 그 주에 아무도 그 감정을 누르지 않았으면 빈 배열입니다.

                    `me` 는 **요청한 기기의 순위**입니다.

                    - **상위 100명 안에 있어도 `me` 를 함께 내려줍니다.**
                    - **100위 밖이어도 실제 순위를 내려줍니다.** 전국 전체를 매긴 뒤 자른 값입니다.
                    - 그 주에 **그 감정을** 한 번도 누르지 않았으면 **`null`** 입니다.
                      다른 감정을 눌렀어도 이 순위표에는 나오지 않습니다.
                    - 기기 식별자는 응답에 담지 않습니다.

                    **프레스 수와 닉네임은 전원 공개입니다.** 랭킹 제외나 익명 표시 선택이 없습니다.
                    `POST /api/v3/emotions` 의 `anonymous` 로 **지도에 익명으로 남긴 감정**과는 별개 기록입니다.

                    주는 **월요일 00:00 KST** 에 바뀝니다. 전체 랭킹과 같은 경계입니다.
                    `weeksAgo` 는 `0` 이 이번 주, `1` 이 지난주이며 `520` 까지 갈 수 있습니다.

                    **순위를 저장해 두지 않고 요청할 때마다 다시 셉니다.**
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "state 가 감정 목록에 없거나 weeksAgo 값이 범위를 벗어남",
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
    EmotionStatePressRankingResponse findPressRankingByState(
            @Parameter(description = "순위를 매길 감정", required = true, example = "ANGRY")
            EmotionState state,

            @Min(value = 0, message = "몇 주 전인지는 0 이상이어야 합니다.")
            @Max(value = 520, message = "몇 주 전인지는 520 이하여야 합니다.")
            int weeksAgo,

            @Parameter(hidden = true) UUID devicePublicId
    );
}
