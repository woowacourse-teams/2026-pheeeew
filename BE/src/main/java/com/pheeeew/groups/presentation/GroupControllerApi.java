package com.pheeeew.groups.presentation;

import com.pheeeew.groups.presentation.dto.GroupCreateRequest;
import com.pheeeew.groups.presentation.dto.GroupDetailResponse;
import com.pheeeew.groups.presentation.dto.GroupPressCountResponse;
import com.pheeeew.groups.presentation.dto.GroupPressRequest;
import com.pheeeew.groups.presentation.dto.GroupResponse;
import com.pheeeew.groups.presentation.dto.GroupStampItemResponse;
import com.pheeeew.groups.presentation.dto.GroupUpdateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "그룹", description = "그룹을 만들고 관리합니다.")
public interface GroupControllerApi {

    @Operation(
            summary = "그룹 생성",
            description = """
                    그룹을 만들고 만든 기기를 그룹장으로 등록합니다.

                    - `name` 은 2~10자이며 **살아 있는 그룹 사이에서 유일**합니다. 앞뒤 공백은 잘라냅니다.
                      이미 쓰는 이름이면 409 입니다. 삭제된 그룹의 이름은 다시 쓸 수 있습니다.
                    - `description` 은 선택이며 100자까지입니다. 비워 보내면 `null` 로 저장합니다.
                    - **스탬프를 함께 보냅니다.** 그룹당 스탬프는 정확히 하나이고, 스탬프 없는 그룹은 만들 수 없습니다.
                      색은 `#RRGGBB` 또는 `#RRGGBBAA` 형식만 확인하며 어떤 색인지는 판단하지 않습니다.
                    - **초대 코드는 서버가 만듭니다.** 요청에 담지 않습니다.
                      혼동되는 글자(`I`, `L`, `O`, `U`)를 뺀 6자리입니다.
                    - 그룹장은 **생성자로 고정**되며 위임할 수 없습니다. 인원 제한은 없습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "409", description = "이미 사용 중인 그룹 이름")
    })
    ResponseEntity<GroupResponse> save(
            @Valid GroupCreateRequest request,
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(
            summary = "내가 속한 그룹 목록",
            description = """
                    요청한 기기가 **현재 속해 있는** 그룹만 가입한 순서로 반환합니다.

                    나갔거나 삭제된 그룹은 나오지 않습니다.
                    """
    )
    List<GroupResponse> findMine(@Parameter(hidden = true) UUID devicePublicId);

    @Operation(
            summary = "내 그룹 스탬프 목록",
            description = """
                    인증된 기기가 현재 속한 그룹의 `groupId`, `name`, `stamp`만 반환합니다.
                    스탬프 선택과 그룹 필터에 사용할 수 있습니다.

                    - 탈퇴한 그룹과 삭제된 그룹은 제외합니다.
                    - 가입한 시각이 빠른 순서로 반환합니다. 같은 시각에는 가입 식별자 순서입니다.
                    - 페이지네이션 없이 전체 목록을 JSON 배열로 반환합니다.
                    - 소속 그룹이 없으면 빈 배열을 반환합니다. 요청 파라미터는 없습니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "내 그룹 스탬프 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증할 수 없거나 등록되지 않은 기기")
    })
    List<GroupStampItemResponse> findMyStamps(@Parameter(hidden = true) UUID devicePublicId);

    @Operation(
            summary = "그룹 상세",
            description = """
                    그룹 정보와 스탬프, 현재 인원수를 반환합니다.

                    - **멤버만 조회할 수 있습니다.** 속하지 않은 그룹은 **403** 입니다.
                      없는 그룹이나 삭제된 그룹만 404 이므로, 클라이언트가 "권한 없음" 과 "사라진 그룹" 을 구분할 수 있습니다.
                    - `inviteCode` 는 **모든 멤버**에게 보입니다. 재발급만 그룹장 권한입니다.
                    - `role` 로 요청한 기기가 그룹장인지 알 수 있습니다.

                    화면에 필요한 숫자들을 함께 내려줍니다. **스탬프와 프레스는 서로 다른 지표입니다.**
                    스탬프는 지도에 남긴 감정이고, 프레스는 감정 버튼을 누른 횟수입니다. 둘은 섞이지 않습니다.

                    감정 버튼(프레스)

                    - `weeklyPresses` — **이번 주** 그룹 전체가 감정 버튼을 누른 횟수입니다.
                      다섯 감정을 0 인 것까지 **항상 전부** 담고, `total` 이 이번 주 프레스 총합입니다.
                    - `todayPresses` — 같은 형태의 **오늘** 집계입니다. 하루는 한국시간 자정에 바뀝니다.
                    - `weeklyPressRank` — 이번 주 프레스 수로 매긴 전체 그룹 중 순위입니다.
                      한 번도 누르지 않았으면 순위표에 오르지 않으므로 **`null`** 입니다.

                    지도 감정(스탬프)

                    - `weeklyScore` — 이번 주 그 그룹으로 **지도에 남긴 감정 수**입니다.
                      **버튼을 누른 횟수는 여기 들어가지 않습니다.**
                    - `weeklyRank` — 그 점수로 매긴 전체 그룹 중 순위입니다. `weeklyScore` 가 0 이면 **`null`** 입니다.

                    두 순위 모두 주는 **월요일 00:00 KST** 에 바뀌고, 동점은 공동 순위(1, 2, 2, 4)입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "그룹 멤버가 아님"),
            @ApiResponse(responseCode = "404", description = "없는 그룹이거나 삭제된 그룹")
    })
    GroupDetailResponse findOne(
            UUID groupId,
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(
            summary = "그룹 이름, 설명, 스탬프 변경",
            description = """
                    **그룹장만 할 수 있습니다.** 멤버가 호출하면 403 입니다.

                    - **세 값을 모두 보냅니다.** 부분 변경이 아니라 보낸 값으로 통째로 덮어씁니다.
                      빠뜨린 값은 그대로 두는 것이 아니라 검증에서 걸립니다.
                    - 이름을 바꾸지 않을 때는 현재 이름을 그대로 보내면 됩니다. 중복 검사에서 자기 이름은 제외합니다.
                    - **스탬프를 바꾸면 과거 기록에 찍힌 스탬프도 함께 바뀝니다.** 그룹당 스탬프가 하나라
                      기록은 스탬프를 따로 저장하지 않습니다.
                    - 색은 `#RRGGBB` 또는 `#RRGGBBAA` 형식만 확인하며, 어떤 색인지는 판단하지 않습니다.
                      배경과 글자가 같은 색이어도 막지 않으므로 클라이언트가 조합을 정해야 합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(responseCode = "403", description = "그룹장이 아니거나 멤버가 아님"),
            @ApiResponse(responseCode = "404", description = "없는 그룹이거나 삭제된 그룹"),
            @ApiResponse(responseCode = "409", description = "이미 사용 중인 그룹 이름")
    })
    GroupResponse update(
            UUID groupId,
            @Valid GroupUpdateRequest request,
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(
            summary = "감정 버튼 누르기",
            description = """
                    그룹 화면의 감정 버튼을 누릅니다. **그룹 멤버만** 누를 수 있습니다.

                    - 요청 형식이 두 가지입니다. `state` 와 `counts` 중 **정확히 하나만** 보냅니다.
                      둘 다 보내거나 둘 다 비우면 400 입니다.
                    - `counts` 는 감정별 누른 횟수를 한 번에 묶어 보냅니다. 예: `{"ANGRY": 9, "EXHAUSTED": 3}`.
                      **일정 주기로 모아 보내는 쪽을 권장합니다.** 버튼마다 요청을 보내지 않습니다.
                    - `state` 는 **구버전 호환용**입니다. `counts` 에 `1` 을 보낸 것과 같게 처리합니다.
                    - `counts` 의 값이 `0` 인 감정은 **누르지 않은 것으로 보고 넘깁니다.**
                      모든 값이 `0` 이거나 빈 객체여도 400 이 아니라 **오늘 집계만 돌려줍니다.**
                      음수는 400 입니다.
                    - 상한을 넘겨도 **거절하지 않고 넘친 만큼만 버립니다.** 감정 하나당 `30`,
                      요청 전체 합 `100` 까지 반영합니다. 전체 합이 넘치면 감정 이름 오름차순으로 채웁니다.
                    - 누른 뒤의 **오늘 집계를 바로 돌려줍니다.** 다시 조회할 필요가 없습니다.
                      누르지 않은 감정도 `0` 으로 내려와 다섯 감정이 항상 모두 있습니다.
                    - 취소는 없습니다.
                    - **지도에 감정이 찍히지 않고 그룹 점수와 순위에도 영향이 없습니다.**
                      점수는 지도에 남긴 감정만 셉니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "누르기 성공"),
            @ApiResponse(responseCode = "400", description = "state 와 counts 를 둘 다 또는 둘 다 보내지 않음, 다루지 않는 감정, counts 값이 음수이거나 null"),
            @ApiResponse(responseCode = "403", description = "그룹 멤버가 아님"),
            @ApiResponse(responseCode = "404", description = "없는 그룹이거나 삭제된 그룹")
    })
    GroupPressCountResponse press(
            UUID groupId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GroupPressRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "묶음(권장)",
                                            value = """
                                                    {"counts":{"ANGRY":9,"EXHAUSTED":3}}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "구버전 단일 감정",
                                            value = """
                                                    {"state":"ANGRY"}
                                                    """
                                    )
                            }
                    )
            )
            @Valid GroupPressRequest request,
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(
            summary = "감정 버튼 주간 집계",
            description = """
                    그 주에 그룹이 누른 감정 버튼을 감정별로 합쳐서 돌려줍니다. **그룹 멤버만** 볼 수 있습니다.

                    - 주는 **월요일 00:00 KST** 에 바뀝니다. 그룹 간 주간 랭킹과 같은 경계입니다.
                    - `weeksAgo` 로 몇 주 전인지 고릅니다. `0` 이 이번 주, `1` 이 지난주입니다.
                    - 누르지 않은 감정도 `0` 으로 내려와 **다섯 감정이 항상 모두 있습니다.**
                    - `POST /api/v2/groups/{groupId}/presses` 와 그룹 상세의 집계는 **오늘치**입니다.
                      이 API 만 주간입니다.
                    - **지도에 남긴 감정과는 다릅니다.** 그룹 점수와 순위는 지도 감정만 세고,
                      버튼 누르기는 이 집계에만 들어갑니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "weeksAgo 값이 올바르지 않음"),
            @ApiResponse(responseCode = "403", description = "그룹 멤버가 아님"),
            @ApiResponse(responseCode = "404", description = "없는 그룹이거나 삭제된 그룹")
    })
    GroupPressCountResponse findWeeklyPresses(
            UUID groupId,
            @Min(value = 0, message = "몇 주 전인지는 0 이상이어야 합니다.")
            @Max(value = 520, message = "몇 주 전인지는 520 이하여야 합니다.")
            int weeksAgo,
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(
            summary = "초대 코드 재발급",
            description = """
                    **그룹장만 할 수 있습니다.**

                    코드에는 만료가 없으므로, 유출되었을 때 이 API 로 바꾸는 것이 유일한 대응입니다.
                    **재발급하면 이전 코드는 즉시 무효**가 되고 그 코드로는 들어올 수 없습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재발급 성공"),
            @ApiResponse(responseCode = "403", description = "그룹장이 아니거나 멤버가 아님"),
            @ApiResponse(responseCode = "404", description = "없는 그룹이거나 삭제된 그룹")
    })
    GroupResponse reissueInviteCode(
            UUID groupId,
            @Parameter(hidden = true) UUID devicePublicId
    );

    @Operation(
            summary = "그룹 삭제",
            description = """
                    **그룹장만, 그리고 다른 멤버가 아무도 남아 있지 않을 때만** 삭제할 수 있습니다.
                    멤버가 남아 있으면 409 입니다.

                    그룹장은 그룹을 나갈 수 없으므로, **멤버가 모두 나간 뒤 그룹을 삭제하는 것이
                    그룹장이 빠지는 유일한 방법**입니다.

                    소프트 삭제라 그룹에 쌓인 기록은 그대로 남습니다. 삭제한 이름은 다시 쓸 수 있습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "403", description = "그룹장이 아니거나 멤버가 아님"),
            @ApiResponse(responseCode = "404", description = "없는 그룹이거나 삭제된 그룹"),
            @ApiResponse(responseCode = "409", description = "다른 멤버가 남아 있음")
    })
    ResponseEntity<Void> delete(
            UUID groupId,
            @Parameter(hidden = true) UUID devicePublicId
    );
}
