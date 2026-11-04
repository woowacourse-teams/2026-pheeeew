package com.pheeeew.notification.domain;

import static com.pheeeew.notification.fixture.PushTokenFixture.기기_등록_값;
import static com.pheeeew.notification.fixture.PushTokenFixture.기본_등록_값;
import static com.pheeeew.notification.fixture.PushTokenFixture.새_등록_값;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class DevicePushTokenTest {

    private static final String 최대_길이_값 = "a".repeat(1024);
    private static final String 너무_긴_값 = "a".repeat(1025);

    @Test
    void 등록_값은_최대_1024자까지_받는다() {
        // when
        DevicePushToken pushToken = 기기_등록_값(1L, 최대_길이_값);

        // then
        assertThat(pushToken.getToken()).hasSize(1024);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 비었거나_없는_등록_값은_받지_않는다(String token) {
        // when / then
        assertThatThrownBy(() -> 기기_등록_값(1L, token)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 최대_길이를_넘는_등록_값이나_기기_없는_등록은_받지_않는다() {
        // when / then
        assertThatThrownBy(() -> 기기_등록_값(1L, 너무_긴_값)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> 기기_등록_값(null, 기본_등록_값)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> 기기_등록_값(0L, 기본_등록_값)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 등록_값을_바꾸면_발송_대상도_새_값을_가리킨다() {
        // given
        DevicePushToken pushToken = 기기_등록_값(1L, 기본_등록_값);

        // when
        pushToken.changeToken(새_등록_값);

        // then
        assertThat(pushToken.toTarget()).isEqualTo(PushTarget.of(1L, 새_등록_값));
    }

    @Test
    void 잘못된_값으로_바꾸려_하면_이전_값을_유지한다() {
        // given
        DevicePushToken pushToken = 기기_등록_값(1L, 기본_등록_값);

        // when / then
        assertThatThrownBy(() -> pushToken.changeToken("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pushToken.changeToken(너무_긴_값)).isInstanceOf(IllegalArgumentException.class);
        assertThat(pushToken.getToken()).isEqualTo(기본_등록_값);
    }
}
