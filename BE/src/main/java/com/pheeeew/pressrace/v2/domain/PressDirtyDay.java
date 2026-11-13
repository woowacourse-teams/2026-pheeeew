package com.pheeeew.pressrace.v2.domain;

import java.time.LocalDate;
import java.util.UUID;

public record PressDirtyDay(LocalDate date, UUID devicePublicId, long version) {
    public static PressDirtyDay of(String member, long version) {
        int separator = member.indexOf(':');
        return new PressDirtyDay(LocalDate.parse(member.substring(0, separator)),
                UUID.fromString(member.substring(separator + 1)), version);
    }

    public String member() {
        return date + ":" + devicePublicId;
    }
}
