package com.pheeeew.climb.stairs;

import java.time.LocalDate;
import java.util.List;

/** 정수 규칙 v1: WEB/climb/public/stairs/rules.js 및 README 표와 함께 변경한다. */
public final class StairsRules {
    public static final int MAX_INPUTS = 5000;
    public static final int MAX_TIME_MS = 600000;
    public static final long GAUGE_MAX = 6000000;
    public static final long GAUGE_GAIN = 400000;
    public static final int MIN_INTERVAL_MS = 45;
    public static final int MAX_FAST_STREAK = 3;

    private StairsRules() { }

    public static int seed(LocalDate day) {
        return (int) day.toEpochDay() ^ 0x51a17e53;
    }

    public static int drainRate(int height) { return 1000 + height * 6; }
    public static long remaining(long gauge, int height, int elapsed) {
        return gauge - (long) elapsed * drainRate(height);
    }
    public static long replenish(long gauge) { return Math.min(GAUGE_MAX, gauge + GAUGE_GAIN); }

    public static String direction(int seed, int index) {
        if (index < 0) throw new IllegalArgumentException("INVALID_INDEX");
        if (index < 10) return (((index / 5) ^ seed) & 1) == 1 ? "R" : "L";
        int block = (index - 10) / 16, offset = (index - 10) % 16;
        int x = seed ^ ((block + 1) * 0x9e3779b9);
        x ^= x << 13; x ^= x >>> 17; x ^= x << 5;
        long u = Integer.toUnsignedLong(x);
        int level = Math.min(3, (10 + block * 16) / 160);
        int mode = (int) (u % 100), base = (x >>> 7) & 1;
        int flip;
        if (mode < 35) flip = offset & 1;
        else if (mode < 80 - level * 5) {
            int length = Math.max(1, 4 - level - ((x >>> 9) & 1));
            flip = (offset / length) & 1;
        } else {
            flip = offset == 7 || (level >= 1 && offset == 12) || (level >= 3 && offset == 3) ? 1 : 0;
        }
        return (base ^ flip) == 1 ? "R" : "L";
    }

    public static Score replay(int seed, List<Input> inputs) {
        if (inputs == null || inputs.isEmpty() || inputs.size() > MAX_INPUTS) fail("INVALID_INPUTS");
        int height = 0, timeMs = 0, previous = 0, fast = 0;
        long gauge = GAUGE_MAX;
        for (int i = 0; i < inputs.size(); i++) {
            Input input = inputs.get(i);
            if (input == null || !("L".equals(input.d()) || "R".equals(input.d())) || input.t() < 0
                    || input.t() > MAX_TIME_MS || (i == 0 ? input.t() != 0 : input.t() <= previous)) {
                fail("INVALID_INPUTS");
            }
            int delta = input.t() - previous;
            fast = i > 0 && delta < MIN_INTERVAL_MS ? fast + 1 : 0;
            if (fast >= MAX_FAST_STREAK) fail("TOO_FAST");
            gauge = remaining(gauge, height, delta);
            if (gauge <= 0) fail("GAUGE_EXPIRED");
            if (!input.d().equals(direction(seed, height))) break;
            height++; timeMs = input.t(); previous = input.t(); gauge = replenish(gauge);
        }
        return new Score(height, timeMs);
    }

    private static void fail(String code) { throw new IllegalArgumentException(code); }
    public record Input(String d, int t) { }
    public record Score(int height, int timeMs) {
        public boolean betterThan(Score other) {
            return height > other.height || (height == other.height && timeMs < other.timeMs);
        }
    }
}
