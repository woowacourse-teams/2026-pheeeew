package com.pheeeew.groups.presentation.dto;

import com.pheeeew.groups.application.dto.GroupPreviewResult;
import java.util.UUID;

public record GroupPreviewResponse(
        UUID groupId,
        String name,
        String description,
        long memberCount,
        GroupStampResponse stamp
) {

    public static GroupPreviewResponse from(GroupPreviewResult result) {
        return new GroupPreviewResponse(
                result.publicId(),
                result.name(),
                result.description(),
                result.memberCount(),
                GroupStampResponse.from(result.stamp())
        );
    }
}
