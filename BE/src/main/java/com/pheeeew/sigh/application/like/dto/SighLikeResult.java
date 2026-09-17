package com.pheeeew.sigh.application.like.dto;

public record SighLikeResult(boolean liked, long likeCount) {

    public static SighLikeResult of(boolean liked, long likeCount) {
        return new SighLikeResult(liked, likeCount);
    }
}
