package com.pheeeew.device.exception;

import com.pheeeew.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DeviceErrorCode implements ErrorCode {

    DEVICE_SAVE_FAILED("DEVICE-001", "기기를 등록하지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    DEVICE_REGISTRATION_WINDOW_EXPIRED(
            "DEVICE-002",
            "기기 등록 재시도 시간이 지났습니다. 새 요청으로 등록해 주세요.",
            HttpStatus.CONFLICT
    ),
    DEVICE_REFRESH_TOKEN_INVALID("DEVICE-003", "인증 정보를 사용할 수 없습니다.", HttpStatus.UNAUTHORIZED),
    DEVICE_NOT_FOUND("DEVICE-004", "인증 정보를 사용할 수 없습니다.", HttpStatus.UNAUTHORIZED);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
