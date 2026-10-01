package com.pheeeew.groups.application.dto;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.groups.exception.GroupErrorCode;
import com.pheeeew.groups.exception.GroupException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

public record GroupPressCommand(Map<EmotionState, Integer> counts) {

    public static final int MAX_PRESS_COUNT = 100;

    public GroupPressCommand {
        if (counts == null || counts.isEmpty()
                || counts.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || entry.getValue() == null || entry.getValue() <= 0)
                || counts.values().stream().mapToLong(Integer::longValue).sum() > MAX_PRESS_COUNT) {
            throw new GroupException(GroupErrorCode.GROUP_PRESS_INVALID);
        }

        // 입력 순서가 다른 동시 요청도 감정 행을 같은 순서로 잠근다.
        Map<EmotionState, Integer> sorted = new TreeMap<>(Comparator.comparing(EmotionState::name));
        sorted.putAll(counts);
        counts = Collections.unmodifiableMap(sorted);
    }

    public static GroupPressCommand from(Map<EmotionState, Integer> counts) {
        return new GroupPressCommand(counts);
    }
}
