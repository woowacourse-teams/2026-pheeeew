package com.pheeeew.sigh.experiment.e001;

record E001Offset(double eastingMeters, double northingMeters, double radiusMeters) {

    static E001Offset of(double eastingMeters, double northingMeters, double radiusMeters) {
        return new E001Offset(eastingMeters, northingMeters, radiusMeters);
    }
}
