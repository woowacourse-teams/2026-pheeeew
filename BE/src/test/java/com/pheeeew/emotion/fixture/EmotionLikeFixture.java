package com.pheeeew.emotion.fixture;

import com.pheeeew.emotion.domain.EmotionLike;

public final class EmotionLikeFixture {

    private EmotionLikeFixture() {
    }

    public static EmotionLike.EmotionLikeBuilder 기본_좋아요_빌더() {
        return EmotionLike.builder()
                .emotionId(42L)
                .deviceId(1L);
    }
}
