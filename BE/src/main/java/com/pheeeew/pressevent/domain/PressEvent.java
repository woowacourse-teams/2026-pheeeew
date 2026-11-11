package com.pheeeew.pressevent.domain;

import java.time.Instant;

public record PressEvent(Instant startsAt, Instant endsAt, Instant observedAt) {

    public static PressEvent of(Instant startsAt, Instant endsAt, Instant observedAt) {
        return new PressEvent(startsAt, endsAt, observedAt);
    }

    public boolean accepts(Instant now) {
        return !now.isBefore(startsAt) && now.isBefore(endsAt);
    }
}
