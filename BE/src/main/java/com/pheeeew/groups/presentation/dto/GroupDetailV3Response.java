package com.pheeeew.groups.presentation.dto;

import com.pheeeew.groups.application.dto.GroupDetailV3Result;
import com.pheeeew.groups.domain.GroupViewerRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record GroupDetailV3Response(
        @Schema(description = "그룹 공개 식별자", example = "5f2b1c84-9d0e-4a13-b6c7-2e8f0a4d7b19")
        UUID groupId,
        @Schema(example = "한숨모임")
        String name,
        @Schema(description = "없으면 null 입니다.", nullable = true, example = "퇴근하고 한숨 쉬는 모임")
        String description,
        @Schema(description = "가입 여부와 무관하게 모두에게 내려갑니다.", example = "ABCD12")
        String inviteCode,
        @Schema(
                description = "요청한 기기와 그룹의 관계입니다. OWNER, MEMBER, 속하지 않았거나 나갔으면 NONE 입니다.",
                example = "NONE"
        )
        GroupViewerRole role,
        @Schema(description = "나가지 않은 멤버 수", example = "7")
        long memberCount,
        GroupStampResponse stamp,
        @Schema(
                description = "이번 주 이 그룹으로 지도에 남긴 감정 수입니다. 감정 버튼을 누른 횟수는 들어가지 않습니다.",
                example = "42"
        )
        long weeklyStampCount,
        @Schema(
                description = "weeklyStampCount 로 매긴 전체 그룹 중 순위입니다. 동점은 공동 순위이고, "
                        + "weeklyStampCount 가 0 이면 순위표에 오르지 않아 null 입니다.",
                nullable = true,
                example = "3"
        )
        Integer weeklyStampRank,
        @Schema(
                description = "이번 주 현재 멤버들이 각자 POST /api/v2/emotions/presses 로 누른 수의 합입니다. "
                        + "구버전 앱이 그룹 화면에서 누른 것은 들어가지 않습니다.",
                example = "1204"
        )
        long weeklyEmotionPressCount,
        @Schema(
                description = "weeklyEmotionPressCount 로 매긴 전체 그룹 중 순위입니다. 동점은 공동 순위이고, "
                        + "weeklyEmotionPressCount 가 0 이면 순위표에 오르지 않아 null 입니다. "
                        + "weeklyStampRank 와 원천이 달라 값이 다릅니다.",
                nullable = true,
                example = "1"
        )
        Integer weeklyEmotionPressRank
) {
    public static GroupDetailV3Response from(GroupDetailV3Result result) {
        return new GroupDetailV3Response(
                result.publicId(),
                result.name(),
                result.description(),
                result.inviteCode(),
                result.role(),
                result.memberCount(),
                GroupStampResponse.from(result.stamp()),
                result.weeklyStampCount(),
                result.weeklyStampRank(),
                result.weeklyEmotionPressCount(),
                result.weeklyEmotionPressRank()
        );
    }
}
