package com.pheeeew.groups.presentation.dto;

import com.pheeeew.groups.application.dto.GroupStampItemResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record GroupStampItemResponse(
        @Schema(description = "그룹의 공개 식별자") UUID groupId,
        @Schema(description = "그룹 이름") String name,
        @Schema(description = "그룹의 현재 스탬프") GroupStampResponse stamp
) {

    public static GroupStampItemResponse from(GroupStampItemResult result) {
        return new GroupStampItemResponse(result.publicId(), result.name(), GroupStampResponse.from(result.stamp()));
    }
}
