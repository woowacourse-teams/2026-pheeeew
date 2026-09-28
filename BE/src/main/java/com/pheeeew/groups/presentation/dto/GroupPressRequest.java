package com.pheeeew.groups.presentation.dto;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.groups.application.dto.GroupPressCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record GroupPressRequest(
        @Schema(description = "1~5개 입력. 같은 감정은 합산하며 전체 count 합계는 최대 100회입니다.")
        @NotNull(message = "감정 입력 목록은 필수입니다.")
        @Size(min = 1, max = 5, message = "감정 입력은 1개 이상 5개 이하여야 합니다.")
        List<@NotNull @Valid Press> presses
) {
    public GroupPressCommand toCommand() {
        Map<EmotionState, Integer> counts = new EnumMap<>(EmotionState.class);
        for (Press press : presses) {
            counts.merge(press.state(), press.count().intValueExact(), Integer::sum);
        }

        return GroupPressCommand.from(counts);
    }

    public record Press(
            @NotNull(message = "감정 상태는 필수입니다.")
            EmotionState state,

            @Schema(description = "이번 요청에서 추가할 횟수. 현재 총합이 아닌 1~100 사이의 정수입니다.",
                    type = "integer", format = "int32", minimum = "1", maximum = "100")
            @NotNull(message = "누른 횟수는 필수입니다.")
            @Min(value = 1, message = "누른 횟수는 1회 이상이어야 합니다.")
            @Max(value = GroupPressCommand.MAX_PRESS_COUNT, message = "누른 횟수는 100회 이하여야 합니다.")
            @Digits(integer = 3, fraction = 0, message = "누른 횟수는 정수여야 합니다.")
            BigDecimal count
    ) {
    }
}
