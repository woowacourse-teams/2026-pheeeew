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
        long weeklyScore,
        Integer weeklyRank
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
                result.weeklyScore(),
                result.weeklyRank()
        );
    }
}
