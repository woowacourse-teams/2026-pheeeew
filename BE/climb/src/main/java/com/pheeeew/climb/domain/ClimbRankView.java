package com.pheeeew.climb.domain;

import java.util.List;

public record ClimbRankView(List<Ranked> top, Ranked me, List<Ranked> near) {

    public static ClimbRankView of(List<Ranked> top, Ranked me, List<Ranked> near) {
        return new ClimbRankView(top, me, near);
    }

    public record Ranked(int rank, ClimbRankEntry entry) {

        public static Ranked of(int rank, ClimbRankEntry entry) {
            return new Ranked(rank, entry);
        }
    }
}
