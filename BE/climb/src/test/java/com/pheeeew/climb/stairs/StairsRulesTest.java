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
        for (String line : resource("generator-v1.txt").lines().toList()) {
            String[] pair = line.split(":");
            int seed = Integer.parseInt(pair[0]);
            StringBuilder actual = new StringBuilder();
            for (int i = 0; i < 2000; i++) actual.append(StairsRules.direction(seed, i));
            assertThat(actual.toString()).isEqualTo(pair[1]);
            for (int i = 1999; i >= 0; i--) assertThat(StairsRules.direction(seed, i)).isEqualTo(pair[1].substring(i, i + 1));
        }
    }
    @Test void gaugeMatchesSharedJavascriptFixtures() throws Exception {
        for (String line : resource("gauge-v1.csv").lines().toList()) {
            String[] row = line.split(",");
            long left = StairsRules.remaining(Long.parseLong(row[0]), Integer.parseInt(row[1]), Integer.parseInt(row[2]));
            assertThat(left).isEqualTo(Long.parseLong(row[3]));
            assertThat(StairsRules.replenish(left)).isEqualTo(Long.parseLong(row[4]));
        }
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
