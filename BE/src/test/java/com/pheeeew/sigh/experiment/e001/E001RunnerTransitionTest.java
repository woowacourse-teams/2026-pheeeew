package com.pheeeew.sigh.experiment.e001;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class E001RunnerTransitionTest {

    @ParameterizedTest
    @ValueSource(strings = {"distribution", "performance", "blind"})
    void v2_전환_중에는_경로와_환경을_읽기_전에_세_실행기를_차단한다(String runner) {
        // when & then
        assertThatThrownBy(() -> {
            switch (runner) {
                case "distribution" -> E001Runners.distribution();
                case "performance" -> E001Runners.performance();
                case "blind" -> E001Runners.blind();
                default -> throw new AssertionError(runner);
            }
        }).isInstanceOf(IOException.class).hasMessageContaining("E001-v2 실행기 전환이 끝나지 않았어요");
    }
}
