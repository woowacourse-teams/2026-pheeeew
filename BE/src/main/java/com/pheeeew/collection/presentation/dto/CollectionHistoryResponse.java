package com.pheeeew.collection.presentation.dto;

import com.pheeeew.collection.application.dto.CollectionHistoryResult;
import com.pheeeew.collection.domain.CollectionCardCounts;
import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

public record CollectionHistoryResponse(
        @Schema(description = "조회한 기기의 현재 닉네임. 미설정이면 null이며 카드에 저장된 이름이 아닙니다.", nullable = true)
        String nickname,
        @Schema(description = "현재 페이지의 수령 카드") List<CollectionCardDetailResponse> items,
        @Schema(description = "같은 날짜 범위의 전체·감정별 보유 장수. 감정 필터와 커서 위치에 영향받지 않습니다.")
        Counts counts,
        @Schema(description = "다음 페이지 존재 여부") boolean hasNext,
        @Schema(description = "다음 페이지 커서. 마지막 페이지는 null입니다.", nullable = true) String nextCursor
) {

    public static CollectionHistoryResponse from(CollectionHistoryResult result) {
        return new CollectionHistoryResponse(result.nickname(),
                result.items().stream().map(CollectionCardDetailResponse::from).toList(),
                Counts.from(result.counts()), result.hasNext(), result.nextCursor());
    }

    public record Counts(
            @Schema(description = "감정별 보유 장수의 합") long total,
            @Schema(description = "다섯 감정별 보유 장수. 없는 감정도 0으로 제공합니다.") Map<EmotionState, Long> byEmotion
    ) {

        public static Counts from(CollectionCardCounts counts) {
            return new Counts(counts.total(), counts.byEmotion());
        }
    }
}
