package com.pheeeew.groups.presentation.dto;

import com.pheeeew.groups.domain.StampFrame;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GroupStampRequest(
        @Schema(
                description = "스탬프에 찍히는 글자. 앞뒤 공백은 서버가 지웁니다.",
                minLength = 1, maxLength = 4, example = "한숨"
        )
        @NotBlank(message = "스탬프 글자는 필수입니다.")
        @Size(min = 1, max = 4, message = "스탬프 글자는 1자 이상 4자 이하여야 합니다.")
        String text,

        @Schema(
                description = "글자 색. #RRGGBB 또는 #RRGGBBAA 입니다.",
                pattern = "^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$", example = "#FFFFFF"
        )
        @NotBlank(message = "글자 색은 필수입니다.")
        @Pattern(regexp = "^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$", message = "글자 색은 #RRGGBB 또는 #RRGGBBAA 형식이어야 합니다.")
        String textColor,

        @Schema(
                description = "배경 색. #RRGGBB 또는 #RRGGBBAA 입니다.",
                pattern = "^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$", example = "#2B2B2B"
        )
        @NotBlank(message = "배경 색은 필수입니다.")
        @Pattern(regexp = "^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$", message = "배경 색은 #RRGGBB 또는 #RRGGBBAA 형식이어야 합니다.")
        String backgroundColor,

        @Schema(description = "스탬프 테두리 모양", example = "SCALLOP")
        @NotNull(message = "스탬프 틀은 필수입니다.")
        StampFrame frame
) {
    public GroupStampRequest {
        text = text == null ? null : text.strip();
    }
}
