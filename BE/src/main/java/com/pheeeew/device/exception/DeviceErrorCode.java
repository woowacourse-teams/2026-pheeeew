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
    DEVICE_NOT_FOUND("DEVICE-004", "인증 정보를 사용할 수 없습니다.", HttpStatus.UNAUTHORIZED),
    DEVICE_CHALLENGE_INVALID("DEVICE-005", "무결성 증명 요청 값을 사용할 수 없습니다.", HttpStatus.BAD_REQUEST),
    DEVICE_ATTESTATION_INVALID("DEVICE-006", "무결성 증명을 확인할 수 없습니다.", HttpStatus.FORBIDDEN),
    DEVICE_ATTESTATION_UNAVAILABLE(
            "DEVICE-007",
            "무결성 증명을 지금 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.",
            HttpStatus.SERVICE_UNAVAILABLE
    );

    private final String code;
    private final String message;
    private final HttpStatus status;
}
