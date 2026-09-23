package com.pheeeew.groups.presentation.dto;

import com.pheeeew.groups.domain.StampFrame;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GroupStampRequest(
        @NotBlank(message = "스탬프 글자는 필수입니다.")
        @Size(min = 2, max = 4, message = "스탬프 글자는 2자 이상 4자 이하여야 합니다.")
        String text,

        @NotBlank(message = "글자 색은 필수입니다.")
        @Pattern(regexp = "^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$", message = "글자 색은 #RRGGBB 또는 #RRGGBBAA 형식이어야 합니다.")
        String textColor,

        @NotBlank(message = "배경 색은 필수입니다.")
        @Pattern(regexp = "^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$", message = "배경 색은 #RRGGBB 또는 #RRGGBBAA 형식이어야 합니다.")
        String backgroundColor,

        @NotNull(message = "스탬프 틀은 필수입니다.")
        StampFrame frame
) {
    public GroupStampRequest {
        text = text == null ? null : text.strip();
    }
}
