package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.application.dto.EmotionPressRankingItem;
import com.pheeeew.emotion.application.dto.EmotionPressRankingResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record EmotionPressRankingResponse(
        @Schema(description = "요청한 몇 주 전. 0 이 이번 주이고 520 까지 요청할 수 있습니다.", example = "0")
        int weeksAgo,
        @Schema(description = "집계 구간 시작. 월요일 00:00 KST 입니다.", example = "2026-10-05T15:00:00Z")
        Instant startAt,
        @Schema(description = "집계 구간 끝. 이 시각은 포함하지 않습니다.", example = "2026-10-12T15:00:00Z")
        Instant endAt,
        @Schema(description = "프레스 합 내림차순 상위 100명. 그 주에 한 번도 누르지 않은 기기는 담기지 않습니다.")
        List<Item> items,
        @Schema(description = "요청한 기기의 순위. 상위 100명 안에 있어도 함께 내려주고, "
                + "100위 밖이어도 전국 전체를 매긴 실제 순위를 내려줍니다. "
                + "그 주에 한 번도 누르지 않았으면 null 이고 items 에도 나오지 않습니다.")
        Item me
) {
    public static EmotionPressRankingResponse from(EmotionPressRankingResult result) {
        return new EmotionPressRankingResponse(
                result.weeksAgo(),
                result.startAt(),
                result.endAt(),
                result.items().stream().map(Item::from).toList(),
                result.me() == null ? null : Item.from(result.me())
        );
    }

    @Schema(name = "EmotionPressRankingItem", description = "프레스 랭킹 항목")
    public record Item(
            @Schema(description = "동점은 공동 순위입니다. 1, 2, 2, 4 로 매깁니다.", example = "1")
            int rank,
            @Schema(
                    description = "기기 닉네임. 항상 값이 있습니다. 닉네임을 정하지 않은 기기는 익명 입니다. "
                            + "지도에 남긴 감정의 익명 선택과는 무관합니다.",
                    example = "한숨많은곰"
            )
            String nickname,
            @Schema(
                    description = "그 주에 누른 개인 감정 프레스의 합. 감정을 구분하지 않은 전체 합계입니다.",
                    example = "412"
            )
            long pressCount
    ) {
        public static Item from(EmotionPressRankingItem item) {
            return new Item(item.rank(), item.nickname(), item.pressCount());
        }
    }
}
