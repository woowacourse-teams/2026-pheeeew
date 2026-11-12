package com.pheeeew.climb.domain;

public enum ClimbErrorCode {

    INVALID_REQUEST(400, "요청 형식이 올바르지 않다."),
    NICKNAME_LENGTH(400, "닉네임은 2자 이상 10자 이하여야 한다."),
    NICKNAME_CHARACTERS(400, "닉네임에는 한글, 영문, 숫자만 쓸 수 있다."),
    INVALID_BOARD(400, "순위표 종류는 online, today, all 중 하나여야 한다."),
    UNAUTHORIZED(401, "손님 쿠키가 없거나 올바르지 않다."),
    ADMIN_KEY_MISMATCH(401, "관리자 키가 맞지 않다."),
    ADMIN_UNAUTHORIZED(401, "관리자 세션이 없거나 끝났다."),
    ADMIN_DISABLED(404, "관리자 기능이 꺼져 있다."),
    FORBIDDEN(403, "허용되지 않은 요청이다."),
    GUEST_CREATE_LIMITED(429, "손님 만들기 한도를 넘었다. 잠시 뒤 다시 시도한다."),
    NICKNAME_CHANGE_LIMITED(429, "닉네임은 하루에 3번까지 바꿀 수 있다."),
    ADMIN_ATTEMPTS_LIMITED(429, "관리자 키를 너무 많이 틀렸다. 잠시 뒤 다시 시도한다.");

    private final int status;
    private final String message;

    ClimbErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public int status() {
        return status;
    }

    public String message() {
        return message;
    }
}
