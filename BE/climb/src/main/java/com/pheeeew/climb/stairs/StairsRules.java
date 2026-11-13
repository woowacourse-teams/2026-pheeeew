package com.pheeeew.climb.stairs;

import java.time.LocalDate;
import java.util.List;

/** 정수 규칙 v2: WEB/climb/public/stairs/rules.js 및 README 표와 함께 변경한다. */
public final class StairsRules {
    public static final int MAX_INPUTS = 5000;
    public static final int MAX_TIME_MS = 600000;
    public static final long GAUGE_GAIN = 400000;
    public static final int MIN_INTERVAL_MS = 45;
    public static final int MAX_FAST_STREAK = 3;

    private StairsRules() { }

    public static int seed(LocalDate day) {
        return (int) day.toEpochDay() ^ 0x51a17e53;
    }

    public static int drainRate(int height) { return 1000 + height * 6; }

    // 높이 | 최대 버팀 시간(ms). 양수 가중합을 나눠 JS·Java 모두 정수 내림한다.
    private static final int[][] CAP_MS_CURVE = {
            {0, 6000}, {300, 2000}, {600, 1200}
    };
    public static int capMs(int height) {
        for (int i = 1; i < CAP_MS_CURVE.length; i++) {
            int h0 = CAP_MS_CURVE[i - 1][0], ms0 = CAP_MS_CURVE[i - 1][1];
            int h1 = CAP_MS_CURVE[i][0], ms1 = CAP_MS_CURVE[i][1];
            if (height <= h1) return (int) (((long) ms0 * (h1 - height) + (long) ms1 * (height - h0)) / (h1 - h0));
        }
        return CAP_MS_CURVE[CAP_MS_CURVE.length - 1][1];
    }
    public static long cap(int height) { return (long) capMs(height) * drainRate(height); }
    public static long remaining(long gauge, int height, int elapsed) {
        return gauge - (long) elapsed * drainRate(height);
    }
    // height는 성공 후 오른 높이.
    public static long replenish(long gauge, int height) { return Math.min(cap(height), gauge + GAUGE_GAIN); }

    // 생성기 상수표 (JS와 동일)
    // 연습 | 10칸, 5칸씩   일반 묶음 | 1~6칸
    // 함정 선택률 | 10칸 10% → 50칸 40% → 800칸 60% (정수 내림)
    // 함정 묶음 | (3~4,1,3~4), (2,1,3~6), (1,1,3~6)
    // 제한 | 1칸 묶음 최대 2연속 (= 번갈아 최대 4칸), 같은 길이 최대 3연속
    private static final int PRACTICE = 10, RUN_MAX = 6, SINGLE_MAX = 2, RHYTHM_MAX = 3;
    private static final int HASH_MULTIPLIER = 0x9e3779b9;
    private static final int TRAP_EARLY = 10, TRAP_RAMP = 30, RAMP_STEPS = 40;
    private static final int TRAP_LATE = 20, LATE_STEPS = 750;

    public static String direction(int seed, int index) {
        if (index < 0) throw new IllegalArgumentException("INVALID_INDEX");
        if (index < PRACTICE) return (((index / 5) ^ seed) & 1) == 1 ? "R" : "L";
        int position = PRACTICE, run = 0, previous = 5, repeated = 2, singles = 0;
        int[] pending = new int[0];
        int cursor = 0, side = seed & 1;
        while (true) {
            int x = seed ^ ((run + 1) * HASH_MULTIPLIER);
            x ^= x << 13; x ^= x >>> 17; x ^= x << 5;
            long u = Integer.toUnsignedLong(x);
            if (cursor == pending.length) {
                int chance = TRAP_EARLY + Math.min(RAMP_STEPS, position - PRACTICE) * TRAP_RAMP / RAMP_STEPS
                        + Math.min(LATE_STEPS, Math.max(0, position - 50)) * TRAP_LATE / LATE_STEPS;
                if (u % 100 < chance) {
                    int kind = (int) ((u >>> 8) % 3);
                    pending = kind == 0 ? new int[]{3 + (int) ((u >>> 12) % 2), 1, 3 + (int) ((u >>> 16) % 2)}
                            : kind == 1 ? new int[]{2, 1, 3 + (int) ((u >>> 16) % 4)}
                            : new int[]{1, 1, 3 + (int) ((u >>> 16) % 4)};
                    cursor = 0;
                }
            }
            int length = cursor < pending.length ? pending[cursor++] : 1 + (int) ((u >>> 16) % RUN_MAX);
            if (length == 1 && singles >= SINGLE_MAX) length = 2 + (int) ((u >>> 20) % (RUN_MAX - 1));
            if (length == previous && repeated >= RHYTHM_MAX) length = 1 + length % RUN_MAX;
            repeated = length == previous ? repeated + 1 : 1;
            singles = length == 1 ? singles + 1 : 0;
            previous = length;
            if (index < position + length) return side == 1 ? "R" : "L";
            position += length; side ^= 1; run++;
        }
    }

    public static Score replay(int seed, List<Input> inputs) {
        if (inputs == null || inputs.isEmpty() || inputs.size() > MAX_INPUTS) fail("INVALID_INPUTS");
        int height = 0, timeMs = 0, previous = 0, fast = 0;
        long gauge = cap(0);
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
            height++; timeMs = input.t(); previous = input.t(); gauge = replenish(gauge, height);
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
