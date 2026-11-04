package com.pheeeew.pressrace.v2.exception;

import com.pheeeew.common.exception.PheeeewException;

public class PressStreamException extends PheeeewException {

    public PressStreamException(PressStreamErrorCode errorCode) {
        super(errorCode, null);
    }
}
