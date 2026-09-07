package com.pheeeew.sigh.experiment.e001;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class E008AgeStyleExperimentTest {

    @Test
    void 미생성은_숨기고_24시간_경계부터_제외한다() {
        // given / when / then
        assertThat(E008AgeStyleExperiment.alpha(-0.001)).isZero();
        assertThat(E008AgeStyleExperiment.alpha(0)).isEqualTo(1);
        assertThat(E008AgeStyleExperiment.alpha(2)).isEqualTo(1);
        assertThat(E008AgeStyleExperiment.alpha(23.99)).isPositive();
        assertThat(E008AgeStyleExperiment.alpha(24)).isZero();
        assertThat(E008AgeStyleExperiment.alpha(25)).isZero();
        assertThatThrownBy(() -> E008AgeStyleExperiment.alpha(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 감쇠는_증가하지_않고_중간값이_정의와_일치한다() {
        // given
        double previous = 1;
        // when / then
        for (int i = 0; i <= 2400; i++) {
            double current = E008AgeStyleExperiment.alpha(i / 100.0);
            assertThat(current).isBetween(0.0, previous);
            previous = current;
        }
        assertThat(E008AgeStyleExperiment.alpha(13)).isCloseTo(.5, within(1e-12));
    }

    @Test
    void 색상은_지정된_나이를_경유하고_A는_고정한다() {
        // given / when / then
        assertThat(E008AgeStyleExperiment.color(true, 0)).isEqualTo(E008AgeStyleExperiment.BLUE);
        assertThat(E008AgeStyleExperiment.color(true, 12)).isEqualTo(E008AgeStyleExperiment.GOLD);
        assertThat(E008AgeStyleExperiment.color(true, 24)).isEqualTo(E008AgeStyleExperiment.RED);
        for (int age = 0; age <= 24; age++) {
            assertThat(E008AgeStyleExperiment.color(false, age)).isEqualTo(E008AgeStyleExperiment.GOLD);
        }
        assertThatThrownBy(() -> E008AgeStyleExperiment.color(true, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 같은_이벤트의_생성시각은_고정하고_범위를_지킨다() {
        // given / when / then
        for (int i = 0; i < 1000; i++) {
            String identity = "hotspots-4500|2026090701|" + i;
            double actual = E008AgeStyleExperiment.birth(identity);
            assertThat(actual).isEqualTo(E008AgeStyleExperiment.birth(identity));
            assertThat(actual).isBetween(-24.0, Math.nextDown(24.0));
        }
    }
}
