package com.pheeeew.groups.application.dto;

import com.pheeeew.emotion.domain.EmotionState;
import java.util.List;

public record GroupStatePressRankingGroup(EmotionState state, List<GroupRankingItem> items) {

    public static GroupStatePressRankingGroup of(EmotionState state, List<GroupRankingItem> items) {
        return new GroupStatePressRankingGroup(state, List.copyOf(items));
    }
}
