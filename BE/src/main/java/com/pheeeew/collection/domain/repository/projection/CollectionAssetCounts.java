package com.pheeeew.collection.domain.repository.projection;

public record CollectionAssetCounts(long characterCount, long stampCount) {

    public static CollectionAssetCounts of(long characterCount, long stampCount) {
        return new CollectionAssetCounts(characterCount, stampCount);
    }
}
