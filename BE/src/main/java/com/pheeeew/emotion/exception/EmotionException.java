package com.pheeeew.emotion.exception;

import com.pheeeew.common.exception.PheeeewException;
import java.time.Duration;

public class EmotionException extends PheeeewException {

    public EmotionException(EmotionErrorCode errorCode) {
        super(errorCode, null);
    }

    public EmotionException(EmotionErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public EmotionException(EmotionErrorCode errorCode, Duration retryAfter) {
        super(errorCode, null, retryAfter);
    }
}
