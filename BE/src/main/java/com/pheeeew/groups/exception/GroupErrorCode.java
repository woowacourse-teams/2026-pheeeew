package com.pheeeew.groups.exception;

import com.pheeeew.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GroupErrorCode implements ErrorCode {

    GROUP_NOT_FOUND("GROUP-001", "그룹을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    GROUP_NAME_DUPLICATED("GROUP-002", "이미 사용 중인 그룹 이름입니다.", HttpStatus.CONFLICT),
    GROUP_OWNER_ONLY("GROUP-003", "그룹장만 할 수 있습니다.", HttpStatus.FORBIDDEN),
    GROUP_MEMBER_REMAINS("GROUP-004", "다른 멤버가 남아 있어 그룹을 삭제할 수 없습니다.", HttpStatus.CONFLICT),
    GROUP_INVITE_CODE_UNAVAILABLE("GROUP-005", "초대 코드를 발급하지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    GROUP_OWNER_CANNOT_LEAVE("GROUP-006", "그룹장은 그룹을 나갈 수 없습니다.", HttpStatus.CONFLICT),
    GROUP_ALREADY_JOINED("GROUP-007", "이미 속해 있는 그룹입니다.", HttpStatus.CONFLICT),
    GROUP_MEMBER_ONLY("GROUP-008", "그룹 멤버만 할 수 있습니다.", HttpStatus.FORBIDDEN);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
