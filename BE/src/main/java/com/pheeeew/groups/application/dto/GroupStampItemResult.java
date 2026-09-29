package com.pheeeew.groups.application.dto;

import com.pheeeew.groups.domain.repository.projection.GroupStampProjection;
import java.util.UUID;

public record GroupStampItemResult(UUID publicId, String name, GroupStampResult stamp) {

    public static GroupStampItemResult of(UUID publicId, String name, GroupStampResult stamp) {
        return new GroupStampItemResult(publicId, name, stamp);
    }

    public static GroupStampItemResult from(GroupStampProjection projection) {
        return of(
                projection.getGroupPublicId(),
                projection.getName(),
                GroupStampResult.of(
                        projection.getStampText(),
                        projection.getStampTextColor(),
                        projection.getStampBackgroundColor(),
                        projection.getStampFrame()
                )
        );
    }
}
