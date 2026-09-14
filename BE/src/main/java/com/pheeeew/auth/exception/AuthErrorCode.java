package com.pheeeew.auth.exception;

import com.pheeeew.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    AUTHENTICATION_REQUIRED("AUTH-001", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("AUTH-002", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
