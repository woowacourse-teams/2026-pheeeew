package com.pheeeew.notification.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

public record PushTokenUpdateRequest(
        @NotBlank(message = "푸시 등록 값은 필수입니다.")
        @Size(max = 1024, message = "푸시 등록 값은 1024자를 초과할 수 없습니다.")
        @Schema(description = "Firebase SDK 가 준 푸시 등록 값입니다. FID 와 기존 등록 토큰을 모두 받고 형식을 해석하지 않습니다. "
                + "앞뒤 공백을 지우지 않습니다. 공백만으로 된 값은 허용하지 않습니다. "
                + "이 API 가 지원하는 상한은 UTF-8 1024바이트이며 널 문자를 포함할 수 없습니다.",
                example = "cA1b2C3d4E5f6G7h8I9j0K", maxLength = 1024, requiredMode = Schema.RequiredMode.REQUIRED)
        String token
) {

    private static final int MAX_TOKEN_BYTES = 1024;
    private static final char NULL_CHARACTER = '\u0000';

    @AssertTrue(message = "푸시 등록 값은 UTF-8 1024바이트 이하이고 널 문자를 포함할 수 없습니다.")
    @Schema(hidden = true)
    public boolean isTokenStorable() {
        if (token == null) {
            return true;
        }

        return token.getBytes(StandardCharsets.UTF_8).length <= MAX_TOKEN_BYTES
                && token.indexOf(NULL_CHARACTER) < 0;
    }
}
