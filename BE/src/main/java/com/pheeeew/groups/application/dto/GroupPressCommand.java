package com.pheeeew.groups.application.dto;

import com.pheeeew.emotion.domain.EmotionState;
import java.util.Map;

public record GroupPressCommand(Map<EmotionState, Integer> counts, boolean bundled) {

    public static GroupPressCommand of(Map<EmotionState, Integer> counts, boolean bundled) {
        return new GroupPressCommand(counts, bundled);
    }
}
