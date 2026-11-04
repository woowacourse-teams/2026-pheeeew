package com.pheeeew.collection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CollectionCalendarRequest(
        @NotBlank
        @Pattern(regexp = "(?!0000)[0-9]{4}-(0[1-9]|1[0-2])")
        @Schema(description = "한국 시간 기준 조회 월. 0001~9999년의 yyyy-MM 형식", example = "2026-11")
        String month
) {
}
