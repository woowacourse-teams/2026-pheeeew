package com.pheeeew.collection.application.dto;

public record CollectionCardImages(String characterUrl, String roundStampUrl, String straightStampUrl) {

    public static CollectionCardImages of(String characterUrl, String roundStampUrl, String straightStampUrl) {
        return new CollectionCardImages(characterUrl, roundStampUrl, straightStampUrl);
    }
}
