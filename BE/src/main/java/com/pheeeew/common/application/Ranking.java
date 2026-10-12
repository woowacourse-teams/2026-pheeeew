package com.pheeeew.common.application;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.ToLongFunction;

public final class Ranking {

    private Ranking() {
    }

    public static <S, T> List<T> rank(
            List<S> scores,
            ToLongFunction<S> scoreOf,
            BiFunction<Integer, S, T> toItem
    ) {
        List<T> items = new ArrayList<>(scores.size());
        int rank = 0;
        long previousScore = Long.MIN_VALUE;
        for (int index = 0; index < scores.size(); index++) {
            S score = scores.get(index);
            long currentScore = scoreOf.applyAsLong(score);
            if (currentScore != previousScore) {
                rank = index + 1;
                previousScore = currentScore;
            }
            items.add(toItem.apply(rank, score));
        }

        return items;
    }
}
