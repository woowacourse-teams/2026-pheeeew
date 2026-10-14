package com.pheeeew.groups.presentation.dto;

import com.pheeeew.groups.domain.GroupJoinType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record GroupJoinTypeUpdateRequest(
        @Schema(
                description = "바꿀 가입 방식. PUBLIC 이면 초대 코드 없이 누구나 가입할 수 있고, "
                        + "PRIVATE 이면 그 그룹의 초대 코드로만 가입할 수 있습니다. "
                        + "생성 요청과 달리 생략할 수 없습니다.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "PUBLIC"
        )
        @NotNull(message = "가입 방식은 필수입니다.")
        GroupJoinType joinType
) {
}
