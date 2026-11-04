package com.pheeeew.notification.domain;

import static com.pheeeew.notification.fixture.NotificationSettingFixture.기기_수신_설정;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DeviceNotificationSettingTest {

    @Test
    void 설정을_바꾸면_발송기가_읽는_설정도_두_알림_모두_바뀐다() {
        // given
        DeviceNotificationSetting setting = 기기_수신_설정(1L, NotificationSettings.of(false, true));

        // when
        setting.update(NotificationSettings.of(true, false));

        // then
        assertThat(setting.toSettings()).isEqualTo(NotificationSettings.of(true, false));
    }

    @Test
    void 기기나_설정_값이_없으면_만들거나_바꾸지_않는다() {
        // given
        DeviceNotificationSetting setting = 기기_수신_설정(1L, NotificationSettings.of(false, true));

        // when / then
        assertThatThrownBy(() -> 기기_수신_설정(null, NotificationSettings.defaults()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> 기기_수신_설정(1L, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> setting.update(null)).isInstanceOf(NullPointerException.class);
        assertThat(setting.toSettings()).isEqualTo(NotificationSettings.of(false, true));
    }
}
