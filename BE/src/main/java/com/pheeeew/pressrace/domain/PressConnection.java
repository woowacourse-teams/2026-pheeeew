package com.pheeeew.pressrace.domain;

import java.time.Instant;
import java.util.UUID;

public record PressConnection(UUID devicePublicId, UUID serverGeneration, UUID connectionGeneration, Instant expiresAt) {

    public static PressConnection of(UUID devicePublicId, UUID serverGeneration, UUID connectionGeneration, Instant expiresAt) {
        return new PressConnection(devicePublicId, serverGeneration, connectionGeneration, expiresAt);
    }
}
