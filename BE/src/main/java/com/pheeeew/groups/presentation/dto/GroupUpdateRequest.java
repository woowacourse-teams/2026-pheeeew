package com.pheeeew.groups.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GroupUpdateRequest(
        @NotBlank(message = "그룹 이름은 필수입니다.")
        @Size(min = 2, max = 10, message = "그룹 이름은 2자 이상 10자 이하여야 합니다.")
        String name,

        @Size(max = 100, message = "그룹 설명은 100자 이하여야 합니다.")
        String description,

        @Valid
        @NotNull(message = "스탬프는 필수입니다.")
        GroupStampRequest stamp
) {
    public GroupUpdateRequest {
        name = name == null ? null : name.strip();
        description = description == null || description.isBlank() ? null : description.strip();
    }
}
