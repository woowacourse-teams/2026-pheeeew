package com.pheeeew.pressrace.v2.exception;

import com.pheeeew.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PressStreamErrorCode implements ErrorCode {

    PRESS_STREAM_SUBSCRIPTION_LIMIT_EXCEEDED("PRESS_STREAM-001", "같은 기기의 실시간 프레스 구독 수를 넘었습니다.",
            HttpStatus.TOO_MANY_REQUESTS);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
