package com.pheeeew.groups.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GroupCreateRequest(
        @Schema(
                description = "그룹 이름. 앞뒤 공백은 서버가 지웁니다. 이미 쓰이는 이름이면 409 입니다.",
                minLength = 2, maxLength = 10, example = "한숨모임"
        )
        @NotBlank(message = "그룹 이름은 필수입니다.")
        @Size(min = 2, max = 10, message = "그룹 이름은 2자 이상 10자 이하여야 합니다.")
        String name,

        @Schema(
                description = "그룹 설명. 유일하게 생략할 수 있는 값입니다. "
                        + "비우거나 공백만 보내면 서버가 null 로 저장하므로, 지울 때는 빈 문자열을 보냅니다.",
                maxLength = 100, nullable = true, example = "퇴근하고 한숨 쉬는 모임"
        )
        @Size(max = 100, message = "그룹 설명은 100자 이하여야 합니다.")
        String description,

        @Schema(description = "그룹 스탬프. 네 값을 모두 보내야 하며 일부만 바꿀 수 없습니다.")
        @Valid
        @NotNull(message = "스탬프는 필수입니다.")
        GroupStampRequest stamp
) {
    public GroupCreateRequest {
        name = name == null ? null : name.strip();
        description = description == null || description.isBlank() ? null : description.strip();
    }
}
