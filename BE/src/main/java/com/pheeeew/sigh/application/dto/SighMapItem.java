package com.pheeeew.sigh.application.dto;

import java.time.Instant;

public record SighMapItem(Long id, double longitude, double latitude, Instant createdAt) {

    public static SighMapItem of(Long id, double longitude, double latitude, Instant createdAt) {
        return new SighMapItem(id, longitude, latitude, createdAt);
    }
}
