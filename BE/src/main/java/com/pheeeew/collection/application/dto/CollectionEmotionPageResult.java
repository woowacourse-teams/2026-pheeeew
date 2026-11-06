package com.pheeeew.collection.application.dto;

import java.util.List;

public record CollectionEmotionPageResult(List<CollectionEmotionResult> items, boolean hasNext, String nextCursor) {

    public static CollectionEmotionPageResult of(List<CollectionEmotionResult> items, boolean hasNext, String nextCursor) {
        return new CollectionEmotionPageResult(items, hasNext, nextCursor);
    }
}
