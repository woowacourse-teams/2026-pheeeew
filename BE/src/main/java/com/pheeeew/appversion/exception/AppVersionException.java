package com.pheeeew.appversion.exception;

import com.pheeeew.common.exception.PheeeewException;

public class AppVersionException extends PheeeewException {

    public AppVersionException(AppVersionErrorCode errorCode) {
        super(errorCode, null);
    }
}
