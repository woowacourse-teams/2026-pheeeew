package com.pheeeew.appconfig.exception;

import com.pheeeew.common.exception.PheeeewException;

public class AppConfigException extends PheeeewException {

    public AppConfigException(AppConfigErrorCode errorCode) {
        super(errorCode, null);
    }
}
