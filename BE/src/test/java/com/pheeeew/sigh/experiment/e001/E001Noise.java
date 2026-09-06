package com.pheeeew.sigh.experiment.e001;

enum E001Noise {
    GRADIENT(0xEECF68A53C368278L),
    VALUE(0xCD7893E850B50B86L);

    private static final double DIAGONAL = 0x1.6a09e667f3bccp-1;
    private static final double GRADIENT_SCALE = 0x1.6a09e667f3bcdp0;
    private static final double[] GRADIENT_X = {1.0, -1.0, 0.0, 0.0, DIAGONAL, -DIAGONAL, DIAGONAL, -DIAGONAL};
    private static final double[] GRADIENT_Y = {0.0, 0.0, 1.0, -1.0, DIAGONAL, DIAGONAL, -DIAGONAL, -DIAGONAL};

    private final long domain;

    E001Noise(long domain) {
        this.domain = domain;
    }

    double valueAt(long fieldSeed, int octave, double qx, double qy) {
        long ix = (long) StrictMath.floor(qx);
        long iy = (long) StrictMath.floor(qy);
        double tx = qx - ix;
        double ty = qy - iy;
        long h00 = latticeHash(fieldSeed, octave, ix, iy);
        long h10 = latticeHash(fieldSeed, octave, ix + 1, iy);
        long h01 = latticeHash(fieldSeed, octave, ix, iy + 1);
        long h11 = latticeHash(fieldSeed, octave, ix + 1, iy + 1);

        if (this == GRADIENT) {
            return gradient(h00, h10, h01, h11, tx, ty);
        }
        double x0 = lerp(latticeValue(h00), latticeValue(h10), fade(tx));
        double x1 = lerp(latticeValue(h01), latticeValue(h11), fade(tx));
        return lerp(x0, x1, fade(ty));
    }

    long latticeHash(long fieldSeed, int octave, long ix, long iy) {
        long hash = E001SplitMix64.mix64(fieldSeed ^ domain);
        hash = E001SplitMix64.mix64(hash ^ Integer.toUnsignedLong(octave));
        hash = E001SplitMix64.mix64(hash ^ ix);
        return E001SplitMix64.mix64(hash ^ iy);
    }

    private double gradient(long h00, long h10, long h01, long h11, double tx, double ty) {
        double d00 = dot(h00, tx, ty);
        double d10 = dot(h10, tx - 1.0, ty);
        double d01 = dot(h01, tx, ty - 1.0);
        double d11 = dot(h11, tx - 1.0, ty - 1.0);
        double x0 = lerp(d00, d10, fade(tx));
        double x1 = lerp(d01, d11, fade(tx));
        double raw = lerp(x0, x1, fade(ty));
        double scaled = raw * GRADIENT_SCALE;
        double lower = StrictMath.min(1.0, scaled);
        return StrictMath.max(-1.0, lower);
    }

    private double dot(long hash, double x, double y) {
        int index = (int) (hash & 7L);
        return GRADIENT_X[index] * x + GRADIENT_Y[index] * y;
    }

    private double latticeValue(long hash) {
        return 2.0 * ((hash >>> 11) * 0x1.0p-53) - 1.0;
    }

    private double fade(double value) {
        double squared = value * value;
        double cubed = squared * value;
        return cubed * (value * ((value * 6.0) - 15.0) + 10.0);
    }

    private double lerp(double from, double to, double ratio) {
        return from + ratio * (to - from);
    }
}
