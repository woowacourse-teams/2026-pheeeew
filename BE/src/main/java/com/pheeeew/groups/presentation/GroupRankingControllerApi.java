package com.pheeeew.groups.presentation;

import com.pheeeew.groups.presentation.dto.GroupRankingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

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
}
