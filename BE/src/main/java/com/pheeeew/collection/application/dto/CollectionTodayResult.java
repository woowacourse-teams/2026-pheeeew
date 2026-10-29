package com.pheeeew.collection.application.dto;

import com.pheeeew.collection.domain.CollectionClaimRequirement;
import com.pheeeew.emotion.domain.EmotionState;
import java.time.LocalDate;
import java.util.List;

public record CollectionTodayResult(
        LocalDate date,
        Progress stamps,
        Progress presses,
        boolean claimed,
        boolean claimable,
        List<Item> items
) {

    public static CollectionTodayResult of(LocalDate date, List<Item> items) {
        long stampCount = items.stream().mapToLong(item -> item.stamps().count()).sum();
        long pressCount = items.stream().mapToLong(item -> item.presses().count()).sum();
        boolean claimed = items.stream().anyMatch(item -> item.card() != null);
        boolean claimable = !claimed && CollectionClaimRequirement.isMet(stampCount, pressCount);
        return new CollectionTodayResult(date, Progress.of(stampCount, CollectionClaimRequirement.STAMP_TARGET),
                Progress.of(pressCount, CollectionClaimRequirement.PRESS_TARGET), claimed, claimable,
                List.copyOf(items));
    }

    public record Item(EmotionState state, Progress stamps, Progress presses, CollectionCardResult card) {

        public static Item of(EmotionState state, long stampCount, long pressCount) {
            return of(state, stampCount, pressCount, null);
        }

        public static Item of(EmotionState state, long stampCount, long pressCount, CollectionCardResult card) {
            return new Item(state, Progress.of(stampCount, CollectionClaimRequirement.STAMP_TARGET),
                    Progress.of(pressCount, CollectionClaimRequirement.PRESS_TARGET), card);
        }
    }

    public record Progress(long count, long target, long remaining) {

        public static Progress of(long count, long target) {
            return new Progress(count, target, Math.max(target - count, 0));
        }
    }
}
