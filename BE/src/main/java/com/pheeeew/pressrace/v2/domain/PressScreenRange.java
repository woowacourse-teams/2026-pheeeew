package com.pheeeew.pressrace.v2.domain;

import java.time.Duration;

public record PressScreenRange(int topSize, int nearbySize, int leaderSize, Duration activeWindow) {
    public static PressScreenRange of(int topSize, int nearbySize, int leaderSize, Duration activeWindow) {
        return new PressScreenRange(topSize, nearbySize, leaderSize, activeWindow);
    }
}
