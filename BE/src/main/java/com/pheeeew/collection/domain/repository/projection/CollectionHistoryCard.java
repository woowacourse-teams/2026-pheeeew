package com.pheeeew.collection.domain.repository.projection;

public record CollectionHistoryCard(long id, CollectionCardDetail detail) {

    public static CollectionHistoryCard of(long id, CollectionCardDetail detail) {
        return new CollectionHistoryCard(id, detail);
    }
}
