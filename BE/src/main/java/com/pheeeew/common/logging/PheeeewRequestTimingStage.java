package com.pheeeew.common.logging;

public enum PheeeewRequestTimingStage implements RequestTimingStage {
    REGIONS_BOUNDARIES,
    REGIONS_INTERSECTION,
    REGIONS_AGGREGATION_READY,
    REGIONS_SUMMARY,
    REGION_EMD_CODE,
    PRESS_INCREASE,
    PRESS_MY_DAILY,
    PRESS_DAILY_TOTAL,
    REGIONS_SERVICE,
    PRESS_SERVICE,
    PRESS_MY_SERVICE,
    PRESS_TOTAL_SERVICE
}
