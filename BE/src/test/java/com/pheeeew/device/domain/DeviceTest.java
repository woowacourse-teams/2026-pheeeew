package com.pheeeew.device.domain;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DeviceTest {

    @Test
    void 닉네임을_수정할_때_앞뒤_공백만_제거한다() {
        // given
        Device device = 기본_기기_빌더().nickname("스타크").build();

        // when
        device.updateNickname("  Star  K  ");

        // then
        assertThat(device.getNickname()).isEqualTo("Star  K");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"익명", "Star1", "abcdefghijk"})
    void 유효하지_않은_닉네임으로_수정하면_기존_닉네임을_유지한다(String nickname) {
        // given
        Device device = 기본_기기_빌더().nickname("스타크").build();

        // when / then
        assertThatThrownBy(() -> device.updateNickname(nickname))
                .isInstanceOfSatisfying(DeviceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NICKNAME_INVALID));
        assertThat(device.getNickname()).isEqualTo("스타크");
    }

    @Test
    void 기기를_만들면_공개_식별자가_자동으로_채워진다() {
        // given / when
        Device device = 기본_기기_빌더().build();

        // then
        assertThat(device.getPublicId()).isNotNull();
    }

    @Test
    void 서로_다른_기기는_서로_다른_공개_식별자를_가진다() {
        // given / when
        Device device = 기본_기기_빌더().build();
        Device otherDevice = 기본_기기_빌더().build();

        // then
        assertThat(device.getPublicId()).isNotEqualTo(otherDevice.getPublicId());
    }

    @Test
    void 요청_식별자가_없으면_기기를_만들_수_없다() {
        // given / when / then
        assertThatThrownBy(() -> Device.builder()
                .requestId(null)
                .platform(DevicePlatform.ANDROID)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void 플랫폼이_없으면_기기를_만들_수_없다() {
        // given / when / then
        assertThatThrownBy(() -> Device.builder()
                .requestId(UUID.randomUUID())
                .platform(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }
}
