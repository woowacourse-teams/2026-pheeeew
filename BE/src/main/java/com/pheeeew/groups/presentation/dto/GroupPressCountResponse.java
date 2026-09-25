package com.pheeeew.groups.presentation.dto;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.groups.application.dto.GroupPressCountResult;
import java.util.Map;

public record GroupPressCountResponse(Map<EmotionState, Long> counts, long total) {

    public static GroupPressCountResponse from(GroupPressCountResult result) {
        return new GroupPressCountResponse(result.counts(), result.total());
    }
}
