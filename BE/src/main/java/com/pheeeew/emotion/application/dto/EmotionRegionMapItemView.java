package com.pheeeew.emotion.application.dto;

import com.pheeeew.region.domain.Region;

public record EmotionRegionMapItemView(Region region, RegionEmotionSummary summary) {

    public static EmotionRegionMapItemView of(Region region, RegionEmotionSummary summary) {
        return new EmotionRegionMapItemView(region, summary);
    }
}
