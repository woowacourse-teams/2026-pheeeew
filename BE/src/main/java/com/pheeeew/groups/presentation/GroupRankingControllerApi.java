package com.pheeeew.groups.presentation;

import com.pheeeew.common.exception.ErrorResponse;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.groups.presentation.dto.GroupPressRankingResponse;
import com.pheeeew.groups.presentation.dto.GroupRankingResponse;
import com.pheeeew.groups.presentation.dto.GroupStatePressRankingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.MediaType;

@Tag(name = "그룹 랭킹", description = "그룹끼리의 주간 경쟁과 그룹 안의 감정 순위를 봅니다.")
public interface GroupRankingControllerApi {

    @Operation(
            summary = "그룹 간 주간 랭킹",
            description = """
                    그룹끼리 겨룹니다. 점수는 **그 주에 그 그룹으로 남긴 감정 수**입니다.

                    - **로그인한 누구나 볼 수 있습니다.** 어느 그룹에도 속하지 않아도 됩니다.
                    - 주는 **월요일 00:00 KST** 에 바뀝니다. `weeksAgo` 로 몇 주 전인지 고릅니다.
                      `0` 이 이번 주, `1` 이 지난주이며 계속 뒤로 갈 수 있습니다.
                    - 응답의 `hasPrevious` 가 `false` 면 **그보다 이전에는 기록이 없습니다.**
                      더 뒤로 가는 버튼을 막는 데 씁니다.
                    - 하루에 몇 개를 남기든 전부 점수입니다. 반영 한도는 없습니다.
                    - **그룹을 고르지 않고 남긴 개인 감정은 어느 그룹 점수에도 들어가지 않습니다.**
                    - 동점은 **공동 순위**입니다. 1, 2, 2, 4 로 매깁니다.
                    - 그 주에 감정이 하나도 없는 그룹은 나오지 않습니다.

                    **순위를 저장해 두지 않고 요청할 때마다 다시 셉니다.** 그래서 감정을 지우면
                    지난주 점수에서도 함께 빠집니다. 지난주 결과가 고정되기를 기대하면 안 됩니다.
                    """
    )
    GroupRankingResponse findGroupRanking(
            @Min(value = 0, message = "몇 주 전인지는 0 이상이어야 합니다.")
            @Max(value = 520, message = "몇 주 전인지는 520 이하여야 합니다.")
            int weeksAgo
    );

