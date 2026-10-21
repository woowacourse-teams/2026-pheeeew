package com.pheeeew.emotion.domain.repository.projection;

import java.util.Set;

public record EmotionMapBlocks(Set<Long> emotions, Set<Long> authors) {

    public static EmotionMapBlocks of(Set<Long> emotions, Set<Long> authors) {
        return new EmotionMapBlocks(emotions, authors);
    }
}
