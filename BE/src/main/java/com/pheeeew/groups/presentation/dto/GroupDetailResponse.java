package com.pheeeew.groups.presentation.dto;

import com.pheeeew.groups.application.dto.GroupDetailResult;
import com.pheeeew.groups.application.dto.GroupResult;
import com.pheeeew.groups.domain.GroupRole;
import java.util.UUID;

public record GroupDetailResponse(
        UUID groupId,
        String name,
        String description,
        String inviteCode,
        GroupRole role,
        long memberCount,
        GroupStampResponse stamp,
        GroupPressCountResponse todayPresses,
        long weeklyScore,
        Integer weeklyRank
) {

    public static GroupDetailResponse from(GroupDetailResult result) {
        GroupResult group = result.group();

        return new GroupDetailResponse(
                group.publicId(),
                group.name(),
                group.description(),
                group.inviteCode(),
                group.role(),
                group.memberCount(),
                GroupStampResponse.from(group.stamp()),
                GroupPressCountResponse.from(result.todayPresses()),
                result.weeklyScore(),
                result.weeklyRank()
        );
    }
}
