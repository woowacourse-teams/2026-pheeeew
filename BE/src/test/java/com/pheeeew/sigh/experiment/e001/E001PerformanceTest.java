package com.pheeeew.sigh.experiment.e001;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class E001PerformanceTest {

    @TempDir
    Path directory;

    @Test
    void 고정_seed와_교대_순서로_warmup_다섯번과_측정_열번을_보존한다() throws IOException {
        // given
        AtomicLong clock = new AtomicLong();
        List<String> calls = new ArrayList<>();

        // when
        var rows = E001Performance.measure(E001RunnerFixture.D, E001RunnerFixture.E, 2, parameter -> (random, x, y) -> {
            calls.add(parameter.modelId() + ":" + random.nextDouble());
            assertThat(x).isEqualTo(971_850.0);
            assertThat(y).isEqualTo(1_969_950.0);
            return E001SamplingResult.Success.of(E001Offset.of(1, 2, StrictMath.sqrt(5)), 1);
        }, () -> clock.getAndAdd(80));
        E001Performance.write(directory.resolve("performance.csv"), rows);

        // then
        assertThat(rows).hasSize(30);
        assertThat(rows.subList(0, 6)).extracting(E001Performance.Batch::modelId).containsExactly("D", "E", "E", "D", "D", "E");
        assertThat(rows.get(10).phase()).isEqualTo("measurement");
        assertThat(rows.get(10).index()).isZero();
        assertThat(rows.get(10).modelId()).isEqualTo("D");
        assertThat(rows).allSatisfy(row -> assertThat(row.nsPerPoint()).isEqualTo(40.0));
        assertThat(calls).hasSize(60);
        assertThat(calls.get(0).substring(2)).isEqualTo(calls.get(2).substring(2));
        assertThat(Long.toUnsignedString(E001Performance.pointSeed("measurement", 0, 0), 16)).isEqualTo("2e387fe8e09c5d1c");
        assertThat(Files.readAllLines(directory.resolve("performance.csv"))).hasSize(31)
                .first().isEqualTo("model_id,parameter_set_id,phase,batch_index,points,elapsed_ns,ns_per_point");
    }

    @Test
    void E가_없으면_D만_warmup_다섯번과_측정_열번을_보존한다() throws IOException {
        // given
        AtomicLong clock = new AtomicLong();
        AtomicLong calls = new AtomicLong();

        // when
        var rows = E001Performance.measure(E001RunnerFixture.D, null, 2, parameter -> (random, x, y) -> {
            calls.incrementAndGet();
            return E001SamplingResult.Success.of(E001Offset.of(1, 2, StrictMath.sqrt(5)), 1);
        }, () -> clock.getAndAdd(20));

        // then
        assertThat(rows).hasSize(15).allSatisfy(row -> {
            assertThat(row.modelId()).isEqualTo("D");
            assertThat(row.nsPerPoint()).isEqualTo(10.0);
        });
        assertThat(rows.stream().filter(row -> row.phase().equals("measurement")))
                .extracting(E001Performance.Batch::index)
                .containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(calls).hasValue(30);
    }

    @ParameterizedTest
    @CsvSource({"100000,250000,true", "100001,250000,false", "100000,250001,false"})
    void 성능_gate는_평균이_아닌_열개_batch의_median과_최댓값을_본다(long median, long maximum, boolean passed) {
        // given
        List<E001Performance.Batch> rows = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            rows.add(E001Performance.Batch.of("D", "selected", "measurement", index, 1, index == 9 ? maximum : median));
        }

        // when
        var gate = E001Performance.evaluate(rows, "D");

        // then
        assertThat(gate.medianNs()).isEqualTo(median);
        assertThat(gate.maxNs()).isEqualTo(maximum);
        assertThat(gate.passed()).isEqualTo(passed);
    }

    @Test
    void 성능_gate는_중복된_측정_batch_index를_허용하지_않는다() {
        // given
        List<E001Performance.Batch> rows = measurementRows("D", "selected", 1, 1);
        rows.set(9, E001Performance.Batch.of("D", "selected", "measurement", 0, 1, 1));

        // when & then
        assertThatThrownBy(() -> E001Performance.evaluate(rows, "D"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 성능_gate는_서로_다른_parameter_set의_측정을_합치지_않는다() {
        // given
        List<E001Performance.Batch> rows = measurementRows("E", "selected", 1, 1);
        rows.set(9, E001Performance.Batch.of("E", "another", "measurement", 9, 1, 1));

        // when & then
        assertThatThrownBy(() -> E001Performance.evaluate(rows, "E"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"0,1", "1,-1", "2,1"})
    void 성능_gate는_동일한_양수_points와_음수가_아닌_시간만_허용한다(int points, long elapsed) {
        // given
        List<E001Performance.Batch> rows = measurementRows("D", "selected", 1, 1);
        rows.set(9, E001Performance.Batch.of("D", "selected", "measurement", 9, points, elapsed));

        // when & then
        assertThatThrownBy(() -> E001Performance.evaluate(rows, "D"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sampler_실패를_측정_성공으로_바꾸지_않는다() {
        // when & then
        assertThatThrownBy(() -> E001Performance.measure(E001RunnerFixture.D, E001RunnerFixture.E, 1,
                parameter -> (random, x, y) -> E001SamplingResult.Failure.from(4096), () -> 0L))
                .isInstanceOf(IOException.class).hasMessageContaining("sampler");
    }

    @Test
    void 중간_sampler_실패가_나도_이미_측정한_batch의_원본_CSV를_남긴다() throws IOException {
        // given
        AtomicLong calls = new AtomicLong();
        AtomicLong clock = new AtomicLong();
        Path csv = directory.resolve("partial.csv");

        // when & then
        assertThatThrownBy(() -> E001Performance.measureTo(csv, E001RunnerFixture.D, E001RunnerFixture.E, 1,
                parameter -> (random, x, y) -> calls.incrementAndGet() > 2 ? E001SamplingResult.Failure.from(4096)
                        : E001SamplingResult.Success.of(E001Offset.of(0, 0, 0), 1), () -> clock.getAndAdd(10)))
                .isInstanceOf(IOException.class);
        assertThat(Files.readAllLines(csv)).hasSize(3);
        assertThat(Files.readString(csv)).contains("D," + E001RunnerFixture.D.parameterSetId() + ",warmup,0,1,10,10.0\n",
                "E," + E001RunnerFixture.E.parameterSetId() + ",warmup,0,1,10,10.0\n");
    }

    private List<E001Performance.Batch> measurementRows(String modelId, String parameterSetId, int points, long elapsed) {
        List<E001Performance.Batch> rows = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            rows.add(E001Performance.Batch.of(modelId, parameterSetId, "measurement", index, points, elapsed));
        }
        return rows;
    }
}
