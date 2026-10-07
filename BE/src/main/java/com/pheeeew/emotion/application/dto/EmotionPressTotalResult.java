package com.pheeeew.emotion.application.dto;

import java.time.LocalDate;

public record EmotionPressTotalResult(LocalDate pressDate, long total) {

    public static EmotionPressTotalResult of(LocalDate pressDate, long total) {
        return new EmotionPressTotalResult(pressDate, total);
    }
}
