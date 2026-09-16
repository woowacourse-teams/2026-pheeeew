package com.pheeeew.report.exception;

import com.pheeeew.common.exception.PheeeewException;

public class BlockException extends PheeeewException {

    public BlockException(BlockErrorCode errorCode) {
        super(errorCode, null);
    }

    public BlockException(BlockErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
