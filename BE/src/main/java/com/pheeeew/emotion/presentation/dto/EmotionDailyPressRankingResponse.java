package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.application.dto.EmotionDailyPressRankingItem;
import com.pheeeew.emotion.application.dto.EmotionDailyPressRankingResult;
import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record EmotionDailyPressRankingResponse(
        @Schema(description = "요청한 날짜. 요청한 값이 그대로 담겨 옵니다.", example = "2026-10-21")
        LocalDate date,
        @Schema(description = "집계 구간 시작. 그 날짜의 00:00 KST 입니다.", example = "2026-10-20T15:00:00Z")
        Instant startAt,
        @Schema(description = "집계 구간 끝. 다음 날 00:00 KST 이고 이 시각은 포함하지 않습니다.",
                example = "2026-10-21T15:00:00Z")
        Instant endAt,
        @Schema(description = "프레스 합 내림차순 상위 100명. 그날 한 번도 누르지 않은 기기는 담기지 않습니다. "
                + "데이터가 없는 날짜는 빈 배열입니다.")
        List<Item> items,
        @Schema(description = "요청한 기기의 순위. 상위 100명 안에 있어도 함께 내려주고, "
                + "100위 밖이어도 전국 전체를 매긴 실제 순위를 내려줍니다. "
                + "그날 한 번도 누르지 않았으면 null 이고 items 에도 나오지 않습니다.")
        Item me
) {
    public static EmotionDailyPressRankingResponse from(EmotionDailyPressRankingResult result) {
        return new EmotionDailyPressRankingResponse(
                result.date(),
                result.startAt(),
                result.endAt(),
                result.items().stream().map(Item::from).toList(),
                result.me() == null ? null : Item.from(result.me())
        );
    }

    @Schema(name = "EmotionDailyPressRankingItem", description = "일일 전체 프레스 랭킹 항목")
    public record Item(
            @Schema(description = "동점은 공동 순위입니다. 1, 2, 2, 4로 매깁니다.", example = "1")
            int rank,
            @Schema(description = "기기 닉네임. 닉네임을 정하지 않은 기기는 익명입니다.", example = "한숨많은곰")
            String nickname,
            @Schema(description = "요청한 날짜(KST)에 누른 다섯 감정의 개인 프레스 합계.", example = "412")
            long pressCount,
            @Schema(description = "요청한 날짜에 가장 많이 누른 감정. 공동 최다는 ANGRY, DISCOURAGED, "
                    + "EXHAUSTED, FRUSTRATED, IRRITATED 순으로 선택합니다.", example = "ANGRY")
            EmotionState representativeState
    ) {
        public static Item from(EmotionDailyPressRankingItem item) {
            return new Item(item.rank(), item.nickname(), item.pressCount(), item.representativeState());
        }
    }
}
