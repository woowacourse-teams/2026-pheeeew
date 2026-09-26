package com.pheeeew.report.domain;

import static com.pheeeew.report.fixture.EmotionReportFixture.기본_신고_빌더;
import static com.pheeeew.report.fixture.EmotionReportFixture.신고_사유;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class EmotionReportTest {

    @Test
    void 신고_사유의_앞뒤_공백을_제거해_보관한다() {
        // given
        String reason = "  광고성 게시물입니다\n";

        // when
        EmotionReport report = 기본_신고_빌더()
                .reason(reason)
                .build();

        // then
        assertThat(report.getReason()).isEqualTo("광고성 게시물입니다");
    }

    @Test
    void 신고_사유는_200자까지_허용한다() {
        // given
        String reason = 신고_사유(200);

        // when
        EmotionReport report = 기본_신고_빌더()
                .reason(reason)
                .build();

        // then
        assertThat(report.getReason()).hasSize(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void 신고_사유가_공백뿐이면_생성할_수_없다(String reason) {
        // given / when
        Throwable throwable = catchThrowable(() -> 기본_신고_빌더()
                .reason(reason)
                .build()
        );

        // then
        assertThat(throwable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("신고 사유는 공백이 아닌 200자 이하의 값이어야 합니다.");
    }

    @Test
    void 신고_사유가_200자를_초과하면_생성할_수_없다() {
        // given
        String reason = 신고_사유(201);

        // when
        Throwable throwable = catchThrowable(() -> 기본_신고_빌더()
                .reason(reason)
                .build()
        );

        // then
        assertThat(throwable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("신고 사유는 공백이 아닌 200자 이하의 값이어야 합니다.");
    }

    @ParameterizedTest
    @MethodSource("필수값이_빠진_신고들")
    void 신고_대상과_신고자와_사유는_비어_있을_수_없다(EmotionReport.EmotionReportBuilder builder) {
        // given / when
        Throwable throwable = catchThrowable(builder::build);

        // then
        assertThat(throwable).isInstanceOf(NullPointerException.class);
    }

    private static Stream<Arguments> 필수값이_빠진_신고들() {
        return Stream.of(
                Arguments.of(기본_신고_빌더().emotionId(null)),
                Arguments.of(기본_신고_빌더().reporterDeviceId(null)),
                Arguments.of(기본_신고_빌더().reason(null))
        );
    }
}
