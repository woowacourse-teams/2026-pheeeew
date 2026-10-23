package com.pheeeew.collection.exception;

import com.pheeeew.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CollectionErrorCode implements ErrorCode {
    COLLECTION_DATE_CHANGED("COLLECTION-001", "조회 중 날짜가 변경되었습니다. 잠시 후 다시 시도해 주세요.",
            HttpStatus.SERVICE_UNAVAILABLE),
    COLLECTION_CLAIM_DATE_INVALID("COLLECTION-002", "카드는 한국 시간 기준 당일에만 새로 수령할 수 있습니다.",
            HttpStatus.BAD_REQUEST),
    COLLECTION_REQUIREMENTS_NOT_MET("COLLECTION-003", "카드 수령에 필요한 활동 조건을 충족하지 못했습니다.",
            HttpStatus.CONFLICT),
    COLLECTION_ASSETS_NOT_READY("COLLECTION-004", "카드 자산을 준비 중입니다. 잠시 후 다시 시도해 주세요.",
            HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
