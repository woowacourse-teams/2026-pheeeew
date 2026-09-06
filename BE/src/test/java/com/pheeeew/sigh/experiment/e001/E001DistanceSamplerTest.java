package com.pheeeew.sigh.experiment.e001;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class E001DistanceSamplerTest {

    private static final long POINT_SEED = 0x437D819BB0E0C990L;
    private static final long SIGMA_SENSITIVE_POINT_SEED = 0x9264CD1658AD0339L;

    @Test
    void A는_첫_두_난수로_정사각형_오프셋을_재현한다() {
        // given
        E001SplitMix64 random = E001SplitMix64.from(POINT_SEED);

        // when
        E001SamplingResult result = E001DistanceSampler.sampleSquare(random);

        // then
        assertSampleRawBits(
                result,
                1,
                0xC006912C0B6A5400L,
                0xC060D13885929276L,
                0x4060D22AC1D0AFC5L
        );
        assertNextDoubleRawBits(random, 0x3FC64E6A8C92E91CL);
    }

    @Test
    void B는_면적_균등_원_오프셋을_재현한다() {
        // given
        E001SplitMix64 random = E001SplitMix64.from(POINT_SEED);

        // when
        E001SamplingResult result = E001DistanceSampler.sampleDisk(random);

        // then
        assertSampleRawBits(
                result,
                1,
                0x4068E69827AFE93BL,
                0x4050B73D1C68F274L,
                0x406A4417C2C324B1L
        );
        assertNextDoubleRawBits(random, 0x3FC64E6A8C92E91CL);
    }

    @Test
    void C는_sigma별_절단_Rayleigh_역함수_오프셋을_재현한다() {
        // given
        E001SplitMix64 sigma100Random = E001SplitMix64.from(POINT_SEED);
        E001SplitMix64 sigma120Random = E001SplitMix64.from(POINT_SEED);

        // when
        E001SamplingResult sigma100 = E001DistanceSampler.sampleTruncatedGaussian(sigma100Random, 100.0);
        E001SamplingResult sigma120 = E001DistanceSampler.sampleTruncatedGaussian(sigma120Random, 120.0);

        // then
        assertSampleRawBits(
                sigma100,
                1,
                0x405B4F4759095366L,
                0x4042553763115734L,
                0x405CCE9685AC714CL
        );
        assertSampleRawBits(
                sigma120,
                1,
                0x4060004C5AED5D4CL,
                0x40457BA7F0EF9157L,
                0x4060E0E2955F7552L
        );
        assertNextDoubleRawBits(sigma100Random, 0x3FC64E6A8C92E91CL);
        assertNextDoubleRawBits(sigma120Random, 0x3FC64E6A8C92E91CL);
    }

    @Test
    void D는_sigma별_거절_횟수와_tapered_Gaussian_오프셋을_재현한다() {
        // given
        E001SplitMix64 sigma100Random = E001SplitMix64.from(SIGMA_SENSITIVE_POINT_SEED);
        E001SplitMix64 sigma120Random = E001SplitMix64.from(SIGMA_SENSITIVE_POINT_SEED);

        // when
        E001SamplingResult sigma100 = E001DistanceSampler.sampleTaperedGaussian(sigma100Random, 100.0);
        E001SamplingResult sigma120 = E001DistanceSampler.sampleTaperedGaussian(sigma120Random, 120.0);

        // then
        assertSampleRawBits(
                sigma100,
                12,
                0x401802438A15217AL,
                0xC016BD70290BA1A5L,
                0x402088C7A3F060AAL
        );
        assertSampleRawBits(
                sigma120,
                5,
                0x405D46B9C1088B22L,
                0xC056A2C805ED25A4L,
                0x406280D59EDD10B1L
        );
        assertNextDoubleRawBits(sigma100Random, 0x3FE1C1257B6A8C94L);
        assertNextDoubleRawBits(sigma120Random, 0x3FD790A16AF2CBE6L);
    }

    @Test
    void 원점의_각도_계산에서_signed_zero를_보존한다() {
        // given
        SequenceUniformRandom random = SequenceUniformRandom.from(0.0, 0.5);

        // when
        E001SamplingResult result = E001DistanceSampler.sampleDisk(random);

        // then
        assertSampleRawBits(
                result,
                1,
                0x8000000000000000L,
                0x0000000000000000L,
                0x0000000000000000L
        );
        assertThat(random.callCount()).isEqualTo(2);
    }

    @Test
    void 삼각함수_계산_뒤_반경이_300미터가_되면_다음_후보를_사용한다() {
        // given
        SequenceUniformRandom random = SequenceUniformRandom.from(
                0x1.fffffffffffffp-1,
                0x1.b7df7d537d89p-3,
                0.0,
                0.0
        );

        // when
        E001SamplingResult result = E001DistanceSampler.sampleDisk(random);

        // then
        assertSampleRawBits(
                result,
                2,
                0x0000000000000000L,
                0x0000000000000000L,
                0x0000000000000000L
        );
        assertThat(random.callCount()).isEqualTo(4);
    }

    @Test
    void D는_acceptance와_같은_난수를_거절하고_다음_세_난수를_소비한다() {
        // given
        SequenceUniformRandom random = SequenceUniformRandom.from(
                0x1.0p-6,
                0.0,
                0x1.d59364ec8f6f2p-1,
                0.0,
                0.0,
                0.0
        );

        // when
        E001SamplingResult result = E001DistanceSampler.sampleTaperedGaussian(random, 100.0);

        // then
        assertSampleRawBits(
                result,
                2,
                0x0000000000000000L,
                0x0000000000000000L,
                0x0000000000000000L
        );
        assertThat(random.callCount()).isEqualTo(6);
    }

    @Test
    void D는_4096개_후보를_모두_거절하면_대체_좌표_없이_실패한다() {
        // given
        RepeatingRejectRandom random = RepeatingRejectRandom.create();

        // when
        E001SamplingResult result = E001DistanceSampler.sampleTaperedGaussian(random, 100.0);

        // then
        assertThat(result).isInstanceOf(E001SamplingResult.Failure.class);
        assertThat(result.proposalCount()).isEqualTo(E001DistanceSampler.MAX_PROPOSALS);
        assertThat(result.succeeded()).isFalse();
        assertThat(random.callCount()).isEqualTo(E001DistanceSampler.MAX_PROPOSALS * 3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"B", "C100", "C120"})
    void B와_C는_비정상_좌표를_거절한_뒤_다음_후보를_채택한다(String model) {
        // given
        SequenceUniformRandom random = SequenceUniformRandom.from(
                Double.NaN, 0.0,
                0.25, Double.POSITIVE_INFINITY,
                0.0, 0.0
        );

        // when
        E001SamplingResult result = sampleWithoutAcceptance(model, random);

        // then
        assertSampleRawBits(result, 3, 0L, 0L, 0L);
        assertThat(random.callCount()).isEqualTo(6);
    }

    @ParameterizedTest
    @ValueSource(strings = {"B", "C100", "C120"})
    void B와_C는_4096개_후보가_모두_무효이면_대체_좌표_없이_실패한다(String model) {
        // given
        double[] values = new double[4_096 * 2];
        Arrays.fill(values, Double.NaN);
        SequenceUniformRandom random = SequenceUniformRandom.from(values);

        // when
        E001SamplingResult result = sampleWithoutAcceptance(model, random);

        // then
        assertThat(result).isInstanceOf(E001SamplingResult.Failure.class);
        assertThat(result.proposalCount()).isEqualTo(4_096);
        assertThat(result.succeeded()).isFalse();
        assertThat(random.callCount()).isEqualTo(8_192);
    }

    @ParameterizedTest
    @ValueSource(strings = {"B", "C100", "C120"})
    void B와_C는_마지막_4096번째_후보도_유효하면_채택한다(String model) {
        // given
        double[] values = new double[4_096 * 2];
        Arrays.fill(values, Double.NaN);
        values[values.length - 2] = 0.0;
        values[values.length - 1] = 0.0;
        SequenceUniformRandom random = SequenceUniformRandom.from(values);

        // when
        E001SamplingResult result = sampleWithoutAcceptance(model, random);

        // then
        assertSampleRawBits(result, 4_096, 0L, 0L, 0L);
        assertThat(random.callCount()).isEqualTo(8_192);
    }

    @Test
    void taper는_경계에서_0으로_부드럽게_감소한다() {
        // given
        double justInsideBoundary = StrictMath.nextDown(1.0);

        // when
        long[] rawBits = {
                Double.doubleToRawLongBits(E001DistanceSampler.taper(0.0)),
                Double.doubleToRawLongBits(E001DistanceSampler.taper(0.25)),
                Double.doubleToRawLongBits(E001DistanceSampler.taper(0.5)),
                Double.doubleToRawLongBits(E001DistanceSampler.taper(0.75)),
                Double.doubleToRawLongBits(E001DistanceSampler.taper(justInsideBoundary)),
                Double.doubleToRawLongBits(E001DistanceSampler.taper(1.0)),
                Double.doubleToRawLongBits(E001DistanceSampler.taper(1.25))
        };

        // then
        assertThat(rawBits).containsExactly(
                0x3FF0000000000000L,
                0x3FECB00000000000L,
                0x3FE0000000000000L,
                0x3FBA800000000000L,
                0x3633FFFFFFFFFFFFL,
                0x0000000000000000L,
                0x0000000000000000L
        );
        assertTaperIsMonotonicallyDecreasing();
    }

    @Test
    void 원형_sampler는_고정_seed_표본을_항상_300미터_안에_생성한다() {
        // given
        int sampleCount = 256;

        // when & then
        for (int pointIndex = 0; pointIndex < sampleCount; pointIndex++) {
            long pointSeed = E001PointSeed.derive(
                    "conformance-cal-n320-per-model",
                    "CAL",
                    0,
                    0,
                    2_026_090_301L,
                    pointIndex
            );
            assertInsideRadius(E001DistanceSampler.sampleDisk(E001SplitMix64.from(pointSeed)));
            assertInsideRadius(E001DistanceSampler.sampleTruncatedGaussian(E001SplitMix64.from(pointSeed), 100.0));
            assertInsideRadius(E001DistanceSampler.sampleTruncatedGaussian(E001SplitMix64.from(pointSeed), 120.0));
            assertInsideRadius(E001DistanceSampler.sampleTaperedGaussian(E001SplitMix64.from(pointSeed), 100.0));
            assertInsideRadius(E001DistanceSampler.sampleTaperedGaussian(E001SplitMix64.from(pointSeed), 120.0));
        }
    }

    private void assertSampleRawBits(
            E001SamplingResult result,
            int expectedProposalCount,
            long expectedEastingBits,
            long expectedNorthingBits,
            long expectedRadiusBits
    ) {
        assertThat(result).isInstanceOf(E001SamplingResult.Success.class);
        E001SamplingResult.Success success = (E001SamplingResult.Success) result;
        E001Offset offset = success.offset();

        assertThat(success.proposalCount()).isEqualTo(expectedProposalCount);
        assertThat(success.succeeded()).isTrue();
        assertThat(Double.doubleToRawLongBits(offset.eastingMeters())).isEqualTo(expectedEastingBits);
        assertThat(Double.doubleToRawLongBits(offset.northingMeters())).isEqualTo(expectedNorthingBits);
        assertThat(Double.doubleToRawLongBits(offset.radiusMeters())).isEqualTo(expectedRadiusBits);
    }

    private E001SamplingResult sampleWithoutAcceptance(String model, E001UniformRandom random) {
        return switch (model) {
            case "B" -> E001DistanceSampler.sampleDisk(random);
            case "C100" -> E001DistanceSampler.sampleTruncatedGaussian(random, 100.0);
            case "C120" -> E001DistanceSampler.sampleTruncatedGaussian(random, 120.0);
            default -> throw new IllegalArgumentException("지원하지 않는 테스트 모델이에요: " + model);
        };
    }

    private void assertTaperIsMonotonicallyDecreasing() {
        double previous = E001DistanceSampler.taper(0.0);
        for (int step = 1; step <= 1_000; step++) {
            double current = E001DistanceSampler.taper(step / 1_000.0);
            assertThat(current).isBetween(0.0, previous);
            previous = current;
        }
    }

    private void assertNextDoubleRawBits(E001SplitMix64 random, long expectedRawBits) {
        assertThat(Double.doubleToRawLongBits(random.nextDouble())).isEqualTo(expectedRawBits);
    }

    private void assertInsideRadius(E001SamplingResult result) {
        assertThat(result).isInstanceOf(E001SamplingResult.Success.class);
        E001Offset offset = ((E001SamplingResult.Success) result).offset();
        assertThat(offset.eastingMeters()).isFinite();
        assertThat(offset.northingMeters()).isFinite();
        assertThat(offset.radiusMeters()).isGreaterThanOrEqualTo(0.0).isLessThan(300.0);
    }

    private static final class SequenceUniformRandom implements E001UniformRandom {

        private final double[] values;
        private int index;

        private SequenceUniformRandom(double[] values) {
            this.values = values;
        }

        static SequenceUniformRandom from(double... values) {
            return new SequenceUniformRandom(values);
        }

        @Override
        public double nextDouble() {
            return values[index++];
        }

        int callCount() {
            return index;
        }
    }

    private static final class RepeatingRejectRandom implements E001UniformRandom {

        private int callCount;

        private RepeatingRejectRandom() {
        }

        static RepeatingRejectRandom create() {
            return new RepeatingRejectRandom();
        }

        @Override
        public double nextDouble() {
            double value = switch (callCount % 3) {
                case 0 -> 0.5;
                case 1 -> 0.25;
                default -> 0x1.fffffffffffffp-1;
            };
            callCount++;
            return value;
        }

        int callCount() {
            return callCount;
        }
    }
}
