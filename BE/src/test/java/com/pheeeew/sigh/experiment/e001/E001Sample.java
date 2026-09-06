package com.pheeeew.sigh.experiment.e001;

import java.util.Comparator;

record E001Sample(long sampleSeed, String centerId, long pointIndex,
                  double centerEasting, double centerNorthing, E001Offset offset) {

    static final Comparator<E001Sample> ORDER = Comparator.comparingLong(E001Sample::sampleSeed)
            .thenComparing(E001Sample::centerId).thenComparingLong(E001Sample::pointIndex);

    static E001Sample of(long sampleSeed, String centerId, long pointIndex,
                         double centerEasting, double centerNorthing, E001Offset offset) {
        return new E001Sample(sampleSeed, centerId, pointIndex, centerEasting, centerNorthing, offset);
    }

    double x() {
        return centerEasting + offset.eastingMeters();
    }

    double y() {
        return centerNorthing + offset.northingMeters();
    }

    double radius() {
        return StrictMath.hypot(offset.eastingMeters(), offset.northingMeters());
    }

    boolean samePoint(E001Sample other) {
        return sampleSeed == other.sampleSeed && pointIndex == other.pointIndex && centerId.equals(other.centerId);
    }
}
