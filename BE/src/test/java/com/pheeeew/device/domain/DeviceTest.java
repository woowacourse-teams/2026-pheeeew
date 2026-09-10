package com.pheeeew.device.domain;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceTest {

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