    @Operation(
            summary = "그룹 간 주간 감정 버튼 랭킹",
            description = """
                    그룹끼리 겨룹니다. 점수는 **그 주에 그 그룹에서 누른 감정 버튼 수**입니다.
                    다섯 감정을 모두 합친 수이며, 감정별로 나눠 보려면 `/press-rankings/states` 를 씁니다.

                    - **스탬프 랭킹(`/rankings`)과는 다른 순위입니다.** 두 점수는 섞이지 않습니다.
                    - 이 순위는 `GET /api/v2/groups/{groupId}` 의 `weeklyPressRank` 와 **같은 값**입니다.
                      멤버들의 개인 감정 프레스를 합친 순위는 `GET /api/v2/groups/press-rankings/members` 에 있고
                      이 순위에 들어가지 않습니다. 두 숫자는 원천이 달라 값이 다릅니다.
                    - **로그인한 누구나 볼 수 있습니다.** 어느 그룹에도 속하지 않아도 됩니다.
                    - 주는 **월요일 00:00 KST** 에 바뀝니다. `/rankings` 와 같은 경계입니다.
                    - `weeksAgo` 로 몇 주 전인지 고릅니다. `0` 이 이번 주, `1` 이 지난주입니다.
                    - 동점은 **공동 순위**입니다. 1, 2, 2, 4 로 매깁니다.
                    - 그 주에 한 번도 누르지 않은 그룹은 나오지 않습니다.
                    - 각 항목의 `mine` 은 **요청한 기기가 그 그룹의 멤버인지**입니다. 내 그룹을 강조하는 데 씁니다.
                    - 응답의 `hasPrevious` 가 `false` 면 그보다 이전에는 누른 기록이 없습니다.

                    **순위를 저장해 두지 않고 요청할 때마다 다시 셉니다.**
                    """
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
                                    {"code": "DEVICE-004", "message": "인증 정보를 사용할 수 없습니다."}
                                    """)))
    })
    GroupPressRankingResponse findPressRanking(
            @Min(value = 0, message = "몇 주 전인지는 0 이상이어야 합니다.")
            @Max(value = 520, message = "몇 주 전인지는 520 이하여야 합니다.")
            int weeksAgo,

            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(
            summary = "그룹 간 주간 감정 프레스 랭킹 (멤버 개인 프레스 합산)",
            description = """
                    그룹끼리 겨룹니다. 점수는 **그 그룹의 현재 멤버들이 그 주에 누른 개인 감정 프레스의 합**입니다.
                    다섯 감정을 모두 합친 수이며, 감정별로 나눠 보려면 `/press-rankings/members/states/{state}` 를 씁니다.
                    `weeksAgo` 가 `0` 이면 `GET /api/v3/groups/{groupId}` 의 `weeklyEmotionPressCount` 와
                    같은 값이고 순위도 같습니다. 그 상세는 이번 주만 보여줍니다.

                    - **`GET /api/v2/groups/press-rankings` 와는 다른 순위입니다.** 그 경로는 구버전 앱이
                      `POST /api/v2/groups/{groupId}/presses` 로 그룹에 직접 쌓은 수를 세고, 이 경로는
                      `POST /api/v2/emotions/presses` 로 각자 누른 개인 프레스를 멤버십으로 합칩니다.
                      두 숫자는 섞이지 않으며 값이 다릅니다.
                    - **스탬프 랭킹(`/rankings`)과도 다릅니다.** 그쪽은 지도에 남긴 감정을 셉니다.

                    - **로그인한 누구나 볼 수 있습니다.** 어느 그룹에도 속하지 않아도 됩니다.
                    - 주는 **월요일 00:00 KST** 에 바뀝니다. 다른 랭킹과 같은 경계입니다.
                    - `weeksAgo` 로 몇 주 전인지 고릅니다. `0` 이 이번 주, `1` 이 지난주입니다.
                    - 동점은 **공동 순위**입니다. 1, 2, 2, 4 로 매깁니다.
                    - 그 주에 멤버 누구도 누르지 않은 그룹은 나오지 않습니다.
                    - 순위를 자르지 않고 **전부** 내려줍니다.

                    **멤버십은 조회 시점에 평가합니다.** 지금 멤버인 사람의 기록만 세므로,
                    멤버가 바뀌면 지난주 순위도 다시 계산됩니다. 누가 나가면 그 주 기록이 전부 빠집니다.

                    **한 기기가 여러 그룹에 속하면 한 번 누른 것이 그 그룹들에 모두 세어집니다.**

                    각 항목의 `mine` 은 **요청한 기기가 그 그룹의 멤버인지**입니다. 나간 그룹은 `false` 입니다.
                    `hasPrevious` 가 `false` 면 그보다 이전에는 누른 기록이 없습니다.

                    **순위를 저장해 두지 않고 요청할 때마다 다시 셉니다.**
                    """
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
                                    {"code": "DEVICE-004", "message": "인증 정보를 사용할 수 없습니다."}
                                    """)))
    })
    GroupPressRankingResponse findEmotionPressRanking(
            @Min(value = 0, message = "몇 주 전인지는 0 이상이어야 합니다.")
            @Max(value = 520, message = "몇 주 전인지는 520 이하여야 합니다.")
            int weeksAgo,

            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(
            summary = "감정 하나의 그룹 간 주간 감정 프레스 랭킹 (멤버 개인 프레스 합산)",
            description = """
                    감정 하나에 대해 그룹 순위를 매깁니다. 점수는 **그 그룹의 현재 멤버들이 그 주에
                    그 감정으로 누른 개인 프레스의 합**입니다. 다섯 감정을 모두 보려면 감정마다 한 번씩 호출합니다.

                    - 다섯 감정을 합친 순위는 `GET /api/v2/groups/press-rankings/members` 에 있습니다.
                    - **`/press-rankings/states/{state}` 와는 다른 순위입니다.** 그 경로는 구버전 앱이
                      그룹에 직접 쌓은 수를 세고, 이 경로는 개인 프레스를 멤버십으로 합칩니다.

                    - `state` 는 `FRUSTRATED`, `IRRITATED`, `EXHAUSTED`, `DISCOURAGED`, `ANGRY` 중 하나입니다.
                      그 밖의 값을 보내면 `400` 입니다.
                    - 그 감정을 그 주에 멤버 누구도 누르지 않았으면 `items` 가 **빈 배열**입니다. 오류가 아닙니다.
                    - 응답의 `state` 에 조회한 감정이 그대로 담겨 옵니다. 여러 감정을 동시에 요청할 때 짝을 맞추는 데 씁니다.
                    - 기간, 공동 순위, `mine`, `hasPrevious`, 멤버십 평가 시점 규칙은
                      `/press-rankings/members` 와 같습니다.

                    **`hasPrevious` 는 감정을 가리지 않습니다.** 그 주 이전에 어떤 감정이든 누른 기록이 있으면
                    `true` 입니다. `/press-rankings/states/{state}` 와 같은 방식입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "감정 값이나 weeksAgo 값이 올바르지 않음",
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
    GroupStatePressRankingResponse findEmotionPressRankingByState(
            @Parameter(description = "순위를 매길 감정", required = true, example = "ANGRY")
            EmotionState state,

            @Min(value = 0, message = "몇 주 전인지는 0 이상이어야 합니다.")
            @Max(value = 520, message = "몇 주 전인지는 520 이하여야 합니다.")
            int weeksAgo,

            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(
            summary = "감정 하나의 그룹 간 주간 감정 버튼 랭킹",
            description = """
                    **감정 하나**에 대한 그룹 순위를 매깁니다. 다섯 감정을 모두 보려면 감정마다 한 번씩 호출합니다.

                    - `state` 는 `FRUSTRATED`, `IRRITATED`, `EXHAUSTED`, `DISCOURAGED`, `ANGRY` 중 하나입니다.
                      그 밖의 값을 보내면 `400` 입니다.
                    - 그 감정을 그 주에 한 번도 누르지 않았으면 `items` 가 **빈 배열**입니다. 오류가 아닙니다.
                    - 응답의 `state` 에 조회한 감정이 그대로 담겨 옵니다. 여러 감정을 동시에 요청할 때 짝을 맞추는 데 씁니다.
                    - 기간, 공동 순위, `mine`, `hasPrevious` 규칙은 `/press-rankings` 와 같습니다.
                    - 다섯 감정을 합친 순위는 `/press-rankings` 에서 봅니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "감정 값이나 weeksAgo 값이 올바르지 않음",
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
    GroupStatePressRankingResponse findPressRankingByState(
            @Parameter(description = "순위를 매길 감정", required = true, example = "ANGRY")
            EmotionState state,

            @Min(value = 0, message = "몇 주 전인지는 0 이상이어야 합니다.")
            @Max(value = 520, message = "몇 주 전인지는 520 이하여야 합니다.")
            int weeksAgo,

            @Parameter(hidden = true) UUID devicePublicId
    );
}
