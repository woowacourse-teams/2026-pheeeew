package com.pheeeew.collection.exception;

import com.pheeeew.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CollectionErrorCode implements ErrorCode {
    COLLECTION_DATE_CHANGED("COLLECTION-001", "조회 중 날짜가 변경되었습니다. 잠시 후 다시 시도해 주세요.",
            HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
