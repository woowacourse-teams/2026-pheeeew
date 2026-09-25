package com.pheeeew.groups.application.dto;

import com.pheeeew.groups.domain.Group;
import java.util.UUID;

public record GroupPreviewResult(
        UUID publicId,
        String name,
        String description,
        long memberCount,
        GroupStampResult stamp
) {

    public static GroupPreviewResult of(Group group, long memberCount, GroupStampResult stamp) {
        return new GroupPreviewResult(
                group.getPublicId(),
                group.getName(),
                group.getDescription(),
                memberCount,
                stamp
        );
    }
}
