package com.pheeeew.emotion.application.like.dto;

public record EmotionLikeResult(boolean liked, long likeCount) {

    public static EmotionLikeResult of(boolean liked, long likeCount) {
        return new EmotionLikeResult(liked, likeCount);
    }
}
