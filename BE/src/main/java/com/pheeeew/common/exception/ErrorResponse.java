package com.pheeeew.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

public record ErrorResponse(
        @Schema(description = "도메인별 오류 코드. 클라이언트 분기는 이 값으로 합니다.", example = "COMMON-001")
        String code,
        @Schema(description = "사용자에게 보여줄 수 있는 한국어 설명", example = "요청 값이 올바르지 않습니다.")
        String message
) {

    public ErrorResponse {
        Objects.requireNonNull(code);
        Objects.requireNonNull(message);
    }

    public static ErrorResponse from(ErrorCode errorCode) {
        Objects.requireNonNull(errorCode);
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
    }
}
