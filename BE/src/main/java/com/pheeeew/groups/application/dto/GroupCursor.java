package com.pheeeew.groups.application.dto;

import java.time.Instant;
import java.util.Objects;

public record GroupCursor(Instant lastCreatedAt, long lastId) {
    public GroupCursor {
        Objects.requireNonNull(lastCreatedAt);
    }

    public static GroupCursor of(Instant lastCreatedAt, long lastId) {
        return new GroupCursor(lastCreatedAt, lastId);
    }
}
