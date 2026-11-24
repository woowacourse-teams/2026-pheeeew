package com.pheeeew.emotion.application.dto;

import java.util.List;

/** clusters가 비어 있지 않으면 핀 대신 셀 묶음으로 응답한 화면이다. */
public record EmotionMapPageView(List<EmotionMapItemView> items, boolean hasNext, String nextCursor,
                                 List<EmotionMapClusterView> clusters) {

    public static EmotionMapPageView of(List<EmotionMapItemView> items, boolean hasNext, String nextCursor) {
        return new EmotionMapPageView(List.copyOf(items), hasNext, nextCursor, List.of());
    }

    public static EmotionMapPageView clustered(List<EmotionMapClusterView> clusters) {
        return new EmotionMapPageView(List.of(), false, null, List.copyOf(clusters));
    }
}
