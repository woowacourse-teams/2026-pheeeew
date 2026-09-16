package com.pheeeew.appversion.domain;

import static com.pheeeew.appversion.exception.AppVersionErrorCode.INVALID_PLATFORM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.pheeeew.appversion.exception.AppVersionException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class AppPlatformTest {

    @ParameterizedTest
    @CsvSource({
            "android, ANDROID",
            "ANDROID, ANDROID",
            "AnDrOiD, ANDROID",
            "ios, IOS",
            "IOS, IOS",
            "iOs, IOS"
    })
    void 대소문자를_구분하지_않고_플랫폼을_변환한다(String value, AppPlatform expected) {
        // given / when
        AppPlatform platform = AppPlatform.from(value);

        // then
        assertThat(platform).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "web", "windows", "android,ios", " android "})
    void 지원하지_않는_플랫폼은_잘못된_플랫폼_오류로_거부한다(String value) {
        // given / when / then
        assertThatExceptionOfType(AppVersionException.class)
                .isThrownBy(() -> AppPlatform.from(value))
                .satisfies(exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(INVALID_PLATFORM);
                    assertThat(exception.getErrorCode().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }
}
