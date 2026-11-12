package com.pheeeew.climb.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ClimbNicknameTest {

    @ParameterizedTest
    @ValueSource(strings = {"피유", "climber01", "등반왕Pheeeew", "  감정이  "})
    void 두_자에서_열_자의_한글_영문_숫자는_앞뒤_공백을_지우고_받는다(String raw) {
        // when
        String nickname = ClimbNickname.validate(raw);

        // then
        assertThat(nickname).isEqualTo(raw.strip());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "피", "  a ", "열한글자를넘는닉네임이다"})
    void 길이가_맞지_않으면_길이_사유로_거절한다(String raw) {
        assertThatThrownBy(() -> ClimbNickname.validate(raw))
                .isInstanceOfSatisfying(ClimbException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ClimbErrorCode.NICKNAME_LENGTH));
    }

    @ParameterizedTest
    @ValueSource(strings = {"피 유", "피유!", "ㅋㅋㅋ", "climb_er", "😀😀"})
    void 한글_영문_숫자_밖의_문자는_문자_사유로_거절한다(String raw) {
        assertThatThrownBy(() -> ClimbNickname.validate(raw))
                .isInstanceOfSatisfying(ClimbException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ClimbErrorCode.NICKNAME_CHARACTERS));
    }
}
