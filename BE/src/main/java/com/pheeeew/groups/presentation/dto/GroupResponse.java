package com.pheeeew.groups.presentation.dto;

import com.pheeeew.groups.application.dto.GroupResult;
import com.pheeeew.groups.domain.GroupRole;
import java.util.UUID;

public record GroupResponse(
        UUID groupId,
        String name,
        String description,
        String inviteCode,
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
                result.role(),
                result.memberCount(),
                GroupStampResponse.from(result.stamp())
        );
    }
}
