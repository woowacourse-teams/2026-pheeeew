package com.pheeeew.climb.domain;

import java.time.Instant;
import java.util.UUID;

public record ClimbGuest(UUID id, String tag, String nickname, Instant createdAt, Instant lastSeenAt) {

    public static ClimbGuest of(UUID id, String tag, String nickname, Instant createdAt, Instant lastSeenAt) {
        return new ClimbGuest(id, tag, nickname, createdAt, lastSeenAt);
    }
}
