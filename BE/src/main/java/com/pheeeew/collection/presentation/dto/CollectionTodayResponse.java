package com.pheeeew.collection.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pheeeew.collection.application.dto.CollectionTodayResult;
import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public record CollectionTodayResponse(
        @Schema(description = "서버가 결정한 한국 시간 기준 오늘 날짜", example = "2026-10-22") LocalDate date,
        @Schema(description = "활동 여부와 관계없이 다섯 감정을 모두 포함하는 진행도") List<Item> items
) {

    public static CollectionTodayResponse from(CollectionTodayResult result) {
        return new CollectionTodayResponse(result.date(), result.items().stream().map(Item::from).toList());
    }

    @Schema(name = "CollectionTodayItem")
    public record Item(
            EmotionState state,
            Progress stamps,
            Progress presses,
            @JsonInclude(JsonInclude.Include.ALWAYS)
            @Schema(description = "발급된 카드 정보. 미발급 상태이면 null입니다.", nullable = true)
            CollectionCardResponse card
    ) {

        public static Item from(CollectionTodayResult.Item item) {
            return new Item(item.state(), Progress.from(item.stamps()), Progress.from(item.presses()),
                    item.card() == null ? null : CollectionCardResponse.from(item.card()));
        }
    }

    @Schema(name = "CollectionActivityProgress")
    public record Progress(
            @Schema(description = "해당 날짜와 감정의 누적 횟수. 목표를 초과해도 실제 횟수를 반환합니다.") long count,
            @Schema(description = "목표 횟수. 스탬프는 1회, 프레스는 100회입니다.") long target,
            @Schema(description = "부족한 횟수. 목표 달성 또는 초과 시 0입니다.") long remaining
    ) {

        public static Progress from(CollectionTodayResult.Progress progress) {
            return new Progress(progress.count(), progress.target(), progress.remaining());
        }
    }
}
