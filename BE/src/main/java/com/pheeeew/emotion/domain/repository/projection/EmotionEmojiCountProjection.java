package com.pheeeew.emotion.domain.repository.projection;

public interface EmotionEmojiCountProjection {

    String getEmojiType();

    long getSelectionCount();

    boolean getSelected();
}
