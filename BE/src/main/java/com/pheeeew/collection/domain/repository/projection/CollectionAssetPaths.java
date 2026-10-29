package com.pheeeew.collection.domain.repository.projection;

public record CollectionAssetPaths(String characterPath, String roundStampPath, String straightStampPath) {

    public static CollectionAssetPaths of(String characterPath, String roundStampPath, String straightStampPath) {
        return new CollectionAssetPaths(characterPath, roundStampPath, straightStampPath);
    }
}
