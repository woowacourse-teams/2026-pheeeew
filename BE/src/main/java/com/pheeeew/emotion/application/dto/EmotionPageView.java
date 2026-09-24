package com.pheeeew.emotion.application.dto;

import java.util.List;

public record EmotionPageView(List<EmotionDetailView> items, boolean hasNext, String nextCursor) {

    public static EmotionPageView of(List<EmotionDetailView> items, boolean hasNext, String nextCursor) {
        return new EmotionPageView(List.copyOf(items), hasNext, nextCursor);
    }
}
