package com.pheeeew.sigh.experiment.e001;

import java.io.IOException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("e001")
@Tag("e001-blind")
class E001BlindRunnerTest {

    @Test
    void 평가자용_네_쌍과_빈_응답지를_생성한다() throws IOException {
        E001Runners.blind();
    }
}
