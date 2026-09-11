package com.pheeeew.sigh.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SighSearchBoundsTest {

    @Test
    void 일반_검색_영역을_생성한다() {
        // given / when
        SighSearchBounds bounds = SighSearchBounds.of(126.9, 37.5, 127.1, 37.6);

        // then
        assertThat(bounds.minLongitude()).isEqualTo(126.9);
        assertThat(bounds.minLatitude()).isEqualTo(37.5);
        assertThat(bounds.maxLongitude()).isEqualTo(127.1);
        assertThat(bounds.maxLatitude()).isEqualTo(37.6);
    }

    @Test
    void 날짜변경선을_가로지르는_검색_영역을_생성한다() {
        // given / when / then
        assertThatCode(() -> SighSearchBounds.of(170.0, -10.0, -170.0, 10.0))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("올바르지_않은_검색_영역들")
    void 올바르지_않은_검색_영역은_거부한다(
            double minLongitude,
            double minLatitude,
            double maxLongitude,
            double maxLatitude
    ) {
        // given / when / then
        assertThatThrownBy(() -> SighSearchBounds.of(
                minLongitude,
                minLatitude,
                maxLongitude,
                maxLatitude
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<Arguments> 올바르지_않은_검색_영역들() {
        return Stream.of(
                Arguments.of(-180.1, 37.5, 127.1, 37.6),
                Arguments.of(126.9, 37.5, 180.1, 37.6),
                Arguments.of(126.9, -90.1, 127.1, 37.6),
                Arguments.of(126.9, 37.5, 127.1, 90.1),
                Arguments.of(126.9, 37.5, 126.9, 37.6),
                Arguments.of(126.9, 37.6, 127.1, 37.5),
                Arguments.of(Double.NaN, 37.5, 127.1, 37.6),
                Arguments.of(126.9, 37.5, Double.POSITIVE_INFINITY, 37.6)
        );
    }
}
