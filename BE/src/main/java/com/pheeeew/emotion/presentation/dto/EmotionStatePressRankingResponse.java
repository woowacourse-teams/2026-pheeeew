package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.application.dto.EmotionStatePressRankingResult;
import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record EmotionStatePressRankingResponse(
        @Schema(description = "조회한 감정. 요청한 값이 그대로 담겨 옵니다.", example = "ANGRY")
        EmotionState state,
        @Schema(description = "요청한 몇 주 전. 0 이 이번 주이고 520 까지 요청할 수 있습니다.", example = "0")
        int weeksAgo,
        @Schema(description = "집계 구간 시작. 월요일 00:00 KST 입니다.", example = "2026-10-05T15:00:00Z")
        Instant startAt,
        @Schema(description = "집계 구간 끝. 이 시각은 포함하지 않습니다.", example = "2026-10-12T15:00:00Z")
        Instant endAt,
        @Schema(description = "그 감정의 프레스 합 내림차순 상위 100명. "
                + "그 주에 그 감정을 한 번도 누르지 않은 기기는 담기지 않습니다.")
        List<EmotionPressRankingResponse.Item> items,
        @Schema(description = "요청한 기기의 순위. 상위 100명 안에 있어도 함께 내려주고, "
                + "100위 밖이어도 전국 전체를 매긴 실제 순위를 내려줍니다. "
                + "그 주에 그 감정을 한 번도 누르지 않았으면 null 이고 items 에도 나오지 않습니다.")
        EmotionPressRankingResponse.Item me
) {
    public static EmotionStatePressRankingResponse from(EmotionStatePressRankingResult result) {
        return new EmotionStatePressRankingResponse(
                result.state(),
                result.weeksAgo(),
                result.startAt(),
                result.endAt(),
                result.items().stream().map(EmotionPressRankingResponse.Item::from).toList(),
                result.me() == null ? null : EmotionPressRankingResponse.Item.from(result.me())
        );
    }
}
