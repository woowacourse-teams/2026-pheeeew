package com.pheeeew.sigh.experiment.e001;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;

final class E001Conformance {

    private E001Conformance() {
    }

    static List<Vector> generate(E001Distribution.Result result, Function<E001Parameters, E001Parameters.PointSampler> samplers) {
        if (result.decision().status() == E001Selection.Status.INCONCLUSIVE) {
            return List.of();
        }
        List<E001Parameters> parameters = selected(result);
        List<Vector> vectors = new ArrayList<>();
        for (E001Parameters parameter : parameters) {
            E001Parameters.PointSampler sampler = samplers.apply(parameter);
            for (long seed : E001Evaluation.SAMPLE_SEEDS.stream().sorted().toList()) {
                for (long index = 0; index < 64; index++) {
                    long pointSeed = E001PointSeed.derive("conformance-cal-n320-per-model", "CAL", 0, 0, seed, index);
                    E001SamplingResult sampled = sampler.sample(E001SplitMix64.from(pointSeed), 953_850.0, 1_951_950.0);
                    if (!(sampled instanceof E001SamplingResult.Success success)) {
                        throw new IllegalStateException("conformance 표본 생성에 실패했어요.");
                    }
                    E001Sample point = E001Sample.of(seed, "single", index, 953_850.0, 1_951_950.0, success.offset());
                    if (!E001ShapeMetrics.valid(point, false)) {
                        throw new IllegalStateException("conformance 표본이 반경 제약을 위반했어요.");
                    }
                    vectors.add(Vector.of(parameter.modelId(), parameter.parameterSetId(), seed, index, pointSeed,
                            Double.doubleToRawLongBits(success.offset().eastingMeters()),
                            Double.doubleToRawLongBits(success.offset().northingMeters())));
                }
            }
        }
        return List.copyOf(vectors);
    }

    static List<E001Parameters> selected(E001Distribution.Result result) {
        List<String> ids = switch (result.decision().status()) {
            case INCONCLUSIVE -> List.of();
            case D_REVIEW_READY -> List.of(result.decision().d().parameterSetId());
            case E_REVIEW_READY -> List.of(result.decision().d().parameterSetId(), result.decision().e().parameterSetId());
            default -> throw new IllegalArgumentException("완료된 분포 판정이 필요해요.");
        };
        List<E001Parameters> parameters = result.trials().stream().map(trial -> trial.batch().parameters()).distinct()
                .filter(parameter -> ids.contains(parameter.parameterSetId()))
                .sorted(Comparator.comparing(E001Parameters::modelId)).toList();
        if (parameters.size() != ids.size()) {
            throw new IllegalArgumentException("선택된 모델의 파라미터가 결과에 없어요.");
        }
        return parameters;
    }

    record Vector(String modelId, String parameterSetId, long sampleSeed, long pointIndex,
                  long pointSeed, long eastingBits, long northingBits) {

        static Vector of(String modelId, String parameterSetId, long sampleSeed, long pointIndex,
                         long pointSeed, long eastingBits, long northingBits) {
            return new Vector(modelId, parameterSetId, sampleSeed, pointIndex, pointSeed, eastingBits, northingBits);
        }

        List<String> cells() {
            return List.of(modelId, parameterSetId, Long.toString(sampleSeed), Long.toString(pointIndex),
                    HexFormat.of().toHexDigits(pointSeed), HexFormat.of().toHexDigits(eastingBits), HexFormat.of().toHexDigits(northingBits));
        }
    }
}
