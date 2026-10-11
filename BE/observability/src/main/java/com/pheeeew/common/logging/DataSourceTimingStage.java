package com.pheeeew.common.logging;

public enum DataSourceTimingStage implements RequestTimingStage {
    DB_CONNECTION_ACQUIRE_SUCCESS,
    DB_CONNECTION_ACQUIRE_FAILED
}
