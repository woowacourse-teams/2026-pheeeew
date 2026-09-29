package com.pheeeew.report.exception;

import com.pheeeew.common.exception.PheeeewException;

public class EmotionReportException extends PheeeewException {

    public EmotionReportException(EmotionReportErrorCode errorCode) {
        super(errorCode, null);
    }

    public EmotionReportException(EmotionReportErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
