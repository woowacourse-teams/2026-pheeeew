package com.pheeeew.groups.presentation.dto;

import com.pheeeew.groups.application.dto.GroupDetailV3Result;
import com.pheeeew.groups.domain.GroupViewerRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record GroupDetailV3Response(
        UUID groupId,
        String name,
        String description,
        String inviteCode,
        @Schema(description = "요청한 기기와 그룹의 관계입니다. OWNER, MEMBER, 속하지 않았거나 나갔으면 NONE 입니다.")
        GroupViewerRole role,
        long memberCount,
        GroupStampResponse stamp,
        @Schema(description = "이번 주 이 그룹으로 지도에 남긴 감정 수입니다. 감정 버튼을 누른 횟수는 들어가지 않습니다.")
        long weeklyStampCount,
        @Schema(description = "weeklyStampCount 로 매긴 전체 그룹 중 순위입니다. 0 이면 null 입니다.")
        Integer weeklyStampRank,
        @Schema(description = "이번 주 현재 멤버들이 각자 POST /api/v2/emotions/presses 로 누른 수의 합입니다. "
                + "구버전 앱이 그룹 화면에서 누른 것은 들어가지 않습니다.")
        long weeklyEmotionPressCount,
        @Schema(description = "weeklyEmotionPressCount 로 매긴 전체 그룹 중 순위입니다. 0 이면 null 입니다.")
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
