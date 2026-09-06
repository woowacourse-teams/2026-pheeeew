package com.pheeeew.sigh.experiment.e001;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class E001ArtifactsTest {

    private static final E001Artifacts.Metadata METADATA = E001Artifacts.Metadata.of("a".repeat(40), "b".repeat(40),
            Map.of("jdkVersion", "test", "gitDirty", false));
    private static final E001Parameters D = E001Parameters.distance("D", 120);
    private static final E001Parameters E = E001Parameters.of(120, E001Noise.GRADIENT, E001FieldProfile.P1, 0.7, "sir16");

    @TempDir
    Path temporary;

    @Test
    void 산출물은_입력_순서와_환경_정보에_관계없이_같은_checksum을_만든다() throws IOException {
        // given
        E001Distribution.Result result = fixture(E001Selection.Status.SELECTED_D);
        List<E001Conformance.Vector> vectors = vectors(result);
        List<E001Conformance.Vector> reversed = new ArrayList<>(vectors);
        Collections.reverse(reversed);
        Path first = Files.createDirectory(temporary.resolve("first"));
        Path second = Files.createDirectory(temporary.resolve("second"));

        // when
        E001Artifacts.write(first, result, METADATA, vectors);
        E001Artifacts.write(second, result, E001Artifacts.Metadata.of("a".repeat(40), "b".repeat(40), Map.of("jdkVersion", "different")), reversed);

        // then
        assertThat(first.resolve("checksums.sha256")).hasSameTextualContentAs(second.resolve("checksums.sha256"));
        E001Checksums.verify(first);
        assertThat(Files.readString(first.resolve("manifest.json")))
                .contains("\"generatedCount\":5", "\"conformance.csv\":320", "\"distributionOutcome\":\"selected-d\"")
                .doesNotContain(temporary.toString(), "jdkVersion");
        assertThat(Files.readString(first.resolve("checksums.sha256"))).doesNotContain("environment.json", "verification-run1");
        byte[] gzip = Files.readAllBytes(first.resolve("coordinates.csv.gz"));
        assertThat(java.util.Arrays.copyOfRange(gzip, 4, 8)).containsOnly((byte) 0);
        try (var input = new GZIPInputStream(Files.newInputStream(first.resolve("coordinates.csv.gz")))) {
            List<String> rows = new String(input.readAllBytes(), UTF_8).lines().toList();
            assertThat(rows).hasSize(6);
            assertThat(rows.getFirst().split(",")).hasSize(15);
            assertThat(rows.get(1)).contains(",5,5,2026090301,single,0,").doesNotContain("-0.0");
        }
    }

    @Test
    void undefined_지표와_실패가_없는_헤더를_보존한다() throws IOException {
        // given
        E001Distribution.Result result = fixture(E001Selection.Status.INCONCLUSIVE);
        Path root = Files.createDirectory(temporary.resolve("undefined"));

        // when
        E001Artifacts.write(root, result, METADATA, List.of());

        // then
        assertThat(Files.readString(root.resolve("metrics.csv"))).contains(",a4,value,,ratio,undefined\n");
        assertThat(Files.readAllLines(root.resolve("conformance.csv"))).hasSize(1);
        assertThat(Files.readAllLines(root.resolve("sampler-failures.csv"))).hasSize(1);
        assertThat(root.resolve("coordinator-only")).doesNotExist();
    }

    @Test
    void conformance는_모델별_320개와_signed_zero_raw_bit를_보존한다() {
        // given
        E001Distribution.Result result = fixture(E001Selection.Status.E_REVIEW_READY);
        AtomicInteger calls = new AtomicInteger();

        // when
        List<E001Conformance.Vector> vectors = E001Conformance.generate(result, ignored -> (random, x, y) -> {
            calls.incrementAndGet();
            assertThat(x).isEqualTo(953_850.0);
            assertThat(y).isEqualTo(1_951_950.0);
            return E001SamplingResult.Success.of(E001Offset.of(-0.0, 0.0, 0.0), 1);
        });

        // then
        assertThat(vectors).hasSize(640);
        assertThat(calls.get()).isEqualTo(640);
        assertThat(vectors.getFirst().cells().get(5)).isEqualTo("8000000000000000");
        assertThat(vectors.getFirst().pointIndex()).isZero();
        assertThat(vectors.getLast().pointIndex()).isEqualTo(63L);
        assertThat(vectors.getFirst().pointSeed()).isEqualTo(E001PointSeed.derive(
                "conformance-cal-n320-per-model", "CAL", 0, 0, 2_026_090_301L, 0L));
        assertThatThrownBy(() -> E001Conformance.generate(result, ignored -> (random, x, y) -> E001SamplingResult.Failure.from(128)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 잘못된_conformance와_환경_필드와_기존_파일은_거부한다() throws IOException {
        // given
        E001Distribution.Result result = fixture(E001Selection.Status.SELECTED_D);
        Path root = Files.createDirectory(temporary.resolve("invalid"));

        // when & then
        assertThatThrownBy(() -> E001Artifacts.write(root, result, METADATA, List.of())).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> E001Artifacts.write(root, result, METADATA, vectors(result))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> E001Artifacts.Metadata.of("a".repeat(40), "b".repeat(40), Map.of("username", "private")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 별_sprite와_픽셀_혼합과_경계_crop은_고정되어_있다() throws IOException {
        // given
        E001Sample center = point(0, 0.0, 0.0);

        // when
        BufferedImage image = E001Panel.render(List.of(center), 0.0, 0.0);
        BufferedImage cropped = E001Panel.render(List.of(point(0, -600.0, 600.0)), 0.0, 0.0);

        // then
        assertThat(E001ArtifactFormat.sha256(E001Panel.sprite()))
                .isEqualTo("a465c52b7bb91edc886c4305f0abb93d8e703fb57eb1c1aeadd3b3ea2ec91960");
        assertThat(image.getWidth()).isEqualTo(1_200);
        assertThat(image.getHeight()).isEqualTo(1_200);
        assertThat(image.getRGB(600, 600) & 0xffffff).isEqualTo(0xb89a52);
        assertThat(image.getRGB(596, 596) & 0xffffff).isEqualTo(E001Panel.BACKGROUND);
        assertThat(cropped.getRGB(0, 0) & 0xffffff).isEqualTo(0xb89a52);
    }

    @Test
    void PNG는_메타데이터_timestamp가_없고_입력_순서에_의존하지_않는다() throws IOException {
        // given
        Path first = temporary.resolve("first.png");
        Path second = temporary.resolve("second.png");
        List<E001Sample> points = List.of(point(0, 0.0, 0.0), point(1, 1.0, 1.0));

        // when
        E001Panel.write(first, points, 0.0, 0.0);
        E001Panel.write(second, points.reversed(), 0.0, 0.0);

        // then
        assertThat(Files.mismatch(first, second)).isEqualTo(-1L);
        byte[] png = Files.readAllBytes(first);
        ByteBuffer chunks = ByteBuffer.wrap(png);
        chunks.position(8);
        List<String> types = new ArrayList<>();
        while (chunks.remaining() > 0) {
            int length = chunks.getInt();
            byte[] type = new byte[4];
            chunks.get(type);
            types.add(new String(type, UTF_8));
            chunks.position(chunks.position() + length + 4);
        }
        assertThat(types).containsOnly("IHDR", "IDAT", "IEND");
    }

    @Test
    void E_review_ready는_확인_원본_여덟_장만_checksum에_포함한다() throws IOException {
        // given
        E001Distribution.Result result = fixture(E001Selection.Status.E_REVIEW_READY);
        Path root = Files.createDirectory(temporary.resolve("panels"));

        // when
        E001Artifacts.write(root, result, METADATA, vectors(result));

        // then
        try (var files = Files.list(root.resolve("coordinator-only/model-panels"))) {
            assertThat(files.toList()).hasSize(8);
        }
        assertThat(Files.readAllLines(root.resolve("checksums.sha256"))).hasSize(13);
        E001Checksums.verify(root);
        Files.writeString(root.resolve("environment.json"), "changed");
        E001Checksums.verify(root);
        Files.writeString(root.resolve("metrics.csv"), "tampered");
        assertThatThrownBy(() -> E001Checksums.verify(root)).isInstanceOf(IOException.class);
    }

    @Test
    void JSON은_중첩_key를_정렬하고_CSV는_인용부호와_줄바꿈을_escape한다() {
        // when & then
        assertThat(E001ArtifactFormat.json(Map.of("z", Map.of("b", 2, "a", 1), "a", 0)))
                .isEqualTo("{\"a\":0,\"z\":{\"a\":1,\"b\":2}}\n");
        assertThat(E001ArtifactFormat.csv(List.of("a,b", "c\"d", "e\nf")))
                .isEqualTo("\"a,b\",\"c\"\"d\",\"e\nf\"\n");
    }

    private static List<E001Conformance.Vector> vectors(E001Distribution.Result result) {
        // 저장 형식 테스트용 고정 stub이며 실제 E001 conformance 표본을 생성하지 않아요.
        return E001Conformance.generate(result, ignored -> (random, x, y) ->
                E001SamplingResult.Success.of(E001Offset.of(-0.0, 0.0, 0.0), 1));
    }

    private static E001Distribution.Result fixture(E001Selection.Status status) {
        List<E001Trial> trials = new ArrayList<>();
        List<E001Parameters> parameters = status == E001Selection.Status.E_REVIEW_READY ? List.of(D, E) : List.of(D);
        List<E001Scenario> scenarios = status == E001Selection.Status.E_REVIEW_READY ? List.of(
                E001Scenario.CONFIRMATION_SINGLE_500, E001Scenario.CONFIRMATION_SINGLE_5000,
                E001Scenario.CONFIRMATION_GRID_500, E001Scenario.CONFIRMATION_GRID_5000) : List.of(E001Scenario.TUNING_SINGLE);
        for (E001Parameters parameter : parameters) {
            for (E001Scenario scenario : scenarios) {
                E001Scenario.Plan plan = E001Scenario.Plan.of(scenario, List.of(E001Scenario.Center.of("single", 0, 0, 1)));
                List<E001Sample> samples = E001Evaluation.SAMPLE_SEEDS.stream().sorted().map(seed ->
                        E001Sample.of(seed, "single", 0, scenario.originX(), scenario.originY(), E001Offset.of(-0.0, 0.0, 0.0))).toList();
                Map<Long, E001MetricSet> bySeed = new java.util.HashMap<>();
                E001Evaluation.SAMPLE_SEEDS.forEach(seed -> bySeed.put(seed, E001MetricSet.from(Map.of("a4", Double.NaN))));
                trials.add(E001Trial.of(E001Batch.of(parameter, plan, samples, List.of()),
                        E001Evaluation.of(true, bySeed, E001MetricSet.from(Map.of("neighbors10", 4.0)))));
            }
        }
        E001Candidate d = E001Candidate.of(D.parameterSetId(), 120, "", 0.0, Map.of());
        E001Candidate e = status == E001Selection.Status.E_REVIEW_READY
                ? E001Candidate.of(E.parameterSetId(), 120, "sir16", 0.2, Map.of()) : null;
        String reason = status == E001Selection.Status.E_REVIEW_READY ? "e-review-pending"
                : status == E001Selection.Status.SELECTED_D ? "no-e" : "no-d";
        Map<E001Parameters, Double> intensities = new java.util.HashMap<>();
        parameters.forEach(parameter -> intensities.put(parameter, parameter.modelId().equals("E") ? 0.2 : 0.0));
        return E001Distribution.Result.of(E001Selection.Decision.of(status, reason, status == E001Selection.Status.INCONCLUSIVE ? null : d, e),
                trials, intensities);
    }

    private static E001Sample point(long index, double x, double y) {
        return E001Sample.of(1L, "single", index, x, y, E001Offset.of(0.0, 0.0, 0.0));
    }
}
