package com.pheeeew.collection.application.dto;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.LocalDate;
import java.util.List;

public record CollectionTodayResult(LocalDate date, List<Item> items) {

    public static CollectionTodayResult of(LocalDate date, List<Item> items) {
        return new CollectionTodayResult(date, List.copyOf(items));
    }

    public record Item(EmotionState state, Progress stamps, Progress presses, CollectionCardResult card) {

        private static final long STAMP_TARGET = 1;
        private static final long PRESS_TARGET = 100;

        public static Item of(EmotionState state, long stampCount, long pressCount) {
            return of(state, stampCount, pressCount, null);
        }

        public static Item of(EmotionState state, long stampCount, long pressCount, CollectionCardResult card) {
            return new Item(state, Progress.of(stampCount, STAMP_TARGET), Progress.of(pressCount, PRESS_TARGET), card);
        }
    }

    public record Progress(long count, long target, long remaining) {

        public static Progress of(long count, long target) {
            return new Progress(count, target, Math.max(target - count, 0));
        }
    }
}
