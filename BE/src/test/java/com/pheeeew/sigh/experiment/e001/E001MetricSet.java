package com.pheeeew.sigh.experiment.e001;

import java.util.Map;

/** NaN은 산출물에서 undefined로 기록할 값이에요. 누락된 값도 판정 시 NaN으로 다뤄요. */
record E001MetricSet(Map<String, Double> values) {

    E001MetricSet {
        values = Map.copyOf(values);
    }

    static E001MetricSet from(Map<String, Double> values) {
        return new E001MetricSet(values);
    }

    double value(String key) {
        return values.getOrDefault(key, Double.NaN);
    }
}
