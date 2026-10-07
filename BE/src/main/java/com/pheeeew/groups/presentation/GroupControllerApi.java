package com.pheeeew.groups.presentation;

import com.pheeeew.groups.presentation.dto.GroupCreateRequest;
import com.pheeeew.groups.presentation.dto.GroupDetailResponse;
import com.pheeeew.groups.presentation.dto.GroupResponse;
import com.pheeeew.groups.presentation.dto.GroupStampItemResponse;
import com.pheeeew.groups.presentation.dto.GroupUpdateRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import java.util.UUID;
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
                    그룹 정보와 스탬프, 현재 인원수, 이번 주 스탬프와 프레스의 개수와 순위를 반환합니다.

                    - **그룹에 속하지 않아도 조회할 수 있습니다.** 가입 여부와 무관하게 200 입니다.
                      인증은 그대로 필요합니다. 없는 그룹과 삭제된 그룹만 404 입니다.
                    - `role` 은 요청한 기기와 그룹의 관계입니다. 그룹장은 `OWNER`, 멤버는 `MEMBER`,
                      **속하지 않았거나 나갔으면 `NONE`** 입니다.
                      관리 메뉴와 가입 버튼 노출은 이 값으로 판단합니다.
                    - `inviteCode` 는 **역할과 무관하게 모두에게** 내려갑니다. 비가입자도 받습니다.
                      재발급만 그룹장 권한입니다.
                    - 네 수치는 **모두 이번 주**입니다. 주는 **월요일 00:00 KST** 에 바뀌고,
                      동점은 공동 순위(1, 2, 2, 4)입니다. 지난주는 그룹 랭킹 API 로 봅니다.
                    - **순위를 저장해 두지 않고 요청할 때마다 다시 셉니다.**

                    지도 감정(스탬프)

                    - `weeklyStampCount` — 이번 주 그 그룹으로 **지도에 남긴 감정 수**입니다.
                      **감정 버튼을 누른 횟수는 여기 들어가지 않습니다.**
                    - `weeklyStampRank` — 그 개수로 매긴 전체 그룹 중 순위입니다.
                      `weeklyStampCount` 가 0 이면 **`null`** 입니다.

                    감정 버튼(프레스)

                    - `weeklyPressCount` — 이번 주 **현재 멤버들이 각자 누른 감정 버튼 수의 합**입니다.
                      `POST /api/v2/emotions/presses` 로 누른 것이 여기 더해집니다.
                      **그룹 화면에서 따로 누르는 버튼은 없습니다.**
                    - 멤버가 **여러 그룹에 속해 있으면 한 번 누른 것이 그 그룹들에 모두 더해집니다.**
                    - **현재 멤버만 셉니다.** 주 중간에 가입하면 그 주 처음부터의 기록이 들어오고,
                      나가면 그 주에 누른 것까지 함께 빠집니다.
                    - **위치가 서비스 범위 밖이면 누른 것이 저장되지 않아 그룹에도 더해지지 않습니다.**
                    - `weeklyPressRank` — 그 개수로 매긴 전체 그룹 중 순위입니다.
                      `weeklyPressCount` 가 0 이면 **`null`** 입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
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
