package com.pheeeew.groups.presentation.dto;

import com.pheeeew.groups.application.dto.GroupDetailResult;
import com.pheeeew.groups.domain.GroupViewerRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record GroupDetailResponse(
        UUID groupId,
        String name,
        String description,
        String inviteCode,
        @Schema(description = "요청한 기기와 그룹의 관계입니다. OWNER, MEMBER, 속하지 않았거나 나갔으면 NONE 입니다.")
        GroupViewerRole role,
        long memberCount,
        GroupStampResponse stamp,
        @Schema(description = "서버가 센 이번 주 지도 감정 수입니다. 감정 버튼을 누른 횟수는 들어가지 않습니다.")
        long weeklyStampCount,
        @Schema(description = "weeklyStampCount 로 매긴 전체 그룹 중 순위입니다. weeklyStampCount 가 0 이면 null 입니다.")
        Integer weeklyStampRank,
        @Schema(description = "서버가 센 이번 주 현재 멤버들의 개인 감정 버튼 수의 합입니다.")
        long weeklyPressCount,
        @Schema(description = "weeklyPressCount 로 매긴 전체 그룹 중 순위입니다. weeklyPressCount 가 0 이면 null 입니다.")
        Integer weeklyPressRank
) {
    public static GroupDetailResponse from(GroupDetailResult result) {
        return new GroupDetailResponse(
                result.publicId(),
                result.name(),
                result.description(),
                result.inviteCode(),
                result.role(),
                result.memberCount(),
                GroupStampResponse.from(result.stamp()),
                result.weeklyStampCount(),
                result.weeklyStampRank(),
                result.weeklyPressCount(),
                result.weeklyPressRank()
        );
    }
}
