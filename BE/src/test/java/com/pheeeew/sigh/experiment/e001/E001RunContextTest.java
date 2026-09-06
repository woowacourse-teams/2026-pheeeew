package com.pheeeew.sigh.experiment.e001;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class E001RunContextTest {

    @TempDir
    Path directory;

    @Test
    void 같은_커밋의_선택_파라미터만_복원하고_재현_checksum도_검증한다() throws IOException {
        // given
        Path root = E001RunnerFixture.distribution(directory);

        // when
        var source = E001RunContext.source(root, E001RunnerFixture.METADATA);

        // then
        assertThat(source.d()).isEqualTo(E001RunnerFixture.D);
        assertThat(source.e()).isEqualTo(E001RunnerFixture.E);
        assertThatThrownBy(() -> E001RunContext.source(root, E001Artifacts.Metadata.of("a".repeat(40), "c".repeat(40), Map.of())))
                .isInstanceOf(IOException.class).hasMessageContaining("runner");
        Files.writeString(root.resolve("verification-run1-checksums.sha256"), "different");
        assertThatThrownBy(() -> E001RunContext.source(root, E001RunnerFixture.METADATA)).isInstanceOf(IOException.class).hasMessageContaining("두 실행");
    }

    @Test
    void 선택_ID와_파라미터_본문이_다르면_재튜닝하지_않고_거절한다() throws IOException {
        // given
        Path root = E001RunnerFixture.distribution(directory);
        var manifest = E001RunContext.readMap(root.resolve("manifest.json"));
        manifest.put("selectedE", "e-not-registered");
        Files.writeString(root.resolve("manifest.json"), E001ArtifactFormat.json(manifest));
        E001RunnerFixture.seal(root);

        // when & then
        assertThatThrownBy(() -> E001RunContext.source(root, E001RunnerFixture.METADATA))
                .isInstanceOf(IOException.class).hasMessageContaining("정확히 한 번");
    }

    @Test
    void 실행_환경은_허용_정보만_기록하고_관련_미커밋_변경을_거절한다() throws Exception {
        // given: 실제 저장소 설정을 바꾸지 않는 임시 Git fixture예요.
        git("init", "--quiet");
        Path protocol = directory.resolve("docs/experiments/e001-map-star-location-distribution/README.md");
        Files.createDirectories(protocol.getParent());
        Files.copy(Path.of("docs/experiments/e001-map-star-location-distribution/README.md"), protocol);
        Files.writeString(protocol.resolveSibling("PROTOCOL-V2.md"), "v2 fixture");
        Files.writeString(directory.resolve("build.gradle"), "fixture");
        git("add", "--", "build.gradle", "docs");
        git("-c", "user.name=E001", "-c", "user.email=e001@example.invalid", "-c", "commit.gpgsign=false", "commit", "--quiet", "-m", "fixture");

        // when
        var metadata = E001RunContext.capture(directory, "test-gradle");

        // then
        assertThat(metadata.runnerSha()).matches("[0-9a-f]{40}");
        assertThat(metadata.protocolSha()).isEqualTo(metadata.runnerSha());
        assertThat(metadata.environment()).containsOnlyKeys("jdkVendor", "jdkVersion", "osName", "osVersion", "osArch", "processors",
                "gradleVersion", "locale", "timezone", "protocolSha", "runnerSha", "gitDirty");
        assertThat(metadata.environment()).containsEntry("gitDirty", false);
        Files.writeString(directory.resolve("unrelated.txt"), "unrelated");
        assertThat(E001RunContext.capture(directory, "test-gradle").environment()).containsEntry("gitDirty", true);
        Files.writeString(directory.resolve("build.gradle"), "changed");
        assertThatThrownBy(() -> E001RunContext.capture(directory, "test-gradle")).isInstanceOf(IOException.class).hasMessageContaining("먼저 커밋");
        Files.writeString(protocol, "changed baseline");
        git("add", "--", "build.gradle", "docs");
        git("-c", "user.name=E001", "-c", "user.email=e001@example.invalid", "-c", "commit.gpgsign=false", "commit", "--quiet", "-m", "changed fixture");
        assertThatThrownBy(() -> E001RunContext.capture(directory, "test-gradle")).isInstanceOf(IOException.class).hasMessageContaining("동결된 v1");
    }

    @Test
    void D_only는_탈락한_E_이력을_후보로_복원하지_않는다() throws IOException {
        Path root = E001RunnerFixture.distribution(directory, false);
        var source = E001RunContext.source(root, E001RunnerFixture.METADATA);
        assertThat(source.d()).isEqualTo(E001RunnerFixture.D);
        assertThat(source.e()).isNull();
    }

    @Test
    void checksum을_다시_만들어도_v1이나_다른_기준_문서를_v2로_읽지_않는다() throws IOException {
        Path root = E001RunnerFixture.distribution(directory);
        var manifest = E001RunContext.readMap(root.resolve("manifest.json"));
        for (var entry : Map.<String, Object>of("protocolVersion", "E001-v1", "schemaVersion", 1,
                "baseProtocolBlobId", "c".repeat(40)).entrySet()) {
            var changed = new java.util.HashMap<>(manifest);
            changed.put(entry.getKey(), entry.getValue());
            Files.writeString(root.resolve("manifest.json"), E001ArtifactFormat.json(changed));
            E001RunnerFixture.seal(root);
            assertThatThrownBy(() -> E001RunContext.source(root, E001RunnerFixture.METADATA))
                    .isInstanceOf(IOException.class).hasMessageContaining("같은 v2");
        }
    }

    @Test
    void 새_경계_CSV가_없으면_두_실행의_checksum이_같아도_거부한다() throws IOException {
        Path root = E001RunnerFixture.distribution(directory);
        Files.delete(root.resolve("boundary-observations.csv"));
        assertThatThrownBy(() -> E001RunContext.source(root, E001RunnerFixture.METADATA)).isInstanceOf(IOException.class);
    }

    private void git(String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", directory.toString()));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        assertThat(process.waitFor()).isZero();
    }
}
