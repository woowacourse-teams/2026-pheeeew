package com.pheeeew.device.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record DeviceV3CreateRequest(
        @NotNull(message = "요청 식별자는 필수입니다.")
        @Schema(description = "등록 재시도의 멱등 키입니다. 등록 후 5분간 자격증명처럼 보호하고 성공하면 폐기합니다.",
                example = "550e8400-e29b-41d4-a716-446655440000")
        UUID requestId,

        @NotNull(message = "닉네임은 필수입니다.")
        @Schema(description = "앞뒤 공백을 제거한 뒤 한글, 영문, 공백으로 1~10자여야 합니다. "
                + "익명은 사용할 수 없으며 영문 대소문자를 구분하지 않고 중복을 확인합니다.",
                example = "스타크", requiredMode = Schema.RequiredMode.REQUIRED)
        String nickname,

        @NotNull(message = "무결성 증명 정보는 필수입니다.")
        @Valid
        @Schema(description = "platform은 필수이며 이 값만 저장합니다. token을 보낸 경우 challenge도 필수입니다.")
        DeviceAttestationRequest attestation
) {
}
