package com.pheeeew.sigh.experiment.e001;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;
import java.util.function.LongSupplier;

final class E001Performance {

    private static volatile long sink;
    private static final String HEADER = "model_id,parameter_set_id,phase,batch_index,points,elapsed_ns,ns_per_point\n";

    private E001Performance() {
    }

    static List<Batch> measureTo(Path path, E001Parameters d, E001Parameters e) throws IOException {
        return measureTo(path, d, e, 100_000, E001Parameters::pointSampler, System::nanoTime);
    }

    static List<Batch> measureTo(Path path, E001Parameters d, E001Parameters e, int points,
            Function<E001Parameters, E001Parameters.PointSampler> samplers, LongSupplier clock) throws IOException {
        try (var writer = Files.newBufferedWriter(path, UTF_8, StandardOpenOption.CREATE_NEW)) {
            writer.write(HEADER);
            writer.flush();
            return measure(d, e, points, samplers, clock, row -> {
                // 시간 측정이 끝난 batch를 바로 보존해 중간 실패 때도 원본 측정값을 잃지 않아요.
                writer.write(csv(row));
                writer.flush();
            });
        }
    }

    static List<Batch> measure(E001Parameters d, E001Parameters e, int points,
            Function<E001Parameters, E001Parameters.PointSampler> samplers, LongSupplier clock) throws IOException {
        return measure(d, e, points, samplers, clock, row -> { });
    }

    static long pointSeed(String phase, int batch, int point) {
        String input = "E001-v1|performance|CAL|0|0|" + phase + "|" + batch + "|" + point;
        return ByteBuffer.wrap(HexFormat.of().parseHex(E001ArtifactFormat.sha256(input.getBytes(UTF_8)))).getLong();
    }

    static Gate evaluate(List<Batch> batches) {
        List<Double> times = batches.stream().filter(row -> row.modelId().equals("E") && row.phase().equals("measurement"))
                .map(Batch::nsPerPoint).sorted().toList();
        if (times.size() != 10 || times.stream().anyMatch(time -> !Double.isFinite(time) || time < 0)) {
            throw new IllegalArgumentException("E 측정 batch 열 개가 필요해요.");
        }
        double median = (times.get(4) + times.get(5)) / 2.0;
        return Gate.of(median, times.getLast(), median <= 100_000.0 && times.getLast() <= 250_000.0);
    }

    static void write(Path path, List<Batch> batches) throws IOException {
        StringBuilder csv = new StringBuilder(HEADER);
        for (Batch row : batches) {
            csv.append(csv(row));
        }
        E001ArtifactFormat.write(path, csv.toString());
    }

    private static List<Batch> measure(E001Parameters d, E001Parameters e, int points,
            Function<E001Parameters, E001Parameters.PointSampler> samplers, LongSupplier clock, BatchWriter writer) throws IOException {
        if (!d.modelId().equals("D") || !e.modelId().equals("E") || d.sigma() != e.sigma() || points <= 0) {
            throw new IllegalArgumentException("같은 sigma의 D와 E를 양의 개수로 측정해야 해요.");
        }
        var dSampler = samplers.apply(d);
        var eSampler = samplers.apply(e);
        List<Batch> rows = new ArrayList<>();
        for (String phase : List.of("warmup", "measurement")) {
            for (int index = 0; index < (phase.equals("warmup") ? 5 : 10); index++) {
                // SHA-256 입력 준비는 pure sampler 측정 구간에서 제외해요. RNG 초기화는 두 모델에 동일하게 포함해요.
                long[] seeds = new long[points];
                for (int point = 0; point < points; point++) {
                    seeds[point] = pointSeed(phase, index, point);
                }
                for (E001Parameters parameter : index % 2 == 0 ? List.of(d, e) : List.of(e, d)) {
                    var sampler = parameter.equals(d) ? dSampler : eSampler;
                    long acc = 0L;
                    long start = clock.getAsLong();
                    for (long seed : seeds) {
                        var result = sampler.sample(E001SplitMix64.from(seed), 953_850.0, 1_951_950.0);
                        if (!(result instanceof E001SamplingResult.Success success)) {
                            throw new IOException("성능 측정에서 sampler가 실패했어요.");
                        }
                        acc = Long.rotateLeft(acc, 1) ^ Double.doubleToRawLongBits(success.offset().eastingMeters());
                        acc = Long.rotateLeft(acc, 1) ^ Double.doubleToRawLongBits(success.offset().northingMeters());
                    }
                    long elapsed = clock.getAsLong() - start;
                    sink = acc;
                    if (elapsed < 0) {
                        throw new IOException("음수 측정 시간은 유효하지 않아요.");
                    }
                    Batch row = Batch.of(parameter.modelId(), parameter.parameterSetId(), phase, index, points, elapsed);
                    rows.add(row);
                    writer.write(row);
                }
            }
        }
        return List.copyOf(rows);
    }

    private static String csv(Batch row) {
        return E001ArtifactFormat.csv(List.of(row.modelId(), row.parameterSetId(), row.phase(),
                Integer.toString(row.index()), Integer.toString(row.points()), Long.toString(row.elapsed()),
                E001ArtifactFormat.decimal(row.nsPerPoint())));
    }

    @FunctionalInterface
    private interface BatchWriter {

        void write(Batch row) throws IOException;
    }

    record Batch(String modelId, String parameterSetId, String phase, int index, int points, long elapsed) {

        static Batch of(String modelId, String parameterSetId, String phase, int index, int points, long elapsed) {
            return new Batch(modelId, parameterSetId, phase, index, points, elapsed);
        }

        double nsPerPoint() {
            return elapsed / (double) points;
        }
    }

    record Gate(double medianNs, double maxNs, boolean passed) {

        static Gate of(double medianNs, double maxNs, boolean passed) {
            return new Gate(medianNs, maxNs, passed);
        }
    }
}
