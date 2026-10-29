package com.pheeeew.collection.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pheeeew.collection.application.dto.CollectionTodayResult;
import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public record CollectionTodayResponse(
        @Schema(description = "서버가 결정한 한국 시간 기준 오늘 날짜", example = "2026-10-22") LocalDate date,
        @Schema(description = "오늘 다섯 감정을 합한 스탬프 진행도. 수령 조건은 이 값과 presses로 판정합니다.")
        Progress stamps,
        @Schema(description = "오늘 다섯 감정을 합한 프레스 진행도. 수령 조건은 이 값과 stamps로 판정합니다.")
        Progress presses,
        @Schema(description = "오늘 이 기기가 어떤 감정이든 카드를 받았으면 true입니다. 카드는 하루에 한 장만 받을 수 있습니다.")
        boolean claimed,
        @Schema(description = "claimed가 false이고 최상위 stamps, presses의 remaining이 모두 0일 때만 true입니다. "
                + "true이면 다섯 감정 중 아무 state로 수령할 수 있습니다.")
        boolean claimable,
        @Schema(description = "다섯 감정별 진행도와 카드. 감정별 target, remaining은 참고용이며 수령 조건이 아닙니다. "
                + "오늘 받은 카드는 한 항목의 card에만 있습니다.")
        List<Item> items
) {

    public static CollectionTodayResponse from(CollectionTodayResult result) {
        return new CollectionTodayResponse(result.date(), Progress.from(result.stamps()),
                Progress.from(result.presses()), result.claimed(), result.claimable(),
                result.items().stream().map(Item::from).toList());
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
            @Schema(description = "해당 날짜의 누적 횟수. items에서는 그 감정, 최상위에서는 다섯 감정 합입니다. "
                    + "목표를 초과해도 실제 횟수를 반환합니다.") long count,
            @Schema(description = "목표 횟수. 스탬프는 1회, 프레스는 100회입니다.") long target,
            @Schema(description = "부족한 횟수. 목표 달성 또는 초과 시 0입니다.") long remaining
    ) {

        public static Progress from(CollectionTodayResult.Progress progress) {
            return new Progress(progress.count(), progress.target(), progress.remaining());
        }
    }
}
