package com.pheeeew.report.domain;

import static com.pheeeew.report.fixture.BlockFixture.기본_사용자_차단_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DeviceBlockTest {

    private static final Long 기기_식별자 = 1L;

    @Test
    void 자기_자신을_차단하는_사용자_차단은_만들_수_없다() {
        // given
        DeviceBlock.DeviceBlockBuilder builder = 기본_사용자_차단_빌더()
                .blockerDeviceId(기기_식별자)
                .blockedDeviceId(기기_식별자);

        // when / then
        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("자기 자신을 차단할 수 없습니다.");
    }

    @Test
    void 서로_다른_기기를_차단하는_사용자_차단은_만들_수_있다() {
        // given
        DeviceBlock.DeviceBlockBuilder builder = 기본_사용자_차단_빌더()
                .blockerDeviceId(기기_식별자)
                .blockedDeviceId(2L);

        // when
        DeviceBlock block = builder.build();

        // then
        assertThat(block.getBlockerDeviceId()).isEqualTo(기기_식별자);
        assertThat(block.getBlockedDeviceId()).isEqualTo(2L);
        assertThat(block.getOriginEmotionId()).isNotNull();
    }
}
