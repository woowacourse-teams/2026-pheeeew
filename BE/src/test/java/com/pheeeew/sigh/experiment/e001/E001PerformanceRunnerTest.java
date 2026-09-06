package com.pheeeew.sigh.experiment.e001;

import java.io.IOException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("e001")
@Tag("e001-performance")
class E001PerformanceRunnerTest {

    @Test
    void 고정된_선택_모델의_전체_성능을_측정한다() throws IOException {
        E001Runners.performance();
    }
}
