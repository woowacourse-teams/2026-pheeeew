package com.pheeeew.groups.application.dto;

import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupViewerRole;
import java.util.UUID;

public record GroupDetailResult(
        UUID publicId,
        String name,
        String description,
        String inviteCode,
        GroupViewerRole role,
        long memberCount,
        GroupStampResult stamp,
        long weeklyScore,
        Integer weeklyRank
) {
    public static GroupDetailResult of(
            Group group,
            GroupViewerRole role,
            long memberCount,
            GroupStampResult stamp,
            long weeklyScore,
            Integer weeklyRank
    ) {
        return new GroupDetailResult(
                group.getPublicId(),
                group.getName(),
                group.getDescription(),
                group.getInviteCode(),
                role,
                memberCount,
                stamp,
                weeklyScore,
                weeklyRank
        );
    }
}
