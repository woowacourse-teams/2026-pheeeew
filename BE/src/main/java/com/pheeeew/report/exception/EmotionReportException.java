package com.pheeeew.report.exception;

import com.pheeeew.common.exception.PheeeewException;

public class EmotionReportException extends PheeeewException {

    public EmotionReportException(SighReportErrorCode errorCode) {
        super(errorCode, null);
    }

    public EmotionReportException(SighReportErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
