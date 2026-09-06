package com.pheeeew.sigh.experiment.e001;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class E001BlindTest {

    @TempDir
    Path directory;

    @Test
    void 별도_HMAC_domain으로_고정_nonce의_좌우_두장씩과_공개_ID를_계산한다() {
        // when
        var assignment = assignment();

        // then: OpenSSL HMAC으로 별도 계산한 vector예요.
        assertThat(assignment.pairs()).extracting(pair -> pair.id() + ":" + pair.left() + ":" + pair.scenario()).containsExactly(
                "469f746fda607f51:E:confirmation-grid-equal-n5000-per-center",
                "507406d5a52854d8:D:confirmation-single-n500",
                "cfc48becc97efe39:D:confirmation-grid-equal-n500-per-center",
                "d6746d521b613738:E:confirmation-single-n5000");
    }

    @Test
    void 평가자_package에는_네_비교그림과_빈_응답지만_넣고_key를_분리한다() throws IOException {
        // given
        Path root = Files.createDirectory(directory.resolve("source"));
        Path panels = Files.createDirectories(root.resolve("coordinator-only/model-panels"));
        for (String scenario : E001Blind.SCENARIOS) {
            for (var parameter : List.of(E001RunnerFixture.D, E001RunnerFixture.E)) {
                BufferedImage image = new BufferedImage(1200, 1200, BufferedImage.TYPE_INT_RGB);
                image.setRGB(0, 0, parameter.modelId().equals("D") ? 0xff0000 : 0x0000ff);
                ImageIO.write(image, "png", panels.resolve(parameter.parameterSetId() + "--" + scenario + ".png").toFile());
            }
        }
        Path staging = Files.createDirectory(directory.resolve("blind"));

        // when
        var assignment = assignment();
        E001Blind.write(staging, root, E001RunContext.Source.of("fixture", E001RunnerFixture.D, E001RunnerFixture.E), assignment);

        // then
        Path reviewer = staging.resolve("reviewer-package");
        try (var files = Files.walk(reviewer)) {
            assertThat(files.filter(Files::isRegularFile).map(path -> reviewer.relativize(path).toString()).toList())
                    .hasSize(6).noneMatch(name -> name.contains("key") || name.contains("responses") || name.contains("e-s120") || name.contains("d-s120"));
        }
        for (var pair : assignment.pairs()) {
            BufferedImage image = ImageIO.read(reviewer.resolve("renders/blind-pairs/" + pair.id() + ".png").toFile());
            assertThat(image.getWidth()).isEqualTo(2432);
            assertThat(image.getHeight()).isEqualTo(1200);
            assertThat(image.getRGB(0, 0) & 0xffffff).isEqualTo(pair.left().equals("E") ? 0x0000ff : 0xff0000);
            assertThat(image.getRGB(1232, 0) & 0xffffff).isEqualTo(pair.right().equals("E") ? 0x0000ff : 0xff0000);
            assertThat(image.getRGB(1200, 0) & 0xffffff).isZero();
            assertThat(image.getRGB(1231, 0) & 0xffffff).isZero();
        }
        assertThat(Files.readString(reviewer.resolve("blind-pairs.csv"))).doesNotContain(assignment.nonceHex(), "left_model", "e-s120", "d-s120");
        var ballot = E001Csv.read(Files.readString(reviewer.resolve("blind-ballot-template.csv")));
        assertThat(ballot).hasSize(5);
        assertThat(ballot.subList(1, 5)).allSatisfy(row -> assertThat(row).containsExactly("", row.get(1), "", "", "", "valid", ""));
        assertThat(Files.readString(staging.resolve("coordinator-only/blind-key.csv"))).contains(assignment.nonceHex());
    }

    @Test
    void 다섯명_전체_응답에서_세화면_네표_조건과_hotspot을_독립적으로_판정한다() {
        // given
        var assignment = assignment();
        List<List<String>> rows = favorable(assignment);

        // when & then
        assertThat(E001Blind.evaluate(assignment, rows)).isTrue();
        rows.set(0, ballot(rows.get(0), "tie", "tie", "neither"));
        assertThat(E001Blind.evaluate(assignment, rows)).isTrue();
        rows.set(1, ballot(rows.get(1), "tie", "tie", "both"));
        assertThat(E001Blind.evaluate(assignment, rows)).isTrue();
        rows.set(0, ballot(rows.get(0), "tie", "tie", "both"));
        assertThat(E001Blind.evaluate(assignment, rows)).isFalse();
    }

    @Test
    void 미완료_중복_무효_응답과_허용값_밖의_응답은_판정하지_않는다() {
        // given
        var assignment = assignment();
        var complete = favorable(assignment);
        List<List<List<String>>> invalid = new ArrayList<>();
        invalid.add(complete.subList(0, 19));
        var duplicate = new ArrayList<>(complete);
        duplicate.set(19, duplicate.getFirst());
        invalid.add(duplicate);
        for (int column : List.of(1, 2, 3, 4, 5, 6)) {
            var rows = new ArrayList<>(complete);
            var row = new ArrayList<>(rows.getFirst());
            row.set(column, "invalid");
            rows.set(0, row);
            invalid.add(rows);
        }

        // when & then
        for (var rows : invalid) {
            assertThatThrownBy(() -> E001Blind.evaluate(assignment, rows)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void shape와_natural은_각각_세화면_이상_네표를_얻어야_한다() {
        // given
        var assignment = assignment();
        var shapeFailure = favorable(assignment);
        var naturalFailure = favorable(assignment);
        for (int index : List.of(0, 1, 5, 6)) {
            var row = shapeFailure.get(index);
            shapeFailure.set(index, ballot(row, "tie", row.get(3), "neither"));
            naturalFailure.set(index, ballot(row, row.get(2), "tie", "neither"));
        }

        // when & then
        assertThat(E001Blind.evaluate(assignment, shapeFailure)).isFalse();
        assertThat(E001Blind.evaluate(assignment, naturalFailure)).isFalse();
    }

    @Test
    void CSV의_쉼표_따옴표_개행과_빈_마지막_열을_보존한다() throws IOException {
        // given
        List<String> row = List.of("reviewer,one", "a\"b", "c\nd", "");

        // when & then
        assertThat(E001Csv.read(E001ArtifactFormat.csv(row))).containsExactly(row);
        assertThat(E001Csv.read("a,b,\r\n")).containsExactly(List.of("a", "b", ""));
        assertThatThrownBy(() -> E001Csv.read("\"unclosed")).isInstanceOf(IOException.class);
    }

    private static E001Blind.Assignment assignment() {
        return E001Blind.assign(() -> HexFormat.of().parseHex("000102030405060708090a0b0c0d0e0f"));
    }

    private static List<List<String>> favorable(E001Blind.Assignment assignment) {
        List<List<String>> rows = new ArrayList<>();
        for (var pair : assignment.pairs()) {
            String side = pair.left().equals("E") ? "left" : "right";
            for (int reviewer = 0; reviewer < 5; reviewer++) {
                rows.add(List.of("reviewer-" + reviewer, pair.id(), side, side, "neither", "valid", ""));
            }
        }
        return rows;
    }

    private static List<String> ballot(List<String> row, String shape, String natural, String hotspot) {
        return List.of(row.get(0), row.get(1), shape, natural, hotspot, "valid", "");
    }
}
