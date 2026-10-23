package com.pheeeew.collection.application.dto;

import com.pheeeew.collection.domain.CollectionCardCounts;
import java.util.List;

public record CollectionHistoryResult(
        List<CollectionCardDetailResult> items, boolean hasNext, String nextCursor, CollectionCardCounts counts, String nickname
) {

    public static CollectionHistoryResult of(
            List<CollectionCardDetailResult> items, boolean hasNext, String nextCursor, CollectionCardCounts counts, String nickname
    ) {
        return new CollectionHistoryResult(List.copyOf(items), hasNext, nextCursor, counts, nickname);
    }
}
