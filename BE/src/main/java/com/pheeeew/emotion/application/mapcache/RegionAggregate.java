package com.pheeeew.emotion.application.mapcache;

import com.pheeeew.emotion.application.dto.RegionEmotionSummary;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.projection.RegionEmotionStateCount;
import com.pheeeew.region.domain.RegionLevel;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/** 전체 기간 집계와 KST 날짜별 집계를 함께 보관한다. 기간 요약은 날짜별 집계를 합산한다. */
final class RegionAggregate {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final List<EmotionState> PRIORITY = List.of(
            EmotionState.ANGRY, EmotionState.DISCOURAGED, EmotionState.EXHAUSTED,
            EmotionState.FRUSTRATED, EmotionState.IRRITATED);

    private final Map<String, Map<EmotionState, Long>> counts;
    private final NavigableMap<LocalDate, Map<String, Map<EmotionState, Long>>> daily;
    private final Instant nextActivation;

    private RegionAggregate(Map<String, Map<EmotionState, Long>> counts,
            NavigableMap<LocalDate, Map<String, Map<EmotionState, Long>>> daily, Instant nextActivation) {
        this.counts = counts;
        this.daily = daily;
        this.nextActivation = nextActivation;
    }

    static RegionAggregate from(List<RegionEmotionStateCount> rows) {
        Map<String, Map<EmotionState, Long>> counts = new HashMap<>();
        TreeMap<LocalDate, Map<String, Map<EmotionState, Long>>> daily = new TreeMap<>();
        Instant nextActivation = null;

        for (RegionEmotionStateCount row : rows) {
            if (row.amount() > 0) {
                add(counts, row.code(), row.state(), row.amount());
                add(daily.computeIfAbsent(row.day(), ignored -> new HashMap<>()), row.code(), row.state(), row.amount());
            }
            if (row.nextActivation() != null && (nextActivation == null || row.nextActivation().isBefore(nextActivation))) {
                nextActivation = row.nextActivation();
            }
        }

        daily.replaceAll((day, values) -> immutableCounts(values));

        return new RegionAggregate(immutableCounts(counts), Collections.unmodifiableNavigableMap(daily), nextActivation);
    }

    RegionAggregate applyChange(EmotionMapContribution before, EmotionMapContribution after, Instant now) {
        Map<String, Map<EmotionState, Long>> updated = mutableCopy(counts);
        TreeMap<LocalDate, Map<String, Map<EmotionState, Long>>> updatedDaily = new TreeMap<>(daily);

        adjust(updated, updatedDaily, before, -1, now);
        adjust(updated, updatedDaily, after, 1, now);

        Instant nextActivation = this.nextActivation;
        if (after != null && !after.deleted() && after.hasContent() && after.createdAt().isAfter(now)
                && (nextActivation == null || after.createdAt().isBefore(nextActivation))) {
            nextActivation = after.createdAt();
        }

        return new RegionAggregate(immutableCounts(updated), Collections.unmodifiableNavigableMap(updatedDaily), nextActivation);
    }

    Map<RegionLevel, Map<String, RegionEmotionSummary>> summaries() {
        return summariesOf(counts);
    }

    /** startDate와 endDate를 모두 포함하는 KST 날짜 범위의 요약을 계산한다. */
    Map<RegionLevel, Map<String, RegionEmotionSummary>> summaries(LocalDate startDate, LocalDate endDate) {
        Map<String, Map<EmotionState, Long>> selected = new HashMap<>();
        daily.subMap(startDate, true, endDate, true).values().forEach(values -> values.forEach((code, amounts) ->
                amounts.forEach((state, amount) -> add(selected, code, state, amount))));

        return summariesOf(selected);
    }

    Instant nextActivation() {
        return nextActivation;
    }

    private static void add(Map<String, Map<EmotionState, Long>> counts, String code, EmotionState state, long amount) {
        counts.computeIfAbsent(code, ignored -> new HashMap<>()).merge(state, amount, Long::sum);
    }

    private static Map<String, Map<EmotionState, Long>> mutableCopy(Map<String, Map<EmotionState, Long>> counts) {
        Map<String, Map<EmotionState, Long>> copied = new HashMap<>();
        counts.forEach((code, values) -> copied.put(code, new HashMap<>(values)));

        return copied;
    }

    private static Map<String, Map<EmotionState, Long>> immutableCounts(Map<String, Map<EmotionState, Long>> counts) {
        Map<String, Map<EmotionState, Long>> result = new HashMap<>();
        counts.forEach((code, values) -> result.put(code, Collections.unmodifiableMap(new HashMap<>(values))));

        return Map.copyOf(result);
    }

    private static void adjust(Map<String, Map<EmotionState, Long>> counts,
            NavigableMap<LocalDate, Map<String, Map<EmotionState, Long>>> daily, EmotionMapContribution entry,
            long delta, Instant now) {
        if (entry == null || !entry.contributesAt(now)) {
            return;
        }

        String code = entry.regionCode().substring(0, 5);
        adjust(counts, code, entry.state(), delta);

        LocalDate day = LocalDate.ofInstant(entry.createdAt(), KOREA);
        Map<String, Map<EmotionState, Long>> dayCounts = mutableCopy(daily.getOrDefault(day, Map.of()));
        adjust(dayCounts, code, entry.state(), delta);
        if (dayCounts.isEmpty()) {
            daily.remove(day);
        } else {
            daily.put(day, immutableCounts(dayCounts));
        }
    }

    private static void adjust(Map<String, Map<EmotionState, Long>> counts, String code, EmotionState state, long delta) {
        Map<EmotionState, Long> values = counts.computeIfAbsent(code, ignored -> new HashMap<>());
        long updated = values.getOrDefault(state, 0L) + delta;

        if (updated < 0) {
            throw new IllegalStateException("지역 집계 기여분이 음수입니다.");
        }

        if (updated == 0) {
            values.remove(state);
            if (values.isEmpty()) {
                counts.remove(code);
            }
        } else {
            values.put(state, updated);
        }
    }

    private static Map<RegionLevel, Map<String, RegionEmotionSummary>> summariesOf(Map<String, Map<EmotionState, Long>> counts) {
        Map<RegionLevel, Map<String, RegionEmotionSummary>> summaries = new EnumMap<>(RegionLevel.class);
        summaries.put(RegionLevel.SIGUNGU, representatives(counts));

        Map<String, Map<EmotionState, Long>> sido = new HashMap<>();
        counts.forEach((code, values) -> values.forEach((state, amount) -> add(sido, code.substring(0, 2), state, amount)));
        summaries.put(RegionLevel.SIDO, representatives(sido));

        return Map.copyOf(summaries);
    }

    private static Map<String, RegionEmotionSummary> representatives(Map<String, Map<EmotionState, Long>> counts) {
        Map<String, RegionEmotionSummary> summaries = new HashMap<>();
        counts.forEach((code, values) -> {
            long total = values.values().stream().mapToLong(Long::longValue).sum();
            if (total > 0) {
                EmotionState representative = null;
                long highest = 0;
                for (EmotionState state : PRIORITY) {
                    long amount = values.getOrDefault(state, 0L);
                    if (amount > highest) {
                        representative = state;
                        highest = amount;
                    }
                }

                summaries.put(code, RegionEmotionSummary.of(total, representative));
            }
        });

        return Map.copyOf(summaries);
    }
}
