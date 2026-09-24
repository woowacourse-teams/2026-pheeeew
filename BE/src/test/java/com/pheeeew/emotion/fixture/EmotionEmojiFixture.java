package com.pheeeew.emotion.fixture;

import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.domain.EmotionEmoji;

public final class EmotionEmojiFixture {

    private EmotionEmojiFixture() {
    }

    public static EmotionEmoji.EmotionEmojiBuilder 기본_이모지_빌더() {
        return EmotionEmoji.builder()
                .emotionId(42L)
                .deviceId(1L)
                .emojiType(EmojiType.HEART);
    }
}
