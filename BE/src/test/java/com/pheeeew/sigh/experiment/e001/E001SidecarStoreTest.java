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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

        // when
        Path result = performance(FIRST, "");

        // then
        assertThat(result).isEqualTo(root.resolve("performance"));
        var manifest = E001RunContext.read(result.resolve("manifest.json"));
        assertThat(manifest.path("status").asString()).isEqualTo("valid");
        assertThat(manifest.path("machineFileCount").asInt()).isEqualTo(3);
        assertThat(manifest.path("machineRows").path("performance.csv").asInt()).isEqualTo(30);
        assertThat(root.resolve("checksums.sha256")).hasContent(checksum);
        E001Checksums.verify(root);
        E001SidecarStore.verifyPerformance(root, E001ArtifactFormat.sha256(root.resolve("checksums.sha256")));
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
        assertThatThrownBy(() -> performance(FIRST, "")).isInstanceOf(IOException.class).hasMessageContaining("e-review-ready");
        assertThat(root.resolve("performance")).doesNotExist();
    }

    @Test
    void 명시적으로_무효화한_전체_성능은_한번만_재시도하고_세번째는_막는다() throws IOException {
        // given
        performance(FIRST, "");

        // when & then
        assertThatThrownBy(() -> performance(SECOND, "")).isInstanceOf(IOException.class).hasMessageContaining("이유");
        performance(SECOND, "measurement_contaminated");
        assertThat(E001RunContext.read(parent.resolve("e001-invalidated/performance-" + FIRST + "/manifest.json"))
                .path("invalidationReason").asString()).isEqualTo("measurement_contaminated");
        assertThatThrownBy(() -> performance(THIRD, "measurement_contaminated")).isInstanceOf(IOException.class).hasMessageContaining("재시도 한 번");
        assertThat(root.resolve("performance")).doesNotExist();
        assertThat(parent.resolve("e001-invalidated/performance-" + SECOND + "/performance.csv")).exists();
        assertThat(parent.resolve("e001-performance-staging-" + THIRD)).doesNotExist();
    }

    @Test
    void 실패한_partial과_남은_manifest_임시파일을_보존하며_재시도_횟수에_포함한다() throws IOException {
        // given
        Path interrupted = Files.createDirectory(parent.resolve("e001-performance-staging-" + FIRST));
        E001ArtifactFormat.write(interrupted.resolve("manifest.json"), E001ArtifactFormat.json(Map.of(
                "kind", "performance", "runId", FIRST, "status", "running",
                "distributionChecksum", E001ArtifactFormat.sha256(root.resolve("checksums.sha256")))));
        E001ArtifactFormat.write(interrupted.resolve("manifest-next-" + THIRD + ".json"), "partial");

        // when
        performance(SECOND, "");

        // then
        Path archived = parent.resolve("e001-invalidated/performance-" + FIRST);
        assertThat(archived.resolve("manifest-next-" + THIRD + ".json")).hasContent("partial");
        assertThat(E001RunContext.read(archived.resolve("manifest.json")).path("invalidationReason").asString()).isEqualTo("interrupted");
        assertThatThrownBy(() -> performance(THIRD, "measurement_contaminated")).isInstanceOf(IOException.class).hasMessageContaining("재시도 한 번");
    }

    @Test
    void 분포_재승격으로_보존된_성능도_같은_checksum의_횟수에_포함한다() throws IOException {
        // given
        performance(FIRST, "");
        Path prior = Files.createDirectories(parent.resolve("e001-invalidated/distribution-" + FIRST));
        Files.move(root.resolve("performance"), prior.resolve("performance"));
        assertThatThrownBy(() -> performance(SECOND, "")).isInstanceOf(IOException.class).hasMessageContaining("이유");
        performance(SECOND, "measurement_contaminated");
        Path next = Files.createDirectories(parent.resolve("e001-invalidated/distribution-" + SECOND));
        Files.move(root.resolve("performance"), next.resolve("performance"));

        // when & then
        assertThatThrownBy(() -> performance(THIRD, "measurement_contaminated")).isInstanceOf(IOException.class).hasMessageContaining("재시도 한 번");
        assertThat(parent.resolve("e001-invalidated/performance-" + FIRST + "/performance.csv")).exists();
        assertThat(parent.resolve("e001-invalidated/performance-" + SECOND + "/performance.csv")).exists();
    }

    @Test
    void producer_실패와_승격_실패는_partial을_공식_결과로_보이지_않게_보존한다() throws IOException {
        // when & then
        assertThatThrownBy(() -> E001SidecarStore.publish(parent, "performance", FIRST, "", METADATA, (staging, path, source) -> {
            E001ArtifactFormat.write(staging.resolve("partial.csv"), "preserve");
            throw new IOException("injected");
        })).isInstanceOf(IOException.class);
        assertThat(parent.resolve("e001-invalidated/performance-" + FIRST + "/partial.csv")).hasContent("preserve");
        assertThat(E001RunContext.read(parent.resolve("e001-invalidated/performance-" + FIRST + "/manifest.json"))
                .path("machineFileCount").asInt()).isEqualTo(2);
        assertThat(root.resolve("performance")).doesNotExist();
        assertThatThrownBy(() -> E001SidecarStore.publish(parent, "performance", SECOND, "", METADATA,
                E001RunnerFixture::performance, (source, target) -> {
                    if (target.equals(root.resolve("performance"))) {
                        throw new IOException("injected promotion failure");
                    }
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                })).isInstanceOf(IOException.class);
        assertThat(parent.resolve("e001-invalidated/performance-" + SECOND + "/performance.csv")).exists();
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

    @Test
    void 노출된_블라인드는_응답_원본과_무효_응답을_네쌍_전체와_보존한다() throws IOException {
        // given
        performance(FIRST, "");
        Path current = blind(FIRST, "");
        String original = E001Blind.BALLOT_HEADER + "reviewer-1,pair,left,right,neither,valid,\n";
        Files.writeString(current.resolve("reviewer-package/blind-responses.csv"), original);
        Files.writeString(current.resolve("reviewer-package/blind-responses-invalidated-" + THIRD + ".csv"), "interrupted");

        // when
        blind(SECOND, "blind_exposed");

        // then
        Path archived = parent.resolve("e001-invalidated/blind-" + FIRST);
        assertThat(archived.resolve("coordinator-only/blind-responses-before-invalidation.csv")).hasContent(original);
        assertThat(Files.readString(archived.resolve("reviewer-package/blind-responses.csv"))).contains(",invalidated,blind_exposed\n");
        assertThat(archived.resolve("reviewer-package/blind-responses-invalidated-" + THIRD + ".csv")).hasContent("interrupted");
        assertThat(current.resolve("reviewer-package/blind-responses.csv")).doesNotExist();
        assertThatThrownBy(() -> performance(SECOND, "measurement_contaminated")).isInstanceOf(IOException.class).hasMessageContaining("블라인드");
    }

    private Path performance(String id, String reason) throws IOException {
        return E001SidecarStore.publish(parent, "performance", id, reason, METADATA, E001RunnerFixture::performance);
    }

    private Path blind(String id, String reason) throws IOException {
        return E001SidecarStore.publish(parent, "blind", id, reason, METADATA, (staging, path, source) -> {
            Files.createDirectory(staging.resolve("coordinator-only"));
            Files.createDirectory(staging.resolve("reviewer-package"));
            E001ArtifactFormat.write(staging.resolve("reviewer-package/blind-pairs.csv"), "tiny fake panel fixture");
            return Map.of("machineRows", Map.of("reviewer-package/blind-pairs.csv", 4));
        });
    }
}
