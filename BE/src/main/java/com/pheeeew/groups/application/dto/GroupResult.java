package com.pheeeew.groups.application.dto;

import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupRole;
import java.util.UUID;

public record GroupResult(
        UUID publicId,
        String name,
        String description,
        String inviteCode,
        GroupRole role,
        long memberCount,
        GroupStampResult stamp
) {

    public static GroupResult of(Group group, GroupRole role, long memberCount, GroupStampResult stamp) {
        return new GroupResult(
                group.getPublicId(),
                group.getName(),
                group.getDescription(),
                group.getInviteCode(),
                role,
                memberCount,
                stamp
        );
    }
}
