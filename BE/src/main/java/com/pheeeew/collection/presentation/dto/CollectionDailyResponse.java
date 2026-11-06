package com.pheeeew.collection.presentation.dto;

import com.pheeeew.collection.application.dto.CollectionDailyResult;
import com.pheeeew.common.presentation.dto.CursorResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record CollectionDailyResponse(
        @Schema(description = "조회한 KST 날짜") LocalDate date,
        @Schema(description = "해당 날짜에 본인이 수령한 카드. 미발급이면 null", nullable = true)
        CollectionCardDetailResponse card,
        @Schema(description = "조회 시점에 저장된 본인·해당 날짜의 모든 감정 프레스 합계. 기록이 없으면 0", minimum = "0")
        long pressCount,
        CursorResponse<CollectionEmotionResponse> emotions
) {

    public static CollectionDailyResponse from(CollectionDailyResult result) {
        var page = result.emotions();
        var items = page.items().stream().map(CollectionEmotionResponse::from).toList();
        return new CollectionDailyResponse(result.date(),
                result.card() == null ? null : CollectionCardDetailResponse.from(result.card()), result.pressCount(),
                CursorResponse.of(items, page.hasNext(), page.nextCursor()));
    }
}
