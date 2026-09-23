package com.pheeeew.emotion.application.dto;

import java.util.List;

public record EmotionListResult(List<EmotionDetailResult> items, boolean hasNext, String nextCursor) {

    public static EmotionListResult of(List<EmotionDetailResult> items, boolean hasNext, String nextCursor) {
        return new EmotionListResult(items, hasNext, nextCursor);
    }
}
