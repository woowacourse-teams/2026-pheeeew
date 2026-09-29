package com.pheeeew.emotion.exception;

import com.pheeeew.common.exception.PheeeewException;

public class EmotionException extends PheeeewException {

    public EmotionException(EmotionErrorCode errorCode) {
        super(errorCode, null);
    }

    public EmotionException(EmotionErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
