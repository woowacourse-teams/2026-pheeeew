package com.pheeeew.appversion.exception;

import com.pheeeew.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AppVersionErrorCode implements ErrorCode {
    INVALID_PLATFORM("APP_VERSION-001", "플랫폼은 android 또는 ios여야 합니다.", HttpStatus.BAD_REQUEST),
    POLICY_NOT_FOUND("APP_VERSION-002", "활성 앱 버전 정책을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
