package com.pheeeew.common.exception;

import java.time.Duration;
import java.util.Objects;
import lombok.Getter;

@Getter
public class PheeeewException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Duration retryAfter;

    public PheeeewException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, cause, null);
    }

    public PheeeewException(ErrorCode errorCode, Throwable cause, Duration retryAfter) {
        super(Objects.requireNonNull(errorCode).getMessage(), cause);
        this.errorCode = errorCode;
        this.retryAfter = retryAfter;
    }
}
