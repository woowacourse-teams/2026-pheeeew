package com.pheeeew.pressevent.exception;

import com.pheeeew.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PressEventErrorCode implements ErrorCode {
    EVENT_NOT_FOUND("PRESS-EVENT-001", "프레스 이벤트를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    EVENT_NOT_OPEN("PRESS-EVENT-002", "프레스 이벤트 결과 제출 기간이 아닙니다.", HttpStatus.CONFLICT),
    REQUEST_CONFLICT("PRESS-EVENT-003", "같은 도전 식별자로 다른 프레스 횟수가 제출되었습니다.", HttpStatus.CONFLICT),
    INVALID_COUNTS("PRESS-EVENT-004", "다섯 감정의 횟수는 0 이상이며 합계가 400 이하여야 합니다.", HttpStatus.BAD_REQUEST),
    EVENT_RATE_LIMITED("PRESS-EVENT-005", "최근 20초 동안의 이벤트 프레스 한도를 넘었습니다.", HttpStatus.TOO_MANY_REQUESTS),
    INVALID_CURSOR("PRESS-EVENT-006", "유효하지 않은 프레스 이벤트 순위 커서입니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
