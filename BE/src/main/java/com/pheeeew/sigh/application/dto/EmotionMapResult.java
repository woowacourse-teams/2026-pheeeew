package com.pheeeew.sigh.application.dto;

import java.util.List;

public record EmotionMapResult(List<EmotionMapItem> emotions, boolean truncated) {

    public static EmotionMapResult of(List<EmotionMapItem> emotions, boolean truncated) {
        return new EmotionMapResult(emotions, truncated);
    }
}
