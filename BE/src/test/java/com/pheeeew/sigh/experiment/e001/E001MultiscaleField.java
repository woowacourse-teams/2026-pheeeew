package com.pheeeew.sigh.experiment.e001;

import java.util.Objects;

final class E001MultiscaleField {

    static final long FIELD_SEED = 0x5048454545455701L;

    private static final double[] ROTATIONS_DEGREES = {17.0, 71.0, 137.0};

    private final E001Noise noise;
    private final E001FieldProfile profile;
    private final long fieldSeed;
    private final double[] cosines = new double[3];
    private final double[] sines = new double[3];

    private E001MultiscaleField(E001Noise noise, E001FieldProfile profile, long fieldSeed) {
        this.noise = Objects.requireNonNull(noise);
        this.profile = Objects.requireNonNull(profile);
        this.fieldSeed = fieldSeed;
        for (int octave = 0; octave < ROTATIONS_DEGREES.length; octave++) {
            double radians = ROTATIONS_DEGREES[octave] * (StrictMath.PI / 180.0);
            cosines[octave] = StrictMath.cos(radians);
            sines[octave] = StrictMath.sin(radians);
        }
    }

    static E001MultiscaleField of(E001Noise noise, E001FieldProfile profile, long fieldSeed) {
        return new E001MultiscaleField(noise, profile, fieldSeed);
    }

    double valueAt(double eastingMeters, double northingMeters) {
        double n0 = octaveValue(0, eastingMeters, northingMeters);
        double n1 = octaveValue(1, eastingMeters, northingMeters);
        double n2 = octaveValue(2, eastingMeters, northingMeters);
        double value = (profile.weight(0) * n0 + profile.weight(1) * n1) + profile.weight(2) * n2;
        return StrictMath.max(-1.0, StrictMath.min(1.0, value));
    }

    private double octaveValue(int octave, double x, double y) {
        double qx = (cosines[octave] * x - sines[octave] * y) / profile.length(octave);
        double qy = (sines[octave] * x + cosines[octave] * y) / profile.length(octave);
        return noise.valueAt(fieldSeed, octave, qx, qy);
    }
}
