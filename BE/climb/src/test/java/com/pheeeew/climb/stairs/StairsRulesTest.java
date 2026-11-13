package com.pheeeew.climb.stairs;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StairsRulesTest {
    @Test void generatorMatchesSharedJavascriptFixtures() throws Exception {
        for (String line : resource("generator-v2.txt").lines().toList()) {
            String[] pair = line.split(":");
            int seed = Integer.parseInt(pair[0]);
            StringBuilder actual = new StringBuilder();
            for (int i = 0; i < 2000; i++) actual.append(StairsRules.direction(seed, i));
            assertThat(actual.toString()).isEqualTo(pair[1]);
            for (int i = 1999; i >= 0; i--) assertThat(StairsRules.direction(seed, i)).isEqualTo(pair[1].substring(i, i + 1));
        }
    }
    @Test void gaugeMatchesSharedJavascriptFixtures() throws Exception {
        for (String line : resource("gauge-v2.csv").lines().toList()) {
            String[] row = line.split(",");
            long left = StairsRules.remaining(Long.parseLong(row[0]), Integer.parseInt(row[1]), Integer.parseInt(row[2]));
            assertThat(left).isEqualTo(Long.parseLong(row[3]));
            assertThat(StairsRules.replenish(left, Integer.parseInt(row[1]) + 1)).isEqualTo(Long.parseLong(row[4]));
            assertThat(StairsRules.capMs(Integer.parseInt(row[1]))).isEqualTo(Integer.parseInt(row[5]));
            assertThat(StairsRules.cap(Integer.parseInt(row[1]))).isEqualTo(Long.parseLong(row[6]));
        }
    }
    @Test void irregularRunsStayBoundedAcrossAllBoundaries() {
        int earlyTraps = 0, lateTraps = 0;
        var seeds = new ArrayList<Integer>();
        for (int seed = 0; seed < 100; seed++) seeds.add(seed);
        seeds.addAll(List.of(-1, Integer.MIN_VALUE, Integer.MAX_VALUE, 0x9e3779b9));
        for (int seed : seeds) {
            var pattern = new StringBuilder();
            for (int i = 0; i < 2001; i++) pattern.append(StairsRules.direction(seed, i));
            assertThat(pattern.substring(0, 10)).isEqualTo((seed & 1) == 1 ? "RRRRRLLLLL" : "LLLLLRRRRR");
            String text = pattern.substring(10, 2000);
            assertThat(text).doesNotContain("LRLRL", "RLRLR");
            var runs = new ArrayList<>(java.util.regex.Pattern.compile("L+|R+").matcher(text)
                    .results().map(m -> m.group().length()).toList());
            runs.removeLast(); // 끝에서 잘린 묶음 제외
            int[] counts = new int[7];
            int traps = 0;
            for (int i = 0; i < runs.size(); i++) {
                int length = runs.get(i);
                counts[Math.min(6, length)]++;
                if (i >= 3) assertThat(runs.subList(i - 3, i + 1).stream().allMatch(n -> n == length))
                        .as("rhythm seed=%d run=%d", seed, i).isFalse();
                if (i >= 2 && runs.get(i - 1) == 1 && runs.get(i - 2) >= 3 && length >= 3) traps++;
            }
            for (int length = 1; length <= 6; length++) assertThat((double) counts[length] / runs.size())
                    .as("distribution seed=%d length=%d", seed, length).isGreaterThanOrEqualTo(.05);
            assertThat(traps).as("long traps seed=%d", seed).isGreaterThanOrEqualTo(30);
            assertThat((double) matches(text, "(?=(LRLR|RLRL))") / text.length()).isLessThan(.05);
            earlyTraps += matches(pattern.substring(10, 50), "(?=(LLRLL|RRLRR))");
            lateTraps += matches(pattern.substring(800, 840), "(?=(LLRLL|RRLRR))");
        }
        assertThat((double) lateTraps).isGreaterThan(earlyTraps * 1.25);
    }
    @Test void gaugeUsesOriginalDrainAndHeightDependentStorage() {
        double[][] targets = {{0, 2.5}, {100, 4}, {300, 7}, {500, 10}, {800, 14.5}};
        for (double[] row : targets) assertThat(StairsRules.drainRate((int) row[0]) * 1000.0 / StairsRules.GAUGE_GAIN)
                .isEqualTo(row[1]);
        int[][] caps = {{0, 6000}, {150, 4000}, {300, 2000}, {450, 1600}, {600, 1200}, {900, 1200},
                {1, 5986}, {299, 2013}, {301, 1997}, {599, 1202}};
        for (int[] row : caps) assertThat(StairsRules.capMs(row[0])).isEqualTo(row[1]);
        assertThat(StairsRules.cap(0)).isEqualTo(6000000);
        for (int h = 1; h <= 5000; h++) {
            assertThat(StairsRules.drainRate(h)).isEqualTo(1000 + h * 6);
            assertThat(StairsRules.capMs(h)).isLessThanOrEqualTo(StairsRules.capMs(h - 1));
            assertThat(StairsRules.replenish(StairsRules.cap(h - 1), h))
                    .isEqualTo(Math.min(StairsRules.cap(h), StairsRules.cap(h - 1) + StairsRules.GAUGE_GAIN));
        }
    }
    @Test void replayExpiresOnExactlyTheSameInputAsJavascript() throws Exception {
        for (String line : resource("replay-v2.csv").lines().toList()) {
            int[] row = java.util.Arrays.stream(line.split(",")).mapToInt(Integer::parseInt).toArray();
            int seed = row[0], interval = row[1], jitter = row[2], height = row[3];
            var events = new ArrayList<StairsRules.Input>();
            int time = 0;
            long gauge = StairsRules.cap(0);
            for (int i = 0; i <= height; i++) {
                int delta = i == 0 ? 0 : interval + i * 37 % (jitter * 2 + 1) - jitter;
                time += delta;
                gauge = StairsRules.remaining(gauge, i, delta);
                if (i == height) {
                    assertThat(time).isEqualTo(row[5]);
                    assertThat(gauge).isEqualTo(row[6]).isLessThanOrEqualTo(0);
                } else {
                    assertThat(gauge).isPositive();
                    gauge = StairsRules.replenish(gauge, i + 1);
                }
                events.add(new StairsRules.Input(StairsRules.direction(seed, i), time));
            }
            assertThat(StairsRules.replay(seed, events.subList(0, height)))
                    .isEqualTo(new StairsRules.Score(height, row[4]));
            assertThatThrownBy(() -> StairsRules.replay(seed, events)).hasMessage("GAUGE_EXPIRED");
            if (height >= 300) {
                var paused = new ArrayList<>(events.subList(0, height));
                paused.add(new StairsRules.Input(StairsRules.direction(seed, height), row[4] + 500));
                assertThatThrownBy(() -> StairsRules.replay(seed, paused)).hasMessage("GAUGE_EXPIRED");
            }
        }
    }
    private static int matches(String text, String regex) {
        return (int) java.util.regex.Pattern.compile(regex).matcher(text).results().count();
    }
    @Test void validRunUsesLastSuccessfulInputTime() {
        assertThat(StairsRules.replay(123, inputs(123, 50, 100))).isEqualTo(new StairsRules.Score(50, 4900));
    }
    @Test void wrongDirectionEndsRunAndIgnoresFollowingInputs() {
        var list = new ArrayList<>(inputs(123, 5, 100));
        list.set(3, new StairsRules.Input(opposite(list.get(3).d()), 300));
        list.set(4, new StairsRules.Input("R", -1));
        assertThat(StairsRules.replay(123, list)).isEqualTo(new StairsRules.Score(3, 200));
        assertThat(StairsRules.replay(123, List.of(new StairsRules.Input(opposite(StairsRules.direction(123, 0)), 0))))
                .isEqualTo(new StairsRules.Score(0, 0));
    }
    @Test void expiredGaugeCannotBeRefilled() {
        assertThatThrownBy(() -> StairsRules.replay(123, inputs(123, 2, 6000))).hasMessage("GAUGE_EXPIRED");
        long gauge = StairsRules.cap(0);
        for (int h = 0; h < 300; h++) gauge = StairsRules.replenish(StairsRules.remaining(gauge, h, h == 0 ? 0 : 45), h + 1);
        assertThat(gauge).isEqualTo(StairsRules.cap(300)).isEqualTo(5600000);
        for (int pause : new int[]{1999, 2000, 2001}) {
            var events = new ArrayList<>(inputs(123, 300, 45));
            int t = events.getLast().t() + pause;
            events.add(new StairsRules.Input(StairsRules.direction(123, 300), t));
            assertThat(StairsRules.remaining(gauge, 300, pause)).isEqualTo((2000L - pause) * 2800);
            if (pause < 2000) assertThat(StairsRules.replay(123, events)).isEqualTo(new StairsRules.Score(301, t));
            else assertThatThrownBy(() -> StairsRules.replay(123, events)).hasMessage("GAUGE_EXPIRED");
        }
    }
    @Test void rejectsThreeConsecutiveIntervalsUnder45msButAllowsOccasionalMultitouch() {
        assertThatThrownBy(() -> StairsRules.replay(123, inputs(123, 4, 44))).hasMessage("TOO_FAST");
        assertThat(StairsRules.replay(123, inputs(123, 4, 45)).height()).isEqualTo(4);
        assertThat(StairsRules.replay(123, inputs(123, 3, 1)).height()).isEqualTo(3);
    }
    @Test void rejectsMalformedOrUnboundedReplay() {
        assertThatThrownBy(() -> StairsRules.replay(123, List.of())).hasMessage("INVALID_INPUTS");
        assertThatThrownBy(() -> StairsRules.replay(123, inputs(123, 5001, 100))).hasMessage("INVALID_INPUTS");
        assertThatThrownBy(() -> StairsRules.replay(123, inputs(123, 2, 0))).hasMessage("INVALID_INPUTS");
        assertThatThrownBy(() -> StairsRules.replay(123, List.of(new StairsRules.Input("L", 1)))).hasMessage("INVALID_INPUTS");
        assertThatThrownBy(() -> StairsRules.replay(123, List.of(new StairsRules.Input("X", 0)))).hasMessage("INVALID_INPUTS");
        assertThatThrownBy(() -> StairsRules.replay(123, inputs(123, 2, 600001))).hasMessage("INVALID_INPUTS");
    }
    @Test void higherHeightThenShorterTimeWins() {
        var best = new StairsRules.Score(20, 4000);
        assertThat(new StairsRules.Score(21, 5000).betterThan(best)).isTrue();
        assertThat(new StairsRules.Score(20, 3999).betterThan(best)).isTrue();
        assertThat(new StairsRules.Score(20, 4000).betterThan(best)).isFalse();
        assertThat(new StairsRules.Score(19, 1000).betterThan(best)).isFalse();
    }
    @Test void seoulDateAndFiveMinuteGraceAreServerAuthoritative() {
        var day = LocalDate.of(2026, 11, 13);
        var atMidnight = serviceAt("2026-11-12T15:00:00Z");
        assertThat(atMidnight.today().day()).isEqualTo(day);
        assertThat(atMidnight.today().seed()).isEqualTo(StairsRules.seed(day));
        atMidnight.requireDay(day); atMidnight.requireDay(day.minusDays(1));
        assertThatThrownBy(() -> atMidnight.requireDay(day.plusDays(1))).hasMessage("INVALID_DAY");
        serviceAt("2026-11-12T15:04:59Z").requireDay(day.minusDays(1));
        assertThatThrownBy(() -> serviceAt("2026-11-12T15:05:00Z").requireDay(day.minusDays(1))).hasMessage("INVALID_DAY");
    }
    static List<StairsRules.Input> inputs(int seed, int count, int interval) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new StairsRules.Input(StairsRules.direction(seed, i), i * interval)).toList();
    }
    private static StairsService serviceAt(String instant) {
        return new StairsService(null, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
    private static String opposite(String d) { return d.equals("L") ? "R" : "L"; }
    private static String resource(String name) throws Exception {
        try (var stream = StairsRulesTest.class.getResourceAsStream("/stairs/" + name)) {
            return new String(java.util.Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
