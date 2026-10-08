package com.pheeeew.device.domain;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NICKNAME_INVALID;

import com.pheeeew.device.exception.DeviceException;
import java.util.regex.Pattern;

public record DeviceNickname(String value) {

    // korean-nickname-generator 0.1.1 기본 사전: 수식어 6자 + 공백 1자 + 캐릭터 3자.
    public static final int MAX_LENGTH = 10;

    private static final Pattern ALLOWED_CHARACTERS = Pattern.compile("[가-힣ㄱ-ㅎㅏ-ㅣA-Za-z ]+");

    public DeviceNickname {
        if (value == null || !ALLOWED_CHARACTERS.matcher(value).matches()) {
            throw new DeviceException(DEVICE_NICKNAME_INVALID);
        }

        value = value.strip();
        if (value.isEmpty() || value.length() > MAX_LENGTH || value.equals("익명")) {
            throw new DeviceException(DEVICE_NICKNAME_INVALID);
        }
    }

    public static DeviceNickname from(String nickname) {
        return new DeviceNickname(nickname);
    }
}
