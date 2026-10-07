package com.pheeeew.device.domain;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NICKNAME_INVALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.device.exception.DeviceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class DeviceNicknameTest {

    @ParameterizedTest
    @ValueSource(strings = {"가", "Star K", "가나다라마바사아자차", "ㄱㅏ", "잠에서 깨는 너구리"})
    void 한글_영문_공백과_길이_경계를_허용한다(String nickname) {
        // given / when
        DeviceNickname result = DeviceNickname.from(nickname);

        // then
        assertThat(result.value()).isEqualTo(nickname);
    }

    @Test
    void 앞뒤_공백은_제거하고_중간_공백과_영문_대소문자는_보존한다() {
        // given / when
        DeviceNickname nickname = DeviceNickname.from("  Star  K  ");

        // then
        assertThat(nickname.value()).isEqualTo("Star  K");
    }

    @Test
    void 길이는_앞뒤_공백을_제거한_뒤_판단한다() {
        // given / when
        DeviceNickname nickname = DeviceNickname.from("  가나다라 마바사아자  ");

        // then
        assertThat(nickname.value()).isEqualTo("가나다라 마바사아자");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "", "   ", "익명", " 익명 ", "스타1", "스타!", "abcdefghijk",
            "スター", "😀", "Star\tK", "Star\nK", "\tStark", "Stark\n", "\u2003Stark", "Stark\u00a0"
    })
    void 규칙을_위반한_닉네임을_거부한다(String nickname) {
        // given / when / then
        assertThatThrownBy(() -> DeviceNickname.from(nickname))
                .isInstanceOfSatisfying(DeviceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(DEVICE_NICKNAME_INVALID));
    }
}
