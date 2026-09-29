package com.pheeeew.emotion.application.dto;

import java.util.List;

public record EmotionMapPageView(List<EmotionMapItemView> items, boolean hasNext, String nextCursor) {

    public static EmotionMapPageView of(List<EmotionMapItemView> items, boolean hasNext, String nextCursor) {
        return new EmotionMapPageView(List.copyOf(items), hasNext, nextCursor);
    }
}
