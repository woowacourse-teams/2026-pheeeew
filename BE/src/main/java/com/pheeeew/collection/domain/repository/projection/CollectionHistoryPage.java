package com.pheeeew.collection.domain.repository.projection;

import com.pheeeew.collection.domain.CollectionCardCounts;
import java.util.List;

public record CollectionHistoryPage(List<CollectionHistoryCard> cards, CollectionCardCounts counts) {

    public static CollectionHistoryPage of(List<CollectionHistoryCard> cards, CollectionCardCounts counts) {
        return new CollectionHistoryPage(List.copyOf(cards), counts);
    }
}
