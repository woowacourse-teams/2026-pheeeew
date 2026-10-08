package com.pheeeew.groups.application.dto;

import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupViewerRole;
import java.util.UUID;

public record GroupDetailV3Result(
        UUID publicId,
        String name,
        String description,
        String inviteCode,
        GroupViewerRole role,
        long memberCount,
        GroupStampResult stamp,
        long weeklyStampCount,
        Integer weeklyStampRank,
        long weeklyEmotionPressCount,
        Integer weeklyEmotionPressRank
) {
    public static GroupDetailV3Result of(
            Group group,
            GroupViewerRole role,
            long memberCount,
            GroupStampResult stamp,
            GroupWeeklyRankResult weeklyRanks
    ) {
        return new GroupDetailV3Result(
                group.getPublicId(),
                group.getName(),
                group.getDescription(),
                group.getInviteCode(),
                role,
                memberCount,
                stamp,
                weeklyRanks.stampCount(),
                weeklyRanks.stampRank(),
                weeklyRanks.emotionPressCount(),
                weeklyRanks.emotionPressRank()
        );
    }
}
