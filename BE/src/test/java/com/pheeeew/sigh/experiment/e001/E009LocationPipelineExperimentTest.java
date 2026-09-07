package com.pheeeew.sigh.experiment.e001;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class E009LocationPipelineExperimentTest {

    @Test
    void 면적_균등_변환은_중심과_반지름의_제곱에_따른다() {
        // given
        var center = E009LocationPipelineExperiment.XY.of(120, -90);
        // when
        var atCenter = E009LocationPipelineExperiment.disk(center, 0, 0);
        var halfway = E009LocationPipelineExperiment.disk(center, .25, 0);
        // then
        assertThat(atCenter).isEqualTo(center);
        assertThat(halfway.x()).isEqualTo(270);
        assertThat(halfway.y()).isEqualTo(-90);
    }

    @Test
    void 이중_랜덤은_클라이언트_좌표에_서버_오프셋을_추가한다() {
        // given
        var truth = E009LocationPipelineExperiment.XY.of(120, -90);
        var grid = E009LocationPipelineExperiment.XY.of(0, 0);
        var existing = E009LocationPipelineExperiment.XY.of(30, -40);
        // when
        var server = E009LocationPipelineExperiment.route(1, truth, grid, existing, .25, 0, .25, .25);
        var client = E009LocationPipelineExperiment.route(2, truth, grid, existing, .25, 0, .25, .25);
        var old = E009LocationPipelineExperiment.route(3, truth, grid, existing, .25, 0, .25, .25);
        // then
        assertThat(server.sent()).isEqualTo(truth);
        assertThat(server.shown()).isEqualTo(E009LocationPipelineExperiment.XY.of(270, -90));
        assertThat(client.sent().x()).isCloseTo(120, within(1e-9));
        assertThat(client.sent().y()).isCloseTo(60, within(1e-9));
        assertThat(client.shown().x()).isCloseTo(270, within(1e-9));
        assertThat(client.shown().y()).isCloseTo(60, within(1e-9));
        assertThat(old.sent()).isEqualTo(grid);
        assertThat(old.shown()).isEqualTo(existing);
    }

    @Test
    void 유효하지_않은_난수와_좌표를_거부한다() {
        // given
        var center = E009LocationPipelineExperiment.XY.of(0, 0);
        // when / then
        for (double invalid : new double[]{-.1, 1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> E009LocationPipelineExperiment.disk(center, invalid, .5)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> E009LocationPipelineExperiment.disk(center, .5, invalid)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> E009LocationPipelineExperiment.XY.of(Double.NaN, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> E009LocationPipelineExperiment.route(4, center, center, center, 0, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 고정_난수_표본은_반경_300미터와_균등_원_통계를_만족한다() {
        // given
        var center = E009LocationPipelineExperiment.XY.of(0, 0);
        var random = new SplittableRandom(2026090709L);
        double sumSquared = 0;
        int inner = 0;
        // when
        for (int i = 0; i < 100000; i++) {
            var point = E009LocationPipelineExperiment.disk(center, random.nextDouble(), random.nextDouble());
            double squared = point.x() * point.x() + point.y() * point.y();
            assertThat(squared).isBetween(0.0, 90000.0);
            sumSquared += squared;
            if (squared <= 22500) { inner++; }
        }
        // then
        assertThat(sumSquared / 100000).isCloseTo(45000, within(600.0));
        assertThat(inner / 100000.0).isCloseTo(.25, within(.008));
    }

    @Test
    void 독립적인_두_원_오프셋은_제곱거리_기댓값이_더해진다() {
        // given
        var center = E009LocationPipelineExperiment.XY.of(0, 0);
        var server = new SplittableRandom(2026090710L);
        var client = new SplittableRandom(2026090711L);
        double sumSquared = 0;
        // when
        for (int i = 0; i < 100000; i++) {
            var result = E009LocationPipelineExperiment.route(2, center, center, center,
                    server.nextDouble(), server.nextDouble(), client.nextDouble(), client.nextDouble());
            double squared = result.shown().x() * result.shown().x() + result.shown().y() * result.shown().y();
            assertThat(squared).isBetween(0.0, 360000.0);
            sumSquared += squared;
        }
        // then
        assertThat(sumSquared / 100000).isCloseTo(90000, within(1800.0));
    }
}
