package com.pheeeew.device.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record DeviceCreateRequest(
        @NotNull(message = "요청 식별자는 필수입니다.")
        @Schema(
                description = """
                        중복 등록을 막는 멱등 키입니다.

                        새 등록마다 새 UUID를 만들고, 같은 등록의 재시도에는 같은 값을 재사용합니다.

                        등록 후 5분 동안은 이 값만으로 그 기기의 토큰을 다시 받을 수 있습니다.
                        그동안은 자격증명처럼 다뤄야 하므로 로그에 남기거나 외부에 넘기지 않습니다.
                        등록에 성공하면 앱은 이 값을 즉시 폐기합니다.
                        """,
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        UUID requestId,

        @NotNull(message = "무결성 증명 정보는 필수입니다.")
        @Valid
        @Schema(
                description = """
                        무결성 증명 정보입니다. `platform`만 저장합니다.

                        `token`은 보낸 경우에만 검증하며, 보내면 `challenge`도 필수입니다.
                        """
        )
        DeviceAttestationRequest attestation
) {
}
