package com.pheeeew.collection.presentation.dto;

import com.pheeeew.common.exception.CommonErrorCode;
import com.pheeeew.common.exception.PheeeewException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.DateTimeException;
import java.time.LocalDate;

public record CollectionDailyRequest(
        @NotBlank
        @Pattern(regexp = "(?!0000)[0-9]{4}-[0-9]{2}-[0-9]{2}")
        @Schema(description = "필수 KST 날짜. 0001~9999년의 yyyy-MM-dd 형식이며 오늘·과거만 허용합니다.", example = "2026-11-06")
        String date,
        @Size(max = 2048)
        @Schema(description = "같은 날짜 상세의 emotions.nextCursor. 첫 페이지·새로고침에서는 생략하고 다음 페이지에도 date를 전달합니다.")
        String cursor
) {

    public LocalDate parsedDate() {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeException exception) {
            throw new PheeeewException(CommonErrorCode.INVALID_REQUEST, exception);
        }
    }
}
