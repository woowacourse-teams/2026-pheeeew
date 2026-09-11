package com.pheeeew.sigh.exception;

import com.pheeeew.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SighErrorCode implements ErrorCode {

    SIGH_SAVE_FAILED("SIGH-001", "한숨을 저장하지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    SIGH_NOT_FOUND("SIGH-002", "한숨을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    SIGH_INVALID_CURSOR("SIGH-003", "한숨 목록 커서를 사용할 수 없습니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
