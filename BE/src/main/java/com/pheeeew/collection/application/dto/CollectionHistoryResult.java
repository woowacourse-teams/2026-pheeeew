package com.pheeeew.collection.application.dto;

import java.util.List;

public record CollectionHistoryResult(List<CollectionCardDetailResult> items, boolean hasNext, String nextCursor) {

    public static CollectionHistoryResult of(List<CollectionCardDetailResult> items, boolean hasNext, String nextCursor) {
        return new CollectionHistoryResult(List.copyOf(items), hasNext, nextCursor);
    }
}
