package com.pheeeew.groups.application.dto;

import com.pheeeew.groups.domain.repository.projection.GroupScoreProjection;
import java.util.UUID;

public record GroupPressRankingItem(
        int rank,
        UUID groupPublicId,
        String name,
        GroupStampResult stamp,
        long score,
        boolean mine
) {
    public static GroupPressRankingItem of(int rank, GroupScoreProjection projection, boolean mine) {
        return new GroupPressRankingItem(
                rank,
                projection.getGroupPublicId(),
                projection.getName(),
                new GroupStampResult(
                        projection.getStampText(),
                        projection.getStampTextColor(),
                        projection.getStampBackgroundColor(),
                        projection.getStampFrame()
                ),
                projection.getScore(),
                mine
        );
    }
}
