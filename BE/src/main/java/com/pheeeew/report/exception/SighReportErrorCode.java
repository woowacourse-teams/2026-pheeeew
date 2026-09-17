package com.pheeeew.report.exception;

import com.pheeeew.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SighReportErrorCode implements ErrorCode {

    SIGH_REPORT_SAVE_FAILED("REPORT-001", "신고를 저장하지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    SIGH_REPORT_SELF_NOT_ALLOWED("REPORT-002", "자기 한숨은 신고할 수 없습니다.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
