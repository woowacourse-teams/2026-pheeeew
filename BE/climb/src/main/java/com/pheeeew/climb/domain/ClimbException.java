package com.pheeeew.climb.domain;

public class ClimbException extends RuntimeException {

    private final ClimbErrorCode code;

    public ClimbException(ClimbErrorCode code) {
        super(code.message());
        this.code = code;
    }

    public ClimbErrorCode code() {
        return code;
    }
}
