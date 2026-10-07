package com.pheeeew.groups.presentation;

import com.pheeeew.groups.presentation.dto.GroupDetailV3Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

@Tag(name = "그룹")
public interface GroupDetailV3ControllerApi {

    @Operation(
            summary = "그룹 상세 (v3)",
            description = """
                    그룹 정보와 스탬프, 현재 인원수, 이번 주 스탬프와 감정 프레스의 개수와 순위를 반환합니다.

                    - `/api/v3` 에는 **이 엔드포인트 하나만** 있습니다. 목록, 생성, 가입, 탈퇴, 수정, 초대 코드,
                      스탬프, 랭킹, 감정 버튼은 모두 `/api/v2` 경로를 그대로 씁니다.
                    - `GET /api/v2/groups/{groupId}` 는 **구버전 앱 호환으로만 남아 있고 그룹 멤버만** 조회할 수 있습니다.
                      새 클라이언트는 이 경로를 씁니다.

                    - **그룹에 속하지 않아도 조회할 수 있습니다.** 가입 여부와 무관하게 200 입니다.
                      인증은 그대로 필요합니다. 없는 그룹과 삭제된 그룹만 404 입니다.
                    - `role` 로 요청한 기기와 그룹의 관계를 알려줍니다. `OWNER`, `MEMBER`,
                      속하지 않았거나 나갔으면 `NONE` 입니다. 관리 메뉴와 가입 버튼 노출은 이 값으로 판단합니다.
                    - `inviteCode` 는 **역할과 무관하게 모두에게** 내려갑니다. 비가입자도 받습니다.
                      재발급만 그룹장 권한입니다.
                    - 네 수치는 **모두 이번 주**입니다. 주는 **월요일 00:00 KST** 에 바뀌고,
                      동점은 공동 순위(1, 2, 2, 4)입니다. **순위를 저장해 두지 않고 요청할 때마다 다시 셉니다.**

                    지도 감정(스탬프)

                    - `weeklyStampCount` — 이번 주 그 그룹으로 **지도에 남긴 감정 수**입니다.
                      감정 버튼을 누른 횟수는 들어가지 않습니다.
                    - `weeklyStampRank` — 그 수로 매긴 전체 그룹 중 순위입니다. 개수가 0 이면 **`null`** 입니다.
                      전체 순위표는 `GET /api/v2/groups/rankings` 입니다.

                    멤버들의 개인 감정 버튼

                    - `weeklyEmotionPressCount` — 이번 주 **현재 멤버들이 각자 `POST /api/v2/emotions/presses` 로
                      누른 수의 합**입니다. 구버전 앱이 그룹 화면에서 누른 것은 들어가지 않습니다 —
                      그 값은 `GET /api/v2/groups/{groupId}` 의 `weeklyPresses`, `weeklyPressRank` 와
                      `GET /api/v2/groups/press-rankings` 에 있습니다.
                    - 멤버가 **여러 그룹에 속해 있으면 한 번 누른 것이 그 그룹들에 모두 더해집니다.**
                    - **현재 멤버만 셉니다.** 주 중간에 가입하면 그 주 처음부터의 기록이 들어오고,
                      나가면 그 주에 누른 것까지 함께 빠집니다.
                    - **위치가 서비스 범위 밖이면 저장되지 않아 그룹에도 더해지지 않습니다.**
                    - `weeklyEmotionPressRank` — 그 수로 매긴 전체 그룹 중 순위입니다. 0 이면 **`null`** 입니다.
                      **이 순위에 해당하는 전체 순위표 엔드포인트는 아직 없습니다.**
                    - **감정별 분해는 이 응답에 없습니다.** 감정별 전 그룹 순위는
                      `GET /api/v2/groups/press-rankings/states/{state}` 에서 보고, 그 원천은 구버전 그룹 버튼입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증할 수 없거나 등록되지 않은 기기"),
            @ApiResponse(responseCode = "404", description = "없는 그룹이거나 삭제된 그룹")
    })
    GroupDetailV3Response findOne(
            UUID groupId,
            @Parameter(hidden = true) UUID devicePublicId
    );
}
