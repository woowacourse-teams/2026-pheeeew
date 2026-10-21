package com.pheeeew.emotion.application.mapcache;

public interface EmotionMapCacheListener {

    void changed(EmotionMapContribution before, EmotionMapContribution after);

    void invalidate();

    default boolean rebuild() {
        return false;
    }
}
