package com.pheeeew.sigh.experiment.e001;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class E001FieldSamplerTest {

    private static final double CENTER_EASTING = 953_850.0;
    private static final double CENTER_NORTHING = 1_951_950.0;

    @ParameterizedTest
    @CsvSource({
            "GRADIENT, P1, 16, 403d339094fcd226, 406d1057a4cc63e4, 406d4acac72b904c, 16, 145, 3fd1b0e11a859a4e",
            "GRADIENT, P1, 32, 403d339094fcd226, 406d1057a4cc63e4, 406d4acac72b904c, 32, 382, 3f9d47ac71826820",
            "GRADIENT, P1, 128, c04c6c3231ed8538, c06261b068ea28c8, 4063b50ac38717d6, 1, 25, 3fcbaec06662f3d0",
            "GRADIENT, P2, 16, 406455c8976d2c93, 4051ac6064fcea70, 40662c0fd6209819, 16, 145, 3fd1b0e11a859a4e",
            "GRADIENT, P2, 32, 403d339094fcd226, 406d1057a4cc63e4, 406d4acac72b904c, 32, 382, 3f9d47ac71826820",
            "GRADIENT, P2, 128, c04c6c3231ed8538, c06261b068ea28c8, 4063b50ac38717d6, 1, 25, 3fcbaec06662f3d0",
            "VALUE, P1, 16, 406455c8976d2c93, 4051ac6064fcea70, 40662c0fd6209819, 16, 145, 3fd1b0e11a859a4e",
            "VALUE, P1, 32, 406455c8976d2c93, 4051ac6064fcea70, 40662c0fd6209819, 32, 382, 3f9d47ac71826820",
            "VALUE, P1, 128, c04c6c3231ed8538, c06261b068ea28c8, 4063b50ac38717d6, 1, 25, 3fcbaec06662f3d0",
            "VALUE, P2, 16, 406455c8976d2c93, 4051ac6064fcea70, 40662c0fd6209819, 16, 145, 3fd1b0e11a859a4e",
            "VALUE, P2, 32, 403d339094fcd226, 406d1057a4cc63e4, 406d4acac72b904c, 32, 382, 3f9d47ac71826820",
            "VALUE, P2, 128, c04c6c3231ed8538, c06261b068ea28c8, 4063b50ac38717d6, 1, 25, 3fcbaec06662f3d0"
    })
    void E는_noise와_profile과_sampler별_좌표와_난수_소비를_재현한다(
            E001Noise noise,
            E001FieldProfile profile,
            int algorithm,
            String xBits,
            String yBits,
            String radiusBits,
            int proposals,
            int expectedCalls,
            String nextBits
    ) {
        // given
        E001MultiscaleField field = E001MultiscaleField.of(noise, profile, E001MultiscaleField.FIELD_SEED);
        E001FieldSampler sampler = E001FieldSampler.of(120.0, 0.7, field::valueAt);
        E001SplitMix64 source = E001SplitMix64.from(0x437D819BB0E0C990L);
        AtomicInteger calls = new AtomicInteger();
        E001UniformRandom random = () -> {
            calls.incrementAndGet();
            return source.nextDouble();
        };

        // when
        E001SamplingResult result = sample(sampler, algorithm, random);

        // then
        E001Offset offset = successOffset(result, proposals);
        assertBits(offset.eastingMeters(), xBits);
        assertBits(offset.northingMeters(), yBits);
        assertBits(offset.radiusMeters(), radiusBits);
        assertThat(offset.radiusMeters()).isLessThan(300.0);
        assertThat(calls.get()).isEqualTo(expectedCalls);
        assertBits(source.nextDouble(), nextBits);
    }

    @ParameterizedTest
    @CsvSource({"16, 0.0, 0", "16, 0.0625, 1", "16, 0.9999999999999999, 15",
            "32, 0.0, 0", "32, 0.03125, 1", "32, 0.9999999999999999, 31"})
    void SIR은_모든_D_후보의_절대_좌표를_평가하고_누적_가중치_경계에서_다음_후보를_고른다(
            int candidateCount, double selectionUniform, int selectedIndex
    ) {
        // given
        double[] values = new double[candidateCount * 3 + 1];
        for (int index = 0; index < candidateCount; index++) {
            double fraction = index / 32.0;
            values[index * 3] = fraction * fraction;
        }
        values[values.length - 1] = selectionUniform;
        SequenceRandom random = SequenceRandom.from(values);
        AtomicInteger evaluations = new AtomicInteger();
        E001FieldSampler sampler = E001FieldSampler.of(120.0, 0.7, (x, y) -> {
            assertThat(random.calls()).isEqualTo(candidateCount * 3);
            int index = evaluations.getAndIncrement();
            assertThat(x).isEqualTo(CENTER_EASTING + index * 9.375);
            assertThat(y).isEqualTo(CENTER_NORTHING);
            return 0.0;
        });

        // when
        E001SamplingResult result = sample(sampler, candidateCount, random);

        // then
        E001Offset offset = successOffset(result, candidateCount);
        assertThat(offset.eastingMeters()).isEqualTo(selectedIndex * 9.375);
        assertThat(offset.northingMeters()).isZero();
        assertThat(evaluations.get()).isEqualTo(candidateCount);
        assertThat(random.calls()).isEqualTo(candidateCount * 3 + 1);
    }

    @Test
    void SIR은_큰_log_weight에서도_최댓값을_빼서_유한한_가중치로_선택한다() {
        // given
        double[] values = new double[16 * 3 + 1];
        for (int index = 0; index < 16; index++) {
            double fraction = index / 32.0;
            values[index * 3] = fraction * fraction;
        }
        SequenceRandom random = SequenceRandom.from(values);
        E001FieldSampler sampler = E001FieldSampler.of(120.0, 1_000.0,
                (x, y) -> x == CENTER_EASTING ? -1.0 : 1.0);

        // when
        E001SamplingResult result = sample(sampler, 16, random);

        // then
        assertThat(successOffset(result, 16).eastingMeters()).isEqualTo(9.375);
        assertThat(random.calls()).isEqualTo(49);
    }

    @ParameterizedTest
    @ValueSource(ints = {16, 32, 128})
    void E는_중간_D_후보_실패를_전달하고_추가_난수나_field를_소비하지_않는다(int algorithm) {
        // given
        int prefix = algorithm == 128 ? 4 : 3;
        double[] values = new double[prefix + 4_096 * 3];
        if (algorithm == 128) {
            values[3] = 0.99;
        }
        for (int index = prefix; index < values.length; index += 3) {
            values[index] = 0.5;
            values[index + 1] = 0.25;
            values[index + 2] = 0.99;
        }
        SequenceRandom random = SequenceRandom.from(values);
        AtomicInteger evaluations = new AtomicInteger();
        E001FieldSampler sampler = E001FieldSampler.of(120.0, 1.0, (x, y) -> {
            evaluations.incrementAndGet();
            return -1.0;
        });

        // when
        E001SamplingResult result = sample(sampler, algorithm, random);

        // then
        assertThat(result).isInstanceOf(E001SamplingResult.Failure.class);
        assertThat(result.proposalCount()).isEqualTo(4_096);
        assertThat(result.succeeded()).isFalse();
        assertThat(random.calls()).isEqualTo(values.length);
        assertThat(evaluations.get()).isEqualTo(algorithm == 128 ? 1 : 0);
    }

    @Test
    void rejection은_acceptance와_같은_난수를_거절한다() {
        // given
        SequenceRandom random = SequenceRandom.from(
                0.0, 0.0, 0.0, 0x1.78b56362cef38p-2,
                0x1.0p-10, 0.0, 0.0, 0.0
        );
        AtomicInteger evaluations = new AtomicInteger();
        E001FieldSampler sampler = E001FieldSampler.of(120.0, 1.0, (x, y) -> {
            int index = evaluations.getAndIncrement();
            assertThat(x).isEqualTo(CENTER_EASTING + index * 9.375);
            assertThat(y).isEqualTo(CENTER_NORTHING);
            return 0.0;
        });

        // when
        E001SamplingResult result = sample(sampler, 128, random);

        // then
        assertThat(successOffset(result, 2).eastingMeters()).isEqualTo(9.375);
        assertThat(evaluations.get()).isEqualTo(2);
        assertThat(random.calls()).isEqualTo(8);
    }

    @Test
    void rejection은_128개_D_후보를_모두_거절하면_대체_좌표_없이_실패한다() {
        // given
        double[] values = rejectedFieldCandidates();
        SequenceRandom random = SequenceRandom.from(values);
        E001FieldSampler sampler = E001FieldSampler.of(120.0, 1.0, (x, y) -> -1.0);

        // when
        E001SamplingResult result = sample(sampler, 128, random);

        // then
        assertThat(result).isInstanceOf(E001SamplingResult.Failure.class);
        assertThat(result.proposalCount()).isEqualTo(128);
        assertThat(result.succeeded()).isFalse();
        assertThat(random.calls()).isEqualTo(512);
    }

    @Test
    void rejection은_마지막_128번째_후보도_조건을_만족하면_채택한다() {
        // given
        double[] values = rejectedFieldCandidates();
        values[values.length - 1] = 0.0;
        SequenceRandom random = SequenceRandom.from(values);
        E001FieldSampler sampler = E001FieldSampler.of(120.0, 1.0, (x, y) -> -1.0);

        // when
        E001SamplingResult result = sample(sampler, 128, random);

        // then
        assertThat(successOffset(result, 128).radiusMeters()).isZero();
        assertThat(random.calls()).isEqualTo(512);
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, -1.01, 1.01})
    void 유효하지_않은_field_값으로_좌표를_조용히_선택하지_않는다(double fieldValue) {
        // given
        E001FieldSampler sampler = E001FieldSampler.of(120.0, 1.0, (x, y) -> fieldValue);

        // when & then
        assertThatThrownBy(() -> sample(sampler, 16, () -> 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sample(sampler, 128, () -> 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private E001SamplingResult sample(E001FieldSampler sampler, int algorithm, E001UniformRandom random) {
        return switch (algorithm) {
            case 16 -> sampler.sampleSir16(random, CENTER_EASTING, CENTER_NORTHING);
            case 32 -> sampler.sampleSir32(random, CENTER_EASTING, CENTER_NORTHING);
            case 128 -> sampler.sampleRejection128(random, CENTER_EASTING, CENTER_NORTHING);
            default -> throw new IllegalArgumentException("지원하지 않는 테스트 sampler예요: " + algorithm);
        };
    }

    private E001Offset successOffset(E001SamplingResult result, int proposals) {
        assertThat(result).isInstanceOf(E001SamplingResult.Success.class);
        assertThat(result.proposalCount()).isEqualTo(proposals);
        return ((E001SamplingResult.Success) result).offset();
    }

    private void assertBits(double value, String expectedHex) {
        assertThat(Double.doubleToRawLongBits(value)).isEqualTo(Long.parseUnsignedLong(expectedHex, 16));
    }

    private double[] rejectedFieldCandidates() {
        double[] values = new double[128 * 4];
        for (int index = 3; index < values.length; index += 4) {
            values[index] = 0.99;
        }
        return values;
    }

    private static final class SequenceRandom implements E001UniformRandom {

        private final double[] values;
        private int index;

        private SequenceRandom(double[] values) {
            this.values = values;
        }

        static SequenceRandom from(double... values) {
            return new SequenceRandom(values);
        }

        @Override
        public double nextDouble() {
            return values[index++];
        }

        int calls() {
            return index;
        }
    }
}
