package com.pheeeew.pressrace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.pressrace.exception.PressRaceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PressAdmissionTest {

    private static final UUID GENERATION = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @TempDir
    private Path directory;

    @Test
    void 필수_차단_파일_설정이_없으면_시작하지_않는다() {
        // when / then
        assertThatThrownBy(() -> new PressAdmission("", GENERATION, true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 최초에는_닫고_같은_파일로_재시작해도_열림과_차단_상태를_덮어쓰지_않는다() throws Exception {
        // given
        Path file = directory.resolve("admission");
        PressAdmission admission = new PressAdmission(file.toString(), GENERATION, true);
        String closed = Files.readString(file);
        assertThat(admission.isClosed()).isTrue();
        assertThatThrownBy(admission::openRevision).isInstanceOf(PressRaceException.class);

        // when / then
        new PressAdmission(file.toString(), GENERATION, true);
        assertThat(Files.readString(file)).isEqualTo(closed);
        String revision = UUID.randomUUID().toString();
        Files.writeString(file, "OPEN " + revision + " " + GENERATION + "\n");
        assertThat(new PressAdmission(file.toString(), GENERATION, true).openRevision()).isEqualTo(revision);
        Files.writeString(file, "PAUSED " + UUID.randomUUID() + " " + GENERATION + "\n");
        assertThatThrownBy(() -> new PressAdmission(file.toString(), GENERATION, true).openRevision())
                .isInstanceOf(PressRaceException.class);
    }

    @Test
    void 차단_후_다시_열렸어도_이전_허용_번호로_저장하지_못한다() throws Exception {
        // given
        Path file = directory.resolve("admission");
        PressAdmission admission = new PressAdmission(file.toString(), GENERATION, true);
        Files.writeString(file, "OPEN " + UUID.randomUUID() + " " + GENERATION + "\n");
        String previous = admission.openRevision();

        // when
        Files.writeString(file, "PAUSED " + UUID.randomUUID() + " " + GENERATION + "\n");
        Files.writeString(file, "OPEN " + UUID.randomUUID() + " " + GENERATION + "\n");

        // then
        assertThatThrownBy(() -> admission.requireOpen(previous)).isInstanceOfSatisfying(PressRaceException.class,
                exception -> assertThat(exception.code()).isEqualTo("SERVER_DRAINING"));
        admission.requireOpen(admission.openRevision());
    }

    @Test
    void 잘못된_내용과_다른_서버_세대와_심볼릭_링크는_닫힘으로_판정한다() throws Exception {
        // given
        Path file = directory.resolve("admission");
        PressAdmission admission = new PressAdmission(file.toString(), GENERATION, true);

        // when / then
        for (String malformed : new String[] {"OPEN", "OPEN " + UUID.randomUUID() + " " + UUID.randomUUID() + "\n",
                "OPEN not-a-revision " + GENERATION + "\n", "OPEN " + UUID.randomUUID() + " " + GENERATION,
                "OPEN " + UUID.randomUUID() + " " + GENERATION + "\n\n", "OPEN ".repeat(100)}) {
            Files.writeString(file, malformed);
            assertThat(admission.isClosed()).isTrue();
            assertThatThrownBy(admission::openRevision).isInstanceOf(PressRaceException.class);
        }
        Files.delete(file);
        Files.createSymbolicLink(file, directory.resolve("target"));
        Files.writeString(directory.resolve("target"), "OPEN " + UUID.randomUUID() + " " + GENERATION + "\n");
        assertThat(admission.isClosed()).isTrue();
    }

    @Test
    void 닫힘_확인은_검증한_닫힘_상태와_같은_번호와_세대만_기록한다() throws Exception {
        // given
        Path file = directory.resolve("admission");
        PressAdmission admission = new PressAdmission(file.toString(), GENERATION, true);
        String closed = Files.readString(file);

        // when
        admission.acknowledgeClosed();

        // then
        Path acknowledgement = directory.resolve("admission.closed");
        assertThat(Files.readString(acknowledgement)).isEqualTo(closed);
        Files.writeString(file, "PAUSED " + UUID.randomUUID() + " " + GENERATION + "\n");
        admission.acknowledgeClosed();
        assertThat(Files.readString(acknowledgement)).isEqualTo(closed);
    }
}
