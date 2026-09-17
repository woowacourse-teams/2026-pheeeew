package com.pheeeew.activity.domain.repository.projection;

import com.pheeeew.device.domain.DevicePlatform;

public interface DeviceActivityCountProjection {

    DevicePlatform getPlatform();

    long getDau();

    long getMau();
}
