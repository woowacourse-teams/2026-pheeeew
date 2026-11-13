package com.pheeeew.climb.domain;

import java.util.regex.Pattern;

public final class ClimbNickname {

    private static final Pattern ALLOWED = Pattern.compile("[가-힣a-zA-Z0-9]+");

    private ClimbNickname() {
    }

    public static String validate(String raw) {
        return validate(raw, 2);
    }

    public static String validate(String raw, int minLength) {
        String nickname = raw == null ? "" : raw.strip();
        int length = nickname.codePointCount(0, nickname.length());
        if (length < minLength || length > 10) {
            throw new ClimbException(ClimbErrorCode.NICKNAME_LENGTH);
        }
        if (!ALLOWED.matcher(nickname).matches()) {
            throw new ClimbException(ClimbErrorCode.NICKNAME_CHARACTERS);
        }
        return nickname;
    }
}
