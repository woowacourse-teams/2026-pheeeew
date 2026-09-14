package com.pheeeew.block.exception;

import com.pheeeew.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BlockErrorCode implements ErrorCode {

    BLOCK_SAVE_FAILED("BLOCK-001", "차단을 저장하지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    BLOCK_SELF_NOT_ALLOWED("BLOCK-002", "자기 자신은 차단할 수 없습니다.", HttpStatus.CONFLICT),
    BLOCK_AUTHOR_UNKNOWN(
            "BLOCK-003",
            "작성자를 알 수 없는 한숨은 사용자 차단을 할 수 없습니다.",
            HttpStatus.CONFLICT
    ),
    BLOCK_INVALID_CURSOR("BLOCK-004", "차단 목록 커서를 사용할 수 없습니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
