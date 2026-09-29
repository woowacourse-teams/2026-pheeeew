package com.pheeeew.groups.application.dto;

import com.pheeeew.groups.domain.repository.projection.GroupScoreProjection;
import java.util.UUID;

public record GroupRankingItem(
        int rank,
        UUID groupPublicId,
        String name,
        GroupStampResult stamp,
        long score
) {

    public static GroupRankingItem of(int rank, GroupScoreProjection projection) {
        return new GroupRankingItem(
                rank,
                projection.getGroupPublicId(),
                projection.getName(),
                new GroupStampResult(
                        projection.getStampText(),
                        projection.getStampTextColor(),
                        projection.getStampBackgroundColor(),
                        projection.getStampFrame()
                ),
                projection.getScore()
        );
    }
}
