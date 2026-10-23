package com.pheeeew.collection.presentation.dto;

import com.pheeeew.collection.application.dto.CollectionCardResult;
import com.pheeeew.collection.application.dto.CollectionCardImages;
import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

public record CollectionCardResponse(
        @Schema(description = "수령한 카드의 공개 식별자") UUID cardId,
        @Schema(description = "카드의 한국 시간 기준 활동 날짜") LocalDate date,
        EmotionState state,
        @Schema(description = "카드에 고정된 캐릭터·원형 도장·일자형 도장의 이미지 URL") Images images
) {

    public static CollectionCardResponse from(CollectionCardResult result) {
        return new CollectionCardResponse(result.cardId(), result.date(), result.state(),
                Images.from(result.images()));
    }

    public record Images(
            @Schema(description = "캐릭터 PNG URL") String characterUrl,
            @Schema(description = "원형 도장 PNG URL") String roundStampUrl,
            @Schema(description = "일자형 도장 PNG URL") String straightStampUrl
    ) {

        public static Images from(CollectionCardImages images) {
            return new Images(images.characterUrl(), images.roundStampUrl(), images.straightStampUrl());
        }
    }
}
