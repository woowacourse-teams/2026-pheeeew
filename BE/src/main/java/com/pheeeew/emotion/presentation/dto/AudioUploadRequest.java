package com.pheeeew.emotion.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record AudioUploadRequest(
        @NotNull @Pattern(regexp = "audio/mp4", message = "녹음 형식은 audio/mp4여야 합니다.")
        @Schema(description = "녹음 파일의 MIME 유형입니다. S3 PUT의 Content-Type에도 같은 값을 사용합니다.",
                allowableValues = {"audio/mp4"}, example = "audio/mp4")
        String contentType,

        @NotNull @Positive @Max(5_242_880)
        @Schema(description = "녹음 파일 본문의 바이트 수입니다. 1바이트 이상, 최대 5MiB(5,242,880바이트)입니다.",
                minimum = "1", maximum = "5242880", example = "102400")
        Long contentLength
) {
}
