package com.pheeeew.groups.presentation.dto;

import com.pheeeew.groups.application.dto.GroupResult;
import com.pheeeew.groups.domain.GroupJoinType;
import com.pheeeew.groups.domain.GroupRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record GroupResponse(
        UUID groupId,
        String name,
        String description,
        String inviteCode,
        @Schema(
                description = "이 그룹의 가입 방식. 생성 요청에서 생략했으면 서버가 고른 PRIVATE 입니다.",
                example = "PRIVATE"
        )
        GroupJoinType joinType,
        GroupRole role,
        long memberCount,
        GroupStampResponse stamp
) {

    public static GroupResponse from(GroupResult result) {
        return new GroupResponse(
                result.publicId(),
                result.name(),
                result.description(),
                result.inviteCode(),
                result.joinType(),
                result.role(),
                result.memberCount(),
                GroupStampResponse.from(result.stamp())
        );
    }
}
