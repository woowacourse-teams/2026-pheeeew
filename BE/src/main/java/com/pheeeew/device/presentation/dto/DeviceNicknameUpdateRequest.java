package com.pheeeew.device.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record DeviceNicknameUpdateRequest(
        @NotNull(message = "닉네임은 필수입니다.")
        @Schema(description = "앞뒤 공백을 제거한 뒤 한글, 영문, 공백으로 1~10자여야 합니다. "
                + "익명은 사용할 수 없으며 영문 대소문자를 구분하지 않고 중복을 확인합니다.",
                example = "스타크", requiredMode = Schema.RequiredMode.REQUIRED)
        String nickname
) {
}
