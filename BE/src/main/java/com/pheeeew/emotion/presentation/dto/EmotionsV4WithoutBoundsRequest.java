package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

@Schema(name = "EmotionsV4WithoutBoundsRequest")
public record EmotionsV4WithoutBoundsRequest(
        @Schema(description = "다음 페이지 조회에 사용할 서버 발급 커서. 첫 페이지에서는 생략합니다.", example = "opaque-cursor")
        String cursor,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "KST 기준 시작일(포함). 종료일과 함께 지정하며 둘 다 생략하면 전체 기간입니다.", example = "2026-10-01")
        LocalDate startDate,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "KST 기준 종료일(포함). 시작일 이상이어야 합니다.", example = "2026-10-11")
        LocalDate endDate,

        @Schema(description = "내용 필터. 첫 페이지에서 생략하면 ALL이며 NONE은 항상 제외합니다.")
        EmotionContentFilter contentType,

        @Schema(description = "조회 범위. ALL·MY만 허용하며 첫 페이지에서 생략하면 ALL입니다. 다음 페이지에서 생략하면 커서의 조건을 유지합니다.", allowableValues = {"ALL", "MY"})
        EmotionSearchScope scope
) {

    @AssertTrue(message = "0001~9999년의 시작일과 종료일을 함께 전달하고, 시작일은 종료일 이하여야 합니다.")
    @Schema(hidden = true)
    public boolean isDateRangeValid() {
        return EmotionSearchFilter.isDateRangeValid(startDate, endDate);
    }

    @AssertTrue(message = "v4는 ALL·MY 범위만 허용합니다.")
    @Schema(hidden = true)
    public boolean isScopeValid() {
        return scope != EmotionSearchScope.GROUP;
    }
}
