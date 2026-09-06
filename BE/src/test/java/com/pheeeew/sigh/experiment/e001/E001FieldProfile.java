package com.pheeeew.sigh.experiment.e001;

enum E001FieldProfile {
    P1(new double[] {120.0, 360.0, 1_080.0}, new double[] {0.40, 0.35, 0.25}),
    P2(new double[] {180.0, 540.0, 1_620.0}, new double[] {0.20, 0.35, 0.45});

    private final double[] lengths;
    private final double[] weights;

    E001FieldProfile(double[] lengths, double[] weights) {
        this.lengths = lengths;
        this.weights = weights;
    }

    double length(int octave) {
        return lengths[octave];
    }

    double weight(int octave) {
        return weights[octave];
    }
}
