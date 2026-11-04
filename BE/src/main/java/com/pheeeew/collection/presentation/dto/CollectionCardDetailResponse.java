package com.pheeeew.collection.presentation.dto;

import com.pheeeew.collection.application.dto.CollectionCardDetailResult;
import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

public record CollectionCardDetailResponse(
        @Schema(description = "수령한 카드의 공개 식별자") UUID cardId,
        @Schema(description = "카드의 한국 시간 기준 활동 날짜") LocalDate date,
        EmotionState state,
        @Schema(description = "조회 시점에 저장된 카드 소유 기기·해당 날짜의 모든 감정 누적 스탬프 합계") long stampCount,
        @Schema(description = "조회 시점에 저장된 카드 소유 기기·해당 날짜의 모든 감정 누적 프레스 합계") long pressCount,
        CollectionCardResponse.Images images
) {

    public static CollectionCardDetailResponse from(CollectionCardDetailResult result) {
        return new CollectionCardDetailResponse(result.cardId(), result.date(), result.state(),
                result.stampCount(), result.pressCount(), CollectionCardResponse.Images.from(result.images()));
    }
}
