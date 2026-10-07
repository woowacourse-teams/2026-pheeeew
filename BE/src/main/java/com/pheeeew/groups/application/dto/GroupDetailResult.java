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
        long weeklyStampCount,
        Integer weeklyStampRank,
        long weeklyPressCount,
        Integer weeklyPressRank
) {
    public static GroupDetailResult of(
            Group group,
            GroupViewerRole role,
            long memberCount,
            GroupStampResult stamp,
            GroupWeeklyRankResult weeklyRanks
    ) {
        return new GroupDetailResult(
                group.getPublicId(),
                group.getName(),
                group.getDescription(),
                group.getInviteCode(),
                role,
                memberCount,
                stamp,
                weeklyRanks.stampCount(),
                weeklyRanks.stampRank(),
                weeklyRanks.pressCount(),
                weeklyRanks.pressRank()
        );
    }
}
