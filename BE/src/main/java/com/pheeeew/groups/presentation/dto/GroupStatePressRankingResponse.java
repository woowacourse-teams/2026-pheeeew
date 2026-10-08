package com.pheeeew.groups.presentation.dto;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.groups.application.dto.GroupStatePressRankingResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record GroupStatePressRankingResponse(
        @Schema(description = "조회한 감정. 요청한 값이 그대로 담겨 옵니다.", example = "ANGRY")
        EmotionState state,
        @Schema(description = "요청한 몇 주 전. 0 이 이번 주입니다.", example = "0")
        int weeksAgo,
        @Schema(description = "집계 구간 시작. 월요일 00:00 KST 입니다.", example = "2026-10-05T15:00:00Z")
        Instant startAt,
        @Schema(description = "집계 구간 끝. 이 시각은 포함하지 않습니다.", example = "2026-10-12T15:00:00Z")
        Instant endAt,
        @Schema(
                description = "이 구간보다 이전에 누른 기록이 있는지. 감정을 가리지 않습니다.",
                example = "true"
        )
        boolean hasPrevious,
        @Schema(
                description = "그 감정의 점수 내림차순. "
                        + "아무도 그 감정을 누르지 않은 주에는 빈 배열이고 오류가 아닙니다."
        )
        List<GroupPressRankingResponse.Item> items
) {
    public static GroupStatePressRankingResponse from(GroupStatePressRankingResult result) {
        return new GroupStatePressRankingResponse(
                result.state(),
                result.weeksAgo(),
                result.startAt(),
                result.endAt(),
                result.hasPrevious(),
                result.items().stream().map(GroupPressRankingResponse.Item::from).toList()
        );
    }
}
