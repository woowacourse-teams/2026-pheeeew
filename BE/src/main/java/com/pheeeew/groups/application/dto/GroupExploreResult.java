package com.pheeeew.groups.application.dto;

import com.pheeeew.groups.domain.GroupJoinType;
import com.pheeeew.groups.domain.StampFrame;
import com.pheeeew.groups.domain.repository.projection.GroupExploreProjection;
import java.time.Instant;
import java.util.UUID;

public record GroupExploreResult(
        UUID publicId,
        String name,
        String description,
        GroupJoinType joinType,
        long memberCount,
        boolean joined,
        GroupStampResult stamp,
        Instant createdAt,
        long id
) {
    public static GroupExploreResult from(GroupExploreProjection projection) {
        return new GroupExploreResult(
                projection.getGroupPublicId(),
                projection.getName(),
                projection.getDescription(),
                GroupJoinType.valueOf(projection.getJoinType()),
                projection.getMemberCount(),
                projection.getJoined(),
                GroupStampResult.of(
                        projection.getStampText(),
                        projection.getStampTextColor(),
                        projection.getStampBackgroundColor(),
                        StampFrame.valueOf(projection.getStampFrame())
                ),
                projection.getCreatedAt(),
                projection.getId()
        );
    }
}
