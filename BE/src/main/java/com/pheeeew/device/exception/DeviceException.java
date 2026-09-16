package com.pheeeew.device.exception;

import com.pheeeew.common.exception.PheeeewException;
import java.time.Duration;

public class DeviceException extends PheeeewException {

    public DeviceException(DeviceErrorCode errorCode) {
        super(errorCode, null);
    }

    public DeviceException(DeviceErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public DeviceException(DeviceErrorCode errorCode, Throwable cause, Duration retryAfter) {
        super(errorCode, cause, retryAfter);
    }
}
