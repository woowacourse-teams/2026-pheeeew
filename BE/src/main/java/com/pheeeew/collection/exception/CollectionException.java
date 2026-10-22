package com.pheeeew.collection.exception;

import com.pheeeew.common.exception.PheeeewException;

public class CollectionException extends PheeeewException {

    public CollectionException(CollectionErrorCode errorCode) {
        super(errorCode, null);
    }
}
