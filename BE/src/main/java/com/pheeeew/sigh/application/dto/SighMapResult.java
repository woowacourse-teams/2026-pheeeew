package com.pheeeew.sigh.application.dto;

import java.util.List;

public record SighMapResult(List<EmotionMapItem> sighs, boolean truncated) {

    public static SighMapResult of(List<EmotionMapItem> sighs, boolean truncated) {
        return new SighMapResult(sighs, truncated);
    }
}
