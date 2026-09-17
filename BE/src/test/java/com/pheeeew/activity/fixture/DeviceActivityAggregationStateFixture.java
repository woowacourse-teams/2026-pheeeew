package com.pheeeew.activity.fixture;

import com.pheeeew.activity.domain.DeviceActivityAggregationState;

public final class DeviceActivityAggregationStateFixture {

    private DeviceActivityAggregationStateFixture() {
    }

    public static DeviceActivityAggregationState.DeviceActivityAggregationStateBuilder 기본_집계_상태_빌더() {
        return DeviceActivityAggregationState.builder();
    }
}
