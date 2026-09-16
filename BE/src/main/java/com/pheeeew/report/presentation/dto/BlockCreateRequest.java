package com.pheeeew.report.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BlockCreateRequest(
        @NotNull(message = "차단 대상 한숨 식별자는 필수입니다.")
        @Positive(message = "한숨 식별자는 양수여야 합니다.")
        @Schema(
                description = """
                        차단 대상을 가리키는 한숨 ID입니다.

                        무엇이 차단되는지는 엔드포인트마다 다릅니다. 각 API 설명을 확인합니다.
                        """,
                minimum = "1",
                example = "42"
        )
        Long sighId
) {
}
