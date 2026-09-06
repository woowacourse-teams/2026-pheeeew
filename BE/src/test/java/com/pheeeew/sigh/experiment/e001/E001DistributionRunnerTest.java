package com.pheeeew.sigh.experiment.e001;

import java.io.IOException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("e001")
@Tag("e001-distribution")
class E001DistributionRunnerTest {

    @Test
    void 분포를_두_번_생성하고_결정적_결과만_승격한다() throws IOException {
        E001Runners.distribution();
    }
}
