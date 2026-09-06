package com.pheeeew.sigh.experiment.e001;

final class E001SplitMix64 implements E001UniformRandom {

    private static final long STATE_INCREMENT = 0x9E3779B97F4A7C15L;
    private static final long FIRST_MULTIPLIER = 0xBF58476D1CE4E5B9L;
    private static final long SECOND_MULTIPLIER = 0x94D049BB133111EBL;
    private static final double DOUBLE_UNIT = 0x1.0p-53;

    private long state;

    private E001SplitMix64(long state) {
        this.state = state;
    }

    static E001SplitMix64 from(long state) {
        return new E001SplitMix64(state);
    }

    long nextLong() {
        state += STATE_INCREMENT;
        return mix64(state);
    }

    @Override
    public double nextDouble() {
        return (nextLong() >>> 11) * DOUBLE_UNIT;
    }

    static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * FIRST_MULTIPLIER;
        mixed = (mixed ^ (mixed >>> 27)) * SECOND_MULTIPLIER;
        return mixed ^ (mixed >>> 31);
    }
}
