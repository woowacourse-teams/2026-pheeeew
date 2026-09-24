package com.pheeeew.emotion.application.emoji.dto;

import com.pheeeew.emotion.domain.EmojiType;

public record EmotionEmojiResult(EmojiType type, long count, boolean selected) {

    public static EmotionEmojiResult of(EmojiType type, long count, boolean selected) {
        return new EmotionEmojiResult(type, count, selected);
    }
}
