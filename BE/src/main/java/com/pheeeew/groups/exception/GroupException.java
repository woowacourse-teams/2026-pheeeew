package com.pheeeew.groups.exception;

import com.pheeeew.common.exception.PheeeewException;

public class GroupException extends PheeeewException {

    public GroupException(GroupErrorCode errorCode) {
        super(errorCode, null);
    }

    public GroupException(GroupErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
