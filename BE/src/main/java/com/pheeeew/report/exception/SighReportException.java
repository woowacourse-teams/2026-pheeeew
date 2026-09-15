package com.pheeeew.report.exception;

import com.pheeeew.common.exception.PheeeewException;

public class SighReportException extends PheeeewException {

    public SighReportException(SighReportErrorCode errorCode) {
        super(errorCode, null);
    }

    public SighReportException(SighReportErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
