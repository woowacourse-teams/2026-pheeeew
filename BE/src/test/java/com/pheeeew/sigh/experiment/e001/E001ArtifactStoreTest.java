package com.pheeeew.sigh.experiment.e001;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class E001ArtifactStoreTest {

    private static final String RUN = "a".repeat(32);
    private static final String OLD = "b".repeat(32);

    @TempDir
    Path parent;

    @Test
    void 두_실행을_검증한_뒤에만_두번째_staging을_공식_결과로_승격한다() throws IOException {
        // given
        AtomicInteger executions = new AtomicInteger();

        // when
        Path official = E001ArtifactStore.publish(parent, RUN, root -> {
            executions.incrementAndGet();
            fixture(root, "stable");
        });

        // then
        assertThat(executions.get()).isEqualTo(2);
        E001Checksums.verify(official);
        assertThat(official.resolve("verification-run1-checksums.sha256"))
                .hasSameTextualContentAs(parent.resolve("e001-run1-staging/checksums.sha256"));
        assertThat(parent.resolve("e001-run2-staging")).doesNotExist();
    }

    @Test
    void 기존_공식_결과와_staging은_덮어쓰지_않고_보존한다() throws IOException {
        // given
        oldDirectory("e001", "old");
        oldDirectory("e001-run1-staging", "partial-1");
        oldDirectory("e001-run2-staging", "partial-2");

        // when
        E001ArtifactStore.publish(parent, RUN, root -> fixture(root, "new"));

        // then
        assertThat(parent.resolve("e001-invalidated/distribution-" + RUN + "/marker.txt")).hasContent("old");
        assertThat(parent.resolve("e001-invalidated/distribution-staging-" + RUN + "-1/marker.txt")).hasContent("partial-1");
        assertThat(parent.resolve("e001-invalidated/distribution-staging-" + RUN + "-2/marker.txt")).hasContent("partial-2");
        assertThat(parent.resolve("e001/manifest.json")).hasContent("new\n");
    }

    @Test
    void checksum_불일치는_기존_공식_결과와_두_staging을_그대로_남긴다() throws IOException {
        // given
        oldDirectory("e001", "old");
        AtomicInteger executions = new AtomicInteger();

        // when & then
        assertThatThrownBy(() -> E001ArtifactStore.publish(parent, RUN,
                root -> fixture(root, "run-" + executions.incrementAndGet()))).isInstanceOf(IOException.class);
        assertThat(parent.resolve("e001/marker.txt")).hasContent("old");
        assertThat(parent.resolve("e001-run1-staging/manifest.json")).hasContent("run-1\n");
        assertThat(parent.resolve("e001-run2-staging/manifest.json")).hasContent("run-2\n");
        assertThat(parent.resolve("e001-run2-staging/failure.json")).exists();
    }

    @Test
    void 두번째_atomic_move_실패_시_previous를_즉시_복원한다() throws IOException {
        // given
        oldDirectory("e001", "old");

        // when & then
        assertThatThrownBy(() -> E001ArtifactStore.publish(parent, RUN, root -> fixture(root, "new"), (source, target) -> {
            if (source.getFileName().toString().equals("e001-run2-staging")) {
                throw new IOException("injected promotion failure");
            }
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        })).isInstanceOf(IOException.class);
        assertThat(parent.resolve("e001/marker.txt")).hasContent("old");
        assertThat(parent.resolve("e001-previous-" + RUN)).doesNotExist();
        assertThat(parent.resolve("e001-run2-staging/failure.json")).exists();
    }

    @Test
    void 즉시_복원도_실패하면_previous를_보존하고_다음_실행에서_복원한다() throws IOException {
        // given
        oldDirectory("e001", "old");

        // when & then
        assertThatThrownBy(() -> E001ArtifactStore.publish(parent, RUN, root -> fixture(root, "new"), (source, target) -> {
            String name = source.getFileName().toString();
            if (name.equals("e001-run2-staging") || name.startsWith("e001-previous-")) {
                throw new IOException("injected move failure");
            }
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        })).isInstanceOf(IOException.class);
        assertThat(parent.resolve("e001")).doesNotExist();
        assertThat(parent.resolve("e001-previous-" + RUN + "/marker.txt")).hasContent("old");
        assertThatThrownBy(() -> E001ArtifactStore.publish(parent, OLD, root -> {
            throw new IOException("stop after recovery");
        })).isInstanceOf(IOException.class);
        assertThat(parent.resolve("e001/marker.txt")).hasContent("old");
    }

    @Test
    void 공식_결과와_previous가_함께_있으면_previous만_격리한다() throws IOException {
        // given
        oldDirectory("e001", "current");
        oldDirectory("e001-previous-" + OLD, "previous");

        // when & then
        assertThatThrownBy(() -> E001ArtifactStore.publish(parent, RUN, root -> {
            throw new IOException("stop after recovery");
        })).isInstanceOf(IOException.class);
        assertThat(parent.resolve("e001/marker.txt")).hasContent("current");
        assertThat(parent.resolve("e001-invalidated/distribution-" + OLD + "/marker.txt")).hasContent("previous");
    }

    @Test
    void previous가_둘이면_어느_디렉터리도_자동으로_옮기지_않는다() throws IOException {
        // given
        oldDirectory("e001-previous-" + RUN, "first");
        oldDirectory("e001-previous-" + OLD, "second");

        // when & then
        assertThatThrownBy(() -> E001ArtifactStore.publish(parent, "c".repeat(32), root -> fixture(root, "new")))
                .isInstanceOf(IOException.class);
        assertThat(parent.resolve("e001-previous-" + RUN + "/marker.txt")).hasContent("first");
        assertThat(parent.resolve("e001-previous-" + OLD + "/marker.txt")).hasContent("second");
        assertThat(parent.resolve("e001-invalidated")).doesNotExist();
    }

    @Test
    void atomic_move_probe_실패는_기존_결과를_건드리지_않는다() throws IOException {
        // given
        oldDirectory("e001", "old");

        // when & then
        assertThatThrownBy(() -> E001ArtifactStore.publish(parent, RUN, root -> fixture(root, "new"), (source, target) -> {
            if (source.getFileName().toString().startsWith("e001-atomic-probe-")) {
                throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "injected");
            }
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        })).isInstanceOf(AtomicMoveNotSupportedException.class);
        assertThat(parent.resolve("e001/marker.txt")).hasContent("old");
        assertThat(parent.resolve("e001-run2-staging/failure.json")).exists();
    }

    @Test
    void 중첩된_동시_실행은_lock에서_거절한다() throws IOException {
        // when
        E001ArtifactStore.publish(parent, RUN, root -> {
            assertThatThrownBy(() -> E001ArtifactStore.publish(parent, OLD, inner -> fixture(inner, "racing")))
                    .isInstanceOf(IOException.class).hasMessageContaining("진행 중");
            fixture(root, "stable");
        });

        // then
        assertThat(parent.resolve("e001/manifest.json")).hasContent("stable\n");
    }

    @Test
    void symbolic_link_공식_경로는_따라가지_않는다() throws IOException {
        // given
        Path outside = oldDirectory("outside", "keep");
        Files.createSymbolicLink(parent.resolve("e001"), outside);

        // when & then
        assertThatThrownBy(() -> E001ArtifactStore.publish(parent, RUN, root -> fixture(root, "new")))
                .isInstanceOf(IOException.class);
        assertThat(outside.resolve("marker.txt")).hasContent("keep");
    }

    @Test
    void checksum_명세만_같아도_실제_파일이_다르면_승격하지_않는다() throws IOException {
        // when & then
        assertThatThrownBy(() -> E001ArtifactStore.publish(parent, RUN, root -> {
            fixture(root, "stable");
            Files.writeString(root.resolve("metrics.csv"), "tampered");
        })).isInstanceOf(IOException.class);
        assertThat(parent.resolve("e001")).doesNotExist();
        assertThat(parent.resolve("e001-run1-staging/failure.json")).exists();
    }

    @Test
    void 승격_후_archive_실패는_새_결과와_previous를_보존하고_다음_실행에서_정리한다() throws IOException {
        // given
        oldDirectory("e001", "old");

        // when & then
        assertThatThrownBy(() -> E001ArtifactStore.publish(parent, RUN, root -> fixture(root, "new"), (source, target) -> {
            if (source.getFileName().toString().startsWith("e001-previous-") && target.startsWith(parent.resolve("e001-invalidated"))) {
                throw new IOException("injected archive failure");
            }
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        })).isInstanceOf(IOException.class);
        assertThat(parent.resolve("e001/manifest.json")).hasContent("new\n");
        assertThat(parent.resolve("e001-previous-" + RUN + "/marker.txt")).hasContent("old");
        assertThat(Files.readString(parent.resolve("e001-run1-staging/failure.json"))).contains("\"officialPromoted\":true");
        assertThatThrownBy(() -> E001ArtifactStore.publish(parent, OLD, root -> {
            throw new IOException("stop after recovery");
        })).isInstanceOf(IOException.class);
        assertThat(parent.resolve("e001/manifest.json")).hasContent("new\n");
        assertThat(parent.resolve("e001-invalidated/distribution-" + RUN + "/marker.txt")).hasContent("old");
    }

    private Path oldDirectory(String name, String value) throws IOException {
        Path path = Files.createDirectory(parent.resolve(name));
        Files.writeString(path.resolve("marker.txt"), value);
        return path;
    }

    private static void fixture(Path path, String content) throws IOException {
        for (String name : E001Checksums.REQUIRED) {
            E001ArtifactFormat.write(path.resolve(name), content + "\n");
        }
        E001Checksums.write(path);
    }
}
