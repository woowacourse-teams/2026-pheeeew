package com.pheeeew.sigh.experiment.e001;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class E001MetricsTest {

    @Test
    void quantile은_nearest_rank이고_median은_중앙_두_값의_평균이다() {
        // given
        double[] values = {40.0, 10.0, 30.0, 20.0};

        // when & then
        assertThat(E001Statistics.quantile(values, 0.5)).isEqualTo(20.0);
        assertThat(E001Statistics.quantile(values, 0.99)).isEqualTo(40.0);
        assertThat(E001Statistics.median(values)).isEqualTo(25.0);
        assertThat(E001Statistics.median(new double[]{30.0, 10.0, 20.0})).isEqualTo(20.0);
        assertThat(E001Statistics.populationStd(values)).isEqualTo(StrictMath.sqrt(125.0));
        assertThat(values).containsExactly(40.0, 10.0, 30.0, 20.0);
    }

    @Test
    void 통계는_빈_표본과_nonfinite를_undefined로_처리한다() {
        // given
        List<double[]> invalid = List.of(new double[0], new double[]{1.0, Double.NaN},
                new double[]{Double.POSITIVE_INFINITY}, new double[]{Double.NEGATIVE_INFINITY});

        // when & then
        for (double[] values : invalid) {
            assertThat(E001Statistics.quantile(values, 0.95)).isNaN();
            assertThat(E001Statistics.median(values)).isNaN();
            assertThat(E001Statistics.populationStd(values)).isNaN();
        }
        assertThatThrownBy(() -> E001Statistics.quantile(new double[]{1.0}, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"true, -150, 0, true", "true, 150, 0, false", "true, 0, -150, true",
            "true, 0, 150, false", "false, 299.999, 0, true", "false, 300, 0, false",
            "false, 0, 0, true", "false, 299, 299, false"})
    void support는_사각형과_원형의_half_open_경계를_검사한다(boolean square, double x, double y, boolean valid) {
        // given
        E001Sample sample = offset(0, x, y);

        // when & then
        assertThat(E001ShapeMetrics.valid(sample, square)).isEqualTo(valid);
        assertThat(E001ShapeMetrics.measure(List.of(sample), square, 0.0, 0.0).value("radius.violations"))
                .isEqualTo(valid ? 0.0 : 1.0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void edge_ratio는_정확한_띠_경계와_면적과_반개_pseudocount를_사용한다(boolean square) {
        // given
        double boundary = square ? 150.0 : 300.0;
        List<E001Sample> samples = List.of(offset(0, boundary - 1.0, 0.0),
                offset(1, boundary - 30.0, 0.0), offset(2, boundary - 60.0, 0.0));
        double outerArea = square ? 32_400.0 : StrictMath.PI * 17_100.0;
        double innerArea = square ? 25_200.0 : StrictMath.PI * 15_300.0;

        // when
        double ratio = E001ShapeMetrics.measure(samples, square, 0.0, 0.0).value("edgeRatio30");

        // then
        assertThat(ratio).isEqualTo((1.5 / outerArea) / (1.5 / innerArea));
    }

    @Test
    void a4는_영점과_편향_보정을_고정하고_입력_순서에_영향받지_않는다() {
        // given
        List<E001Sample> samples = List.of(offset(3, 10.0, 0.0), offset(2, -10.0, 0.0),
                offset(1, 0.0, 10.0), offset(0, -0.0, -0.0));
        List<E001Sample> reversed = new ArrayList<>(samples);
        Collections.reverse(reversed);

        // when
        E001MetricSet result = E001ShapeMetrics.measure(samples, false, 0.0, 0.0);

        // then
        assertThat(result.value("a4")).isEqualTo(StrictMath.sqrt(0.75));
        assertThat(result).isEqualTo(E001ShapeMetrics.measure(reversed, false, 0.0, 0.0));
    }

    @Test
    void seam은_경계_교차점과_접선_half_open_구간을_처리한다() {
        // given
        List<E001Sample> samples = List.of(at(0, -150.0, -150.0), at(1, -165.0, 0.0),
                at(2, -135.0, 0.0), at(3, -150.0, 450.0), at(4, -150.0, -450.0));

        // when
        double seam = E001ShapeMetrics.measure(samples, false, 0.0, 0.0).value("seam300");

        // then
        // x=-150은 L=1.5/R=2.5, y=-150은 L=0.5/R=1.5이고 나머지는 대칭이에요.
        assertThat(seam).isEqualTo((0.5 + 0.0 + 1.0 + 0.0) / 4.0);
    }

    @Test
    void radial_KS는_같은_반경을_모두_소비한_CDF를_비교한다() {
        // given
        List<E001Sample> d = List.of(offset(0, 0.0, 0.0), offset(1, 1.0, 0.0), offset(2, 1.0, 0.0));
        List<E001Sample> e = List.of(offset(0, 1.0, 0.0), offset(1, 1.0, 0.0), offset(2, 2.0, 0.0));

        // when & then
        assertThat(E001ShapeMetrics.radialKs(d, e)).isCloseTo(1.0 / 3.0, within(1.0e-15));
        assertThat(E001ShapeMetrics.radialKs(d, d)).isZero();
        assertThat(E001ShapeMetrics.radialKs(d, List.of())).isNaN();
        assertThatThrownBy(() -> E001ShapeMetrics.radialKs(d,
                List.of(E001Sample.of(2L, "0:0", 0, 0.0, 0.0, E001Offset.of(0.0, 0.0, 0.0)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 형태_지표에_여러_seed를_섞지_않는다() {
        // given
        List<E001Sample> samples = List.of(offset(0, 0.0, 0.0),
                E001Sample.of(2L, "0:0", 0, 0.0, 0.0, E001Offset.of(0.0, 0.0, 0.0)));

        // when & then
        assertThatThrownBy(() -> E001ShapeMetrics.measure(samples, false, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void log_intensity는_고정된_1681개_좌표의_모집단_표준편차다() {
        // given
        List<String> visits = new ArrayList<>();

        // when
        double result = E001ShapeMetrics.logIntensityStd((x, y) -> {
            visits.add(x + ":" + y);
            return (x - 1_000.0) / 600.0;
        }, 0.7, 1_000.0, 2_000.0);

        // then
        assertThat(visits).hasSize(1_681);
        assertThat(visits.getFirst()).isEqualTo("400.0:1400.0");
        assertThat(visits.get(41)).isEqualTo("400.0:1430.0");
        assertThat(visits.getLast()).isEqualTo("1600.0:2600.0");
        assertThat(result).isCloseTo(0.7 * StrictMath.sqrt(0.35), within(1.0e-14));
    }

    @Test
    void 밀도_공간_탐색은_음수_cell과_buffer를_포함한_전수_비교와_같다() {
        // given
        List<E001Sample> pool = new ArrayList<>();
        for (int index = 0; index < 200; index++) {
            pool.add(at(index, (index * 37 % 653) - 326.0, (index * 67 % 647) - 323.0));
        }
        pool.add(at(200, -299.0, 0.0));
        pool.add(at(201, -301.0, 0.0));
        // 같은 위치여도 seed가 다르면 이웃이에요.
        pool.add(E001Sample.of(2L, "0:0", 200, -299.0, 0.0, E001Offset.of(0.0, 0.0, 0.0)));
        Collections.reverse(pool);

        // when
        E001MetricSet actual = E001DensityMetrics.measure(pool, 0.0, 0.0, 300);

        // then
        E001MetricSet expected = bruteForce(pool);
        for (String key : List.of("proximity8", "proximity16", "proximity24", "neighbors10")) {
            assertThat(actual.value(key)).as(key).isEqualTo(expected.value(key));
        }
    }

    @ParameterizedTest
    @CsvSource({"7.999, 1, 1, 1, 1", "8, 0, 1, 1, 1", "10, 0, 1, 1, 0",
            "16, 0, 0, 1, 0", "24, 0, 0, 0, 0"})
    void 근접_임계값은_미만이고_자기_자신은_제외한다(double distance, double p8, double p16, double p24, double neighbors) {
        // given
        List<E001Sample> samples = List.of(at(0, -1.0, 0.0), at(1, distance - 1.0, 0.0));

        // when
        E001MetricSet result = E001DensityMetrics.measure(samples, 0.0, 0.0, 300);

        // then
        assertThat(result.value("proximity8")).isEqualTo(p8);
        assertThat(result.value("proximity16")).isEqualTo(p16);
        assertThat(result.value("proximity24")).isEqualTo(p24);
        assertThat(result.value("neighbors10")).isEqualTo(neighbors);
    }

    @Test
    void hotspot은_절대_50미터_cell과_빈_cell과_평가_창_경계를_반영한다() {
        // given
        List<E001Sample> samples = List.of(at(0, -300.0, -300.0), at(1, -250.001, -300.0),
                at(2, -250.0, -300.0), at(3, 300.0, 0.0), at(4, 0.0, 300.0));

        // when
        E001MetricSet result = E001DensityMetrics.measure(samples, 0.0, 0.0, 300);

        // then
        assertThat(result.value("hotspot50.p99")).isEqualTo(1.0);
        assertThat(result.value("hotspot50.max")).isEqualTo(2.0);
        double mean = 3.0 / 144.0;
        assertThat(result.value("hotspot50.cv"))
                .isCloseTo(StrictMath.sqrt(5.0 / 144.0 - mean * mean) / mean, within(1.0e-12));
        assertThat(E001DensityMetrics.measure(List.of(), 0.0, 0.0, 300).value("hotspot50.cv")).isNaN();
    }

    @Test
    void 무결성은_실패_개수와_중복과_누락과_support_위반을_탈락시킨다() {
        // given
        Map<E001Integrity.Shard, Long> requested = Map.of(E001Integrity.Shard.of(1L, "0:0"), 2L);
        List<E001Sample> valid = List.of(offset(0, 10.0, 0.0), offset(1, 20.0, 0.0));

        // when & then
        assertThat(E001Integrity.passes(requested, valid, 0, false)).isTrue();
        assertThat(E001Integrity.passes(requested, valid, 1, false)).isFalse();
        assertThat(E001Integrity.passes(requested, List.of(valid.getFirst()), 0, false)).isFalse();
        assertThat(E001Integrity.passes(requested, List.of(valid.getFirst(), valid.getFirst()), 0, false)).isFalse();
        assertThat(E001Integrity.passes(requested, List.of(valid.getFirst(), offset(1, 300.0, 0.0)), 0, false)).isFalse();
        assertThat(E001Integrity.passes(requested, List.of(valid.getFirst(), offset(2, 20.0, 0.0)), 0, false)).isFalse();
        assertThat(E001Integrity.passes(requested, List.of(valid.getFirst(), offset(1, Double.NaN, 0.0)), 0, false)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, 300.0, Double.NaN, Double.POSITIVE_INFINITY})
    void 무결성은_실제_offset뿐_아니라_sampler가_기록한_반경도_검사한다(double radius) {
        // given
        E001Sample sample = E001Sample.of(1L, "0:0", 0L, 0.0, 0.0, E001Offset.of(10.0, 0.0, radius));

        // when & then
        assertThat(E001Integrity.passes(Map.of(E001Integrity.Shard.of(1L, "0:0"), 1L),
                List.of(sample), 0, false)).isFalse();
    }

    private static E001Sample offset(long index, double x, double y) {
        return E001Sample.of(1L, "0:0", index, 0.0, 0.0, E001Offset.of(x, y, StrictMath.hypot(x, y)));
    }

    private static E001Sample at(long index, double x, double y) {
        return E001Sample.of(1L, "0:0", index, x, y, E001Offset.of(0.0, 0.0, 0.0));
    }

    private static E001MetricSet bruteForce(List<E001Sample> pool) {
        List<E001Sample> targets = pool.stream().filter(p -> p.x() >= -300.0 && p.x() < 300.0
                && p.y() >= -300.0 && p.y() < 300.0).sorted(E001Sample.ORDER).toList();
        double[] proximity = new double[3];
        double neighbors = 0.0;
        for (E001Sample target : targets) {
            double nearest = Double.POSITIVE_INFINITY;
            for (E001Sample other : pool) {
                if (!target.samePoint(other)) {
                    double distance = StrictMath.hypot(target.x() - other.x(), target.y() - other.y());
                    nearest = StrictMath.min(nearest, distance);
                    if (distance < 10.0) {
                        neighbors++;
                    }
                }
            }
            for (int index = 0; index < 3; index++) {
                if (nearest < (index + 1) * 8.0) {
                    proximity[index]++;
                }
            }
        }
        return E001MetricSet.from(Map.of("proximity8", proximity[0] / targets.size(),
                "proximity16", proximity[1] / targets.size(), "proximity24", proximity[2] / targets.size(),
                "neighbors10", neighbors / targets.size()));
    }
}
