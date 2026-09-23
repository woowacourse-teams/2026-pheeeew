package com.pheeeew.sigh.application.dto;

import java.util.List;

public record SighListResult(List<EmotionDetailResult> items, boolean hasNext, String nextCursor) {

    public static SighListResult of(List<EmotionDetailResult> items, boolean hasNext, String nextCursor) {
        return new SighListResult(items, hasNext, nextCursor);
    }
}
