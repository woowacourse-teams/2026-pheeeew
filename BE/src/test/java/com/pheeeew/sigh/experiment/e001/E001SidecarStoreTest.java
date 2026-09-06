package com.pheeeew.sigh.experiment.e001;

import static com.pheeeew.sigh.experiment.e001.E001RunnerFixture.FIRST;
import static com.pheeeew.sigh.experiment.e001.E001RunnerFixture.SECOND;
import static com.pheeeew.sigh.experiment.e001.E001RunnerFixture.THIRD;
import static com.pheeeew.sigh.experiment.e001.E001RunnerFixture.METADATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class E001SidecarStoreTest {

    @TempDir
    Path parent;

    Path root;

    @BeforeEach
    void 준비한다() throws IOException {
        root = E001RunnerFixture.distribution(parent);
    }

    @Test
    void 완성된_sidecar만_승격하고_분포_checksum은_변경하지_않는다() throws IOException {
        // given
        String checksum = Files.readString(root.resolve("checksums.sha256"));
        Path v1Marker = Files.createDirectories(parent.resolve("e001")).resolve("preserved-v1.txt");
        Files.writeString(v1Marker, "preserve");

        // when
        Path result = performance(FIRST, "");

        // then
        assertThat(result).isEqualTo(root.resolve("performance"));
        var manifest = E001RunContext.read(result.resolve("manifest.json"));
        assertThat(manifest.path("status").asString()).isEqualTo("valid");
        assertThat(manifest.path("protocolVersion").asString()).isEqualTo("E001-v2");
        assertThat(manifest.path("schemaVersion").asInt()).isEqualTo(2);
        assertThat(manifest.path("protocolSha").asString()).isEqualTo(METADATA.protocolSha());
        assertThat(manifest.path("runnerSha").asString()).isEqualTo(METADATA.runnerSha());
        assertThat(manifest.path("machineFileCount").asInt()).isEqualTo(3);
        assertThat(manifest.path("machineRows").path("performance.csv").asInt()).isEqualTo(30);
        assertThat(root.resolve("checksums.sha256")).hasContent(checksum);
        assertThat(v1Marker).hasContent("preserve");
        E001Checksums.verify(root);
        E001RunContext.Source source = E001RunContext.source(root, METADATA);
        assertThat(E001SidecarStore.verifyPerformance(root, source)).isTrue();
    }

    @Test
    void D만_review_ready이면_성능_열다섯행을_검증하고_E를_포함하지_않는다() throws IOException {
        // given
        Path dOnlyParent = Files.createDirectory(parent.resolve("d-only"));
        Path dOnlyRoot = E001RunnerFixture.distribution(dOnlyParent, false);

        // when
        Path result = E001SidecarStore.publish(
                dOnlyParent, "performance", FIRST, "", METADATA, E001RunnerFixture::performance
        );

        // then
        E001RunContext.Source source = E001RunContext.source(dOnlyRoot, METADATA);
        assertThat(E001RunContext.read(result.resolve("manifest.json"))
                .path("machineRows").path("performance.csv").asInt()).isEqualTo(15);
        assertThat(E001SidecarStore.verifyPerformance(dOnlyRoot, source)).isFalse();
    }

    @Test
    void 성능_gate는_manifest_요약보다_raw_CSV의_D와_E_측정을_우선한다() throws IOException {
        // given
        E001RunContext.Source source = E001RunContext.source(root, METADATA);
        publishPerformance(FIRST, 1, 100_001, false);

        // when
        boolean includeE = E001SidecarStore.verifyPerformance(root, source);

        // then
        assertThat(includeE).isFalse();
    }

    @Test
    void raw_CSV의_D가_성능_gate를_실패하면_inconclusive로_중단한다() throws IOException {
        // given
        E001RunContext.Source source = E001RunContext.source(root, METADATA);
        publishPerformance(FIRST, 100_001, 1, false);

        // when & then
        assertThatThrownBy(() -> E001SidecarStore.verifyPerformance(root, source))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("inconclusive");
    }

    @Test
    void manifest와_checksum이_맞아도_raw_CSV의_중복_batch는_거절한다() throws IOException {
        // given
        E001RunContext.Source source = E001RunContext.source(root, METADATA);
        publishPerformance(FIRST, 1, 1, true);

        // when & then
        assertThatThrownBy(() -> E001SidecarStore.verifyPerformance(root, source))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("batch");
    }

    @Test
    void producer가_보호_metadata를_덮어쓰지_못하고_사후_변경도_거절한다() throws IOException {
        // given
        E001RunContext.Source source = E001RunContext.source(root, METADATA);

        // when
        Path result = E001SidecarStore.publish(parent, "performance", FIRST, "", METADATA, (staging, path, selected) -> {
            Map<String, Object> values = new HashMap<>(E001RunnerFixture.performance(staging, path, selected));
            values.put("protocolVersion", "E001-v1");
            values.put("schemaVersion", 1);
            values.put("protocolSha", "0".repeat(40));
            values.put("runnerSha", "0".repeat(40));
            return values;
        });

        // then
        Map<String, Object> manifest = E001RunContext.readMap(result.resolve("manifest.json"));
        assertThat(manifest).containsEntry("protocolVersion", "E001-v2")
                .containsEntry("schemaVersion", 2)
                .containsEntry("protocolSha", METADATA.protocolSha())
                .containsEntry("runnerSha", METADATA.runnerSha());
        manifest.put("protocolSha", "0".repeat(40));
        Files.writeString(result.resolve("manifest.json"), E001ArtifactFormat.json(manifest));
        assertThatThrownBy(() -> E001SidecarStore.verifyPerformance(root, source))
                .isInstanceOf(IOException.class);
    }

    @Test
    void 같은_lock으로_분포_재승격과_sidecar_동시_실행을_거절한다() throws IOException {
        // when
        E001RunLock.execute(parent, () -> {
            assertThatThrownBy(() -> performance(FIRST, "")).isInstanceOf(IOException.class).hasMessageContaining("진행 중");
            assertThatThrownBy(() -> E001ArtifactStore.publish(parent, SECOND, staging -> { }))
                    .isInstanceOf(IOException.class).hasMessageContaining("진행 중");
            return root;
        });

        // then
        assertThat(root.resolve("performance")).doesNotExist();
    }

    @Test
    void checksum_불일치와_허용되지_않은_outcome에서는_실행하지_않는다() throws IOException {
        // given
        Files.writeString(root.resolve("metrics.csv"), "tampered");

        // when & then
        assertThatThrownBy(() -> performance(FIRST, "")).isInstanceOf(IOException.class);
        var manifest = E001RunContext.readMap(root.resolve("manifest.json"));
        manifest.put("distributionOutcome", "selected-d");
        Files.writeString(root.resolve("manifest.json"), E001ArtifactFormat.json(manifest));
        E001RunnerFixture.seal(root);
        assertThatThrownBy(() -> performance(FIRST, "")).isInstanceOf(IOException.class).hasMessageContaining("review-ready");
        assertThat(root.resolve("performance")).doesNotExist();
    }

    @Test
    void 명시적으로_무효화한_전체_성능은_한번만_재시도하고_세번째는_막는다() throws IOException {
        // given
        performance(FIRST, "");

        // when & then
        assertThatThrownBy(() -> performance(SECOND, "")).isInstanceOf(IOException.class).hasMessageContaining("이유");
        performance(SECOND, "measurement_contaminated");
        assertThat(E001RunContext.read(parent.resolve("e001-v2-invalidated/performance-" + FIRST + "/manifest.json"))
                .path("invalidationReason").asString()).isEqualTo("measurement_contaminated");
        assertThatThrownBy(() -> performance(THIRD, "measurement_contaminated")).isInstanceOf(IOException.class).hasMessageContaining("재시도 한 번");
        assertThat(root.resolve("performance")).doesNotExist();
        assertThat(parent.resolve("e001-v2-invalidated/performance-" + SECOND + "/performance.csv")).exists();
        assertThat(parent.resolve("e001-v2-performance-staging-" + THIRD)).doesNotExist();
    }

    @Test
    void 실패한_partial과_남은_manifest_임시파일을_보존하며_재시도_횟수에_포함한다() throws IOException {
        // given
        Path interrupted = Files.createDirectory(parent.resolve("e001-v2-performance-staging-" + FIRST));
        E001ArtifactFormat.write(interrupted.resolve("manifest.json"), E001ArtifactFormat.json(Map.of(
                "kind", "performance", "runId", FIRST, "status", "running",
                "distributionChecksum", E001ArtifactFormat.sha256(root.resolve("checksums.sha256")))));
        E001ArtifactFormat.write(interrupted.resolve("manifest-next-" + THIRD + ".json"), "partial");

        // when
        performance(SECOND, "");

        // then
        Path archived = parent.resolve("e001-v2-invalidated/performance-" + FIRST);
        assertThat(archived.resolve("manifest-next-" + THIRD + ".json")).hasContent("partial");
        assertThat(E001RunContext.read(archived.resolve("manifest.json")).path("invalidationReason").asString()).isEqualTo("interrupted");
        assertThatThrownBy(() -> performance(THIRD, "measurement_contaminated")).isInstanceOf(IOException.class).hasMessageContaining("재시도 한 번");
    }

    @Test
    void 분포_재승격으로_보존된_성능도_같은_checksum의_횟수에_포함한다() throws IOException {
        // given
        performance(FIRST, "");
        Path prior = Files.createDirectories(parent.resolve("e001-v2-invalidated/distribution-" + FIRST));
        Files.move(root.resolve("performance"), prior.resolve("performance"));
        assertThatThrownBy(() -> performance(SECOND, "")).isInstanceOf(IOException.class).hasMessageContaining("이유");
        performance(SECOND, "measurement_contaminated");
        Path next = Files.createDirectories(parent.resolve("e001-v2-invalidated/distribution-" + SECOND));
        Files.move(root.resolve("performance"), next.resolve("performance"));

        // when & then
        assertThatThrownBy(() -> performance(THIRD, "measurement_contaminated")).isInstanceOf(IOException.class).hasMessageContaining("재시도 한 번");
        assertThat(parent.resolve("e001-v2-invalidated/performance-" + FIRST + "/performance.csv")).exists();
        assertThat(parent.resolve("e001-v2-invalidated/performance-" + SECOND + "/performance.csv")).exists();
    }

    @Test
    void producer_실패와_승격_실패는_partial을_공식_결과로_보이지_않게_보존한다() throws IOException {
        // when & then
        assertThatThrownBy(() -> E001SidecarStore.publish(parent, "performance", FIRST, "", METADATA, (staging, path, source) -> {
            E001ArtifactFormat.write(staging.resolve("partial.csv"), "preserve");
            throw new IOException("injected");
        })).isInstanceOf(IOException.class);
        assertThat(parent.resolve("e001-v2-invalidated/performance-" + FIRST + "/partial.csv")).hasContent("preserve");
        assertThat(E001RunContext.read(parent.resolve("e001-v2-invalidated/performance-" + FIRST + "/manifest.json"))
                .path("machineFileCount").asInt()).isEqualTo(2);
        assertThat(root.resolve("performance")).doesNotExist();
        assertThatThrownBy(() -> E001SidecarStore.publish(parent, "performance", SECOND, "", METADATA,
                E001RunnerFixture::performance, (source, target) -> {
                    if (target.equals(root.resolve("performance"))) {
                        throw new IOException("injected promotion failure");
                    }
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                })).isInstanceOf(IOException.class);
        assertThat(parent.resolve("e001-v2-invalidated/performance-" + SECOND + "/performance.csv")).exists();
        assertThat(root.resolve("performance")).doesNotExist();
    }

    @Test
    void atomic_probe_실패는_기존_공식_sidecar를_그대로_둔다() throws IOException {
        // given
        Path current = performance(FIRST, "");
        String before = Files.readString(current.resolve("manifest.json"));

        // when & then
        assertThatThrownBy(() -> E001SidecarStore.publish(parent, "performance", SECOND, "measurement_contaminated", METADATA,
                E001RunnerFixture::performance, (source, target) -> { throw new IOException("no atomic move"); }))
                .isInstanceOf(IOException.class);
        assertThat(current.resolve("manifest.json")).hasContent(before);
    }

    @Test
    void 유효한_성능_없이는_블라인드를_만들지_않고_변경된_성능도_거절한다() throws IOException {
        // when & then
        assertThatThrownBy(() -> blind(FIRST, "")).isInstanceOf(IOException.class);
        performance(FIRST, "");
        Files.writeString(root.resolve("performance/performance.csv"), "changed");
        assertThatThrownBy(() -> blind(SECOND, "")).isInstanceOf(IOException.class).hasMessageContaining("변경");
        assertThat(root.resolve("blind")).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 노출된_블라인드는_D와_E_포함_여부별_전체_응답을_무효화해_보존한다(boolean includeE) throws IOException {
        // given
        Path scenarioParent = Files.createDirectory(parent.resolve(includeE ? "with-e" : "d-only-blind"));
        Path scenarioRoot = E001RunnerFixture.distribution(scenarioParent, includeE);
        performance(scenarioParent, FIRST, "");
        Path current = blind(scenarioParent, FIRST, "");
        int pairs = includeE ? 8 : 4;
        String original = responses(pairs);
        Files.writeString(current.resolve("reviewer-package/blind-responses.csv"), original);
        Files.writeString(current.resolve("reviewer-package/blind-responses-invalidated-" + THIRD + ".csv"), "interrupted");

        // when
        blind(scenarioParent, SECOND, "blind_exposed");

        // then
        Path archived = scenarioParent.resolve("e001-v2-invalidated/blind-" + FIRST);
        assertThat(archived.resolve("coordinator-only/blind-responses-before-invalidation.csv")).hasContent(original);
        List<List<String>> invalidated = E001Csv.read(Files.readString(archived.resolve("reviewer-package/blind-responses.csv")));
        assertThat(invalidated).hasSize((pairs * 5) + 1);
        assertThat(invalidated.subList(1, invalidated.size())).allSatisfy(row -> {
            assertThat(row.get(5)).isEqualTo("invalidated");
            assertThat(row.get(6)).isEqualTo("blind_exposed");
        });
        assertThat(archived.resolve("reviewer-package/blind-responses-invalidated-" + THIRD + ".csv")).hasContent("interrupted");
        assertThat(current.resolve("reviewer-package/blind-responses.csv")).doesNotExist();
        assertThatThrownBy(() -> performance(scenarioParent, SECOND, "measurement_contaminated"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("블라인드");
        assertThat(scenarioRoot.resolve("blind")).exists();
    }

    @Test
    void 노출된_블라인드의_재생성이_실패해도_성능_재측정을_막는다() throws IOException {
        // given
        Path currentPerformance = performance(FIRST, "");
        String performanceManifest = Files.readString(currentPerformance.resolve("manifest.json"));
        blind(FIRST, "");

        // when
        assertThatThrownBy(() -> E001SidecarStore.publish(
                parent, "blind", SECOND, "blind_exposed", METADATA,
                (staging, path, source) -> { throw new IOException("injected blind failure"); }
        )).isInstanceOf(IOException.class);

        // then
        assertThat(root.resolve("blind")).doesNotExist();
        assertThat(parent.resolve("e001-v2-invalidated/blind-" + FIRST)).exists();
        assertThat(parent.resolve("e001-v2-invalidated/blind-" + SECOND)).exists();
        assertThatThrownBy(() -> performance(SECOND, "measurement_contaminated"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("블라인드");
        assertThat(currentPerformance.resolve("manifest.json")).hasContent(performanceManifest);
    }

    @Test
    void 중단된_블라인드의_모호한_checksum과_manifest_링크는_재측정으로_우회하지_않는다() throws IOException {
        // given
        Path current = performance(FIRST, "");
        String before = Files.readString(current.resolve("manifest.json"));
        Path staging = Files.createDirectory(parent.resolve("e001-v2-blind-staging-" + SECOND));
        Path manifest = staging.resolve("manifest.json");
        Files.writeString(manifest, E001ArtifactFormat.json(Map.of("kind", "blind", "runId", SECOND)));

        // when & then
        assertThatThrownBy(() -> performance(THIRD, "measurement_contaminated"))
                .isInstanceOf(IOException.class).hasMessageContaining("블라인드 실행 이력");
        Files.move(manifest, staging.resolve("original.json"));
        Files.createSymbolicLink(manifest, Path.of("original.json"));
        assertThatThrownBy(() -> performance(THIRD, "measurement_contaminated"))
                .isInstanceOf(IOException.class).hasMessageContaining("일반 파일");
        assertThat(current.resolve("manifest.json")).hasContent(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"performance", "blind"})
    void 분포_previous에_미보존_이력이_남으면_두_sidecar를_모두_차단한다(String kind) throws IOException {
        // given
        Path current = performance(FIRST, "");
        String before = Files.readString(current.resolve("manifest.json"));
        Path previous = Files.createDirectory(parent.resolve("e001-v2-previous-" + FIRST));
        Files.writeString(previous.resolve("marker.txt"), "preserve history before recovery");

        // when & then
        assertThatThrownBy(() -> E001SidecarStore.publish(parent, kind, SECOND, "", METADATA,
                (staging, path, source) -> { throw new AssertionError("복구 전에 producer를 실행하면 안 돼요."); }))
                .isInstanceOf(IOException.class).hasMessageContaining("previous");
        assertThat(previous.resolve("marker.txt")).hasContent("preserve history before recovery");
        assertThat(current.resolve("manifest.json")).hasContent(before);
    }

    @Test
    void 보관된_블라인드의_manifest_누락이나_경로_ID_불일치를_새_측정으로_우회하지_않는다() throws IOException {
        // given
        Path current = performance(FIRST, "");
        String before = Files.readString(current.resolve("manifest.json"));
        Path archived = Files.createDirectory(parent.resolve("e001-v2-invalidated/blind-" + SECOND));

        // when & then
        assertThatThrownBy(() -> performance(THIRD, "measurement_contaminated"))
                .isInstanceOf(IOException.class).hasMessageContaining("일반 파일");
        Files.writeString(archived.resolve("manifest.json"), E001ArtifactFormat.json(Map.of(
                "kind", "blind", "runId", THIRD, "distributionChecksum", "a".repeat(64))));
        assertThatThrownBy(() -> performance(THIRD, "measurement_contaminated"))
                .isInstanceOf(IOException.class).hasMessageContaining("블라인드 실행 이력");
        assertThat(current.resolve("manifest.json")).hasContent(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "checksum", "kind", "runId"})
    void 손상된_성능_이력을_재시도_횟수에서_빼지_않는다(String invalid) throws IOException {
        // given
        Path current = performance(FIRST, "");
        String before = Files.readString(current.resolve("manifest.json"));
        Path archived = Files.createDirectory(parent.resolve("e001-v2-invalidated/performance-" + SECOND));
        if (!invalid.equals("missing")) {
            Map<String, Object> values = new HashMap<>(Map.of("kind", "performance", "runId", SECOND,
                    "distributionChecksum", E001ArtifactFormat.sha256(root.resolve("checksums.sha256"))));
            values.put(invalid.equals("checksum") ? "distributionChecksum" : invalid, "invalid");
            Files.writeString(archived.resolve("manifest.json"), E001ArtifactFormat.json(values));
        }

        // when & then
        assertThatThrownBy(() -> performance(THIRD, "measurement_contaminated"))
                .isInstanceOf(IOException.class).hasMessageContaining("성능 실행");
        assertThat(current.resolve("manifest.json")).hasContent(before);
        assertThat(parent.resolve("e001-v2-performance-staging-" + THIRD)).doesNotExist();
    }

    @Test
    void 이름이_손상된_블라인드_staging도_재측정_전에_확인한다() throws IOException {
        // given
        Path current = performance(FIRST, "");
        String before = Files.readString(current.resolve("manifest.json"));
        Files.createDirectory(parent.resolve("e001-v2-blind-staging-invalid"));

        // when & then
        assertThatThrownBy(() -> performance(SECOND, "measurement_contaminated"))
                .isInstanceOf(IOException.class).hasMessageContaining("staging 이름");
        assertThat(current.resolve("manifest.json")).hasContent(before);
    }

    private Path performance(String id, String reason) throws IOException {
        return performance(parent, id, reason);
    }

    private Path performance(Path reports, String id, String reason) throws IOException {
        return E001SidecarStore.publish(reports, "performance", id, reason, METADATA, E001RunnerFixture::performance);
    }

    private Path publishPerformance(String id, long dNsPerPoint, long eNsPerPoint, boolean duplicateBatch)
            throws IOException {
        return E001SidecarStore.publish(parent, "performance", id, "", METADATA, (staging, path, source) -> {
            List<E001Performance.Batch> batches = performanceBatches(source, dNsPerPoint, eNsPerPoint);
            if (duplicateBatch) {
                E001Performance.Batch last = batches.getLast();
                batches.set(batches.size() - 1, E001Performance.Batch.of(
                        last.modelId(), last.parameterSetId(), last.phase(), 0, last.points(), last.elapsed()
                ));
            }
            E001Performance.write(staging.resolve("performance.csv"), batches);
            E001ArtifactFormat.write(staging.resolve("environment.json"), "{}\n");
            return Map.of(
                    "machineRows", Map.of("performance.csv", batches.size()),
                    "dPerformanceGatePassed", true,
                    "ePerformanceGatePassed", true
            );
        });
    }

    private List<E001Performance.Batch> performanceBatches(
            E001RunContext.Source source,
            long dNsPerPoint,
            long eNsPerPoint
    ) {
        List<E001Performance.Batch> batches = new ArrayList<>();
        for (String phase : List.of("warmup", "measurement")) {
            int count = phase.equals("warmup") ? 5 : 10;
            for (int index = 0; index < count; index++) {
                List<E001Parameters> parameters = index % 2 == 0
                        ? List.of(source.d(), source.e())
                        : List.of(source.e(), source.d());
                for (E001Parameters parameter : parameters) {
                    long nsPerPoint = parameter.modelId().equals("D") ? dNsPerPoint : eNsPerPoint;
                    batches.add(E001Performance.Batch.of(
                            parameter.modelId(), parameter.parameterSetId(), phase, index, 100_000, nsPerPoint * 100_000
                    ));
                }
            }
        }
        return batches;
    }

    private Path blind(String id, String reason) throws IOException {
        return blind(parent, id, reason);
    }

    private Path blind(Path reports, String id, String reason) throws IOException {
        return E001SidecarStore.publish(reports, "blind", id, reason, METADATA, (staging, path, source) -> {
            Files.createDirectory(staging.resolve("coordinator-only"));
            Files.createDirectory(staging.resolve("reviewer-package"));
            E001ArtifactFormat.write(staging.resolve("reviewer-package/blind-pairs.csv"), "tiny fake panel fixture");
            int pairs = E001SidecarStore.verifyPerformance(path, source) ? 8 : 4;
            return Map.of("machineRows", Map.of("reviewer-package/blind-pairs.csv", pairs));
        });
    }

    private String responses(int pairs) {
        StringBuilder csv = new StringBuilder(E001Blind.BALLOT_HEADER);
        for (int reviewer = 1; reviewer <= 5; reviewer++) {
            for (int pair = 1; pair <= pairs; pair++) {
                csv.append(E001ArtifactFormat.csv(List.of(
                        "reviewer-" + reviewer,
                        "pair-" + pair,
                        "left",
                        "right",
                        "neither",
                        "valid",
                        ""
                )));
            }
        }
        return csv.toString();
    }
}
