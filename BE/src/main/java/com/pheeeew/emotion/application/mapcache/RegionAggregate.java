package com.pheeeew.emotion.application.mapcache;

import com.pheeeew.emotion.application.dto.RegionEmotionSummary;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.projection.RegionEmotionStateCount;
import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.region.domain.RegionLevel;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 내용 종류(메모·녹음)별로 전체 기간 집계와 KST 날짜별 집계를 함께 보관한다.
 * 기간 요약은 날짜별 집계를 합산하고, ALL은 두 종류를 합한다.
 */
final class RegionAggregate {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final List<EmotionState> PRIORITY = List.of(
            EmotionState.ANGRY, EmotionState.DISCOURAGED, EmotionState.EXHAUSTED,
            EmotionState.FRUSTRATED, EmotionState.IRRITATED);

    private final Tally memo;
    private final Tally audio;
    private final Instant nextActivation;

    private RegionAggregate(Tally memo, Tally audio, Instant nextActivation) {
        this.memo = memo;
        this.audio = audio;
        this.nextActivation = nextActivation;
    }

    static RegionAggregate from(List<RegionEmotionStateCount> rows) {
        Tally.Builder memo = new Tally.Builder();
        Tally.Builder audio = new Tally.Builder();
        Instant nextActivation = null;

        for (RegionEmotionStateCount row : rows) {
            if (row.amount() > 0) {
                (row.audio() ? audio : memo).add(row.code(), row.day(), row.state(), row.amount());
            }
            if (row.nextActivation() != null && (nextActivation == null || row.nextActivation().isBefore(nextActivation))) {
                nextActivation = row.nextActivation();
            }
        }

        return new RegionAggregate(memo.build(), audio.build(), nextActivation);
    }

    RegionAggregate applyChange(EmotionMapContribution before, EmotionMapContribution after, Instant now) {
        Tally.Builder memo = this.memo.toBuilder();
        Tally.Builder audio = this.audio.toBuilder();

        adjust(memo, audio, before, -1, now);
        adjust(memo, audio, after, 1, now);

        Instant nextActivation = this.nextActivation;
        if (after != null && !after.deleted() && after.hasContent() && after.createdAt().isAfter(now)
                && (nextActivation == null || after.createdAt().isBefore(nextActivation))) {
            nextActivation = after.createdAt();
        }

        return new RegionAggregate(memo.build(), audio.build(), nextActivation);
    }

    Map<RegionLevel, Map<String, RegionEmotionSummary>> summaries(EmotionContentFilter contentType) {
        Map<String, Map<EmotionState, Long>> selected = new HashMap<>();
        tallies(contentType).forEach(tally -> merge(selected, tally.counts));

        return summariesOf(selected);
    }

    /** startDate와 endDate를 모두 포함하는 KST 날짜 범위의 요약을 계산한다. */
    Map<RegionLevel, Map<String, RegionEmotionSummary>> summaries(EmotionContentFilter contentType, LocalDate startDate,
            LocalDate endDate) {
        Map<String, Map<EmotionState, Long>> selected = new HashMap<>();
        tallies(contentType).forEach(tally -> tally.daily.subMap(startDate, true, endDate, true).values()
                .forEach(values -> merge(selected, values)));

        return summariesOf(selected);
    }

    Instant nextActivation() {
        return nextActivation;
    }

    private List<Tally> tallies(EmotionContentFilter contentType) {
        List<Tally> selected = new ArrayList<>();
        if (contentType.includes(false)) {
            selected.add(memo);
        }
        if (contentType.includes(true)) {
            selected.add(audio);
        }

        return selected;
    }

    static void add(Map<String, Map<EmotionState, Long>> counts, String code, EmotionState state, long amount) {
        counts.computeIfAbsent(code, ignored -> new HashMap<>()).merge(state, amount, Long::sum);
    }

    private static void merge(Map<String, Map<EmotionState, Long>> target, Map<String, Map<EmotionState, Long>> source) {
        source.forEach((code, amounts) -> amounts.forEach((state, amount) -> add(target, code, state, amount)));
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

    private static void adjust(Tally.Builder memo, Tally.Builder audio, EmotionMapContribution entry, long delta,
            Instant now) {
        if (entry == null || !entry.contributesAt(now)) {
            return;
        }

        (entry.audio() ? audio : memo).adjust(entry.regionCode().substring(0, 5),
                LocalDate.ofInstant(entry.createdAt(), KOREA), entry.state(), delta);
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

    /** 시군구 코드별 상태 개수를 시군구·시도 요약으로 만든다. */
    static Map<RegionLevel, Map<String, RegionEmotionSummary>> summariesOf(Map<String, Map<EmotionState, Long>> counts) {
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

    /** 한 내용 종류의 전체 기간 집계와 KST 날짜별 집계. 게시 후에는 바꾸지 않는다. */
    private static final class Tally {

        private final Map<String, Map<EmotionState, Long>> counts;
        private final NavigableMap<LocalDate, Map<String, Map<EmotionState, Long>>> daily;

        private Tally(Map<String, Map<EmotionState, Long>> counts,
                NavigableMap<LocalDate, Map<String, Map<EmotionState, Long>>> daily) {
            this.counts = counts;
            this.daily = daily;
        }

        private Builder toBuilder() {
            return new Builder(mutableCopy(counts), new TreeMap<>(daily), false);
        }

        private static final class Builder {

            private final Map<String, Map<EmotionState, Long>> counts;
            private final TreeMap<LocalDate, Map<String, Map<EmotionState, Long>>> daily;
            /** 처음 적재할 때만 날짜별 맵이 가변이다. 변경 반영 때는 바뀐 날짜만 고정해서 넣는다. */
            private final boolean freezeDays;

            private Builder() {
                this(new HashMap<>(), new TreeMap<>(), true);
            }

            private Builder(Map<String, Map<EmotionState, Long>> counts,
                    TreeMap<LocalDate, Map<String, Map<EmotionState, Long>>> daily, boolean freezeDays) {
                this.counts = counts;
                this.daily = daily;
                this.freezeDays = freezeDays;
            }

            private void add(String code, LocalDate day, EmotionState state, long amount) {
                RegionAggregate.add(counts, code, state, amount);
                RegionAggregate.add(daily.computeIfAbsent(day, ignored -> new HashMap<>()), code, state, amount);
            }

            /** 이전 날짜별 맵은 공유하므로 바뀌는 날짜만 복사해서 교체한다. */
            private void adjust(String code, LocalDate day, EmotionState state, long delta) {
                RegionAggregate.adjust(counts, code, state, delta);

                Map<String, Map<EmotionState, Long>> dayCounts = mutableCopy(daily.getOrDefault(day, Map.of()));
                RegionAggregate.adjust(dayCounts, code, state, delta);
                if (dayCounts.isEmpty()) {
                    daily.remove(day);
                } else {
                    daily.put(day, immutableCounts(dayCounts));
                }
            }

            private Tally build() {
                if (freezeDays) {
                    daily.replaceAll((day, values) -> immutableCounts(values));
                }

                return new Tally(immutableCounts(counts), Collections.unmodifiableNavigableMap(daily));
            }
        }
    }
}
