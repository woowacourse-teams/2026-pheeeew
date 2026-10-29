package com.pheeeew.appconfig.exception;

import com.pheeeew.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AppConfigErrorCode implements ErrorCode {
    INVALID_PLATFORM("APP_CONFIG-001", "플랫폼은 android 또는 ios여야 합니다.", HttpStatus.BAD_REQUEST),
    CONFIG_NOT_FOUND("APP_CONFIG-002", "앱 설정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
