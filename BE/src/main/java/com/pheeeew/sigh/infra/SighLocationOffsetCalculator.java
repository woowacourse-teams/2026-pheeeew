package com.pheeeew.sigh.infra;

final class SighLocationOffsetCalculator {

    private static final double RADIUS_METERS = 300.0;

    private SighLocationOffsetCalculator() {
    }

    static Offset calculate(double radialUniform, double angularUniform) {
        if (!(radialUniform >= 0 && radialUniform < 1 && angularUniform >= 0 && angularUniform < 1)) {
            throw new IllegalArgumentException("난수는 0 이상 1 미만이어야 합니다.");
        }

        // 반경 자체가 아닌 원의 면적에 균등하게 분포하도록 제곱근을 적용해요.
        double radius = RADIUS_METERS * Math.sqrt(radialUniform);
        double angle = 2 * Math.PI * angularUniform;
        return Offset.of(radius * Math.cos(angle), radius * Math.sin(angle));
    }

    record Offset(double eastingMeters, double northingMeters) {

        static Offset of(double eastingMeters, double northingMeters) {
            return new Offset(eastingMeters, northingMeters);
        }
    }
}
