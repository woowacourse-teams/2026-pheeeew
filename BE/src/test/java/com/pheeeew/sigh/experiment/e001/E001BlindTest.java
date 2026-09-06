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
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class E001BlindTest {

    private static final String NONCE = "000102030405060708090a0b0c0d0e0f";
    private static final E001Parameters A = E001Parameters.distance("A", 0);

    @TempDir
    Path directory;

    @Test
    void OpenSSL로_계산한_HMAC_vector와_family별_좌우_두장씩을_재현한다() {
        // when
        var ad = assignment(false);
        var adAndDe = assignment(true);

        // then
        assertThat(ad.pairs()).extracting(E001BlindTest::description).containsExactly(
                "3245e0bad49bdf5d:a-d:single-n500:D:A:review-ad-single-n500",
                "876f09199dafbad3:a-d:single-n5000:A:D:review-ad-single-n5000",
                "b53152b9bdb3b358:a-d:grid-equal-n500-per-center:D:A:review-ad-grid-equal-n500-per-center",
                "ce2b7326effd924d:a-d:grid-equal-n5000-per-center:A:D:review-ad-grid-equal-n5000-per-center"
        );
        assertThat(adAndDe.pairs()).extracting(E001BlindTest::description).containsExactly(
                "2b1eec48eb7e0cd5:d-e:grid-equal-n5000-per-center:E:D:confirmation-grid-equal-n5000-per-center",
                "3245e0bad49bdf5d:a-d:single-n500:D:A:review-ad-single-n500",
                "62817f116969e9e1:d-e:single-n500:E:D:confirmation-single-n500",
                "876f09199dafbad3:a-d:single-n5000:A:D:review-ad-single-n5000",
                "b53152b9bdb3b358:a-d:grid-equal-n500-per-center:D:A:review-ad-grid-equal-n500-per-center",
                "c60bf056fbd99510:d-e:single-n5000:D:E:confirmation-single-n5000",
                "ce2b7326effd924d:a-d:grid-equal-n5000-per-center:A:D:review-ad-grid-equal-n5000-per-center",
                "e5bc5219109102cc:d-e:grid-equal-n500-per-center:D:E:confirmation-grid-equal-n500-per-center"
        );
        assertThat(adAndDe.pairs()).extracting(E001Blind.Pair::id).doesNotHaveDuplicates().isSorted();
        assertThat(challengeLeftCount(adAndDe, "a-d", "D")).isEqualTo(2);
        assertThat(challengeLeftCount(adAndDe, "d-e", "E")).isEqualTo(2);
    }

    @Test
    void nonce를_clone하고_raw_16byte만_허용한다() {
        // given
        byte[] nonce = HexFormat.of().parseHex(NONCE);

        // when
        var assignment = E001Blind.assign(false, () -> nonce);
        java.util.Arrays.fill(nonce, (byte) 0xff);

        // then
        assertThat(assignment.nonceHex()).isEqualTo(NONCE);
        assertThat(assignment.pairs().getFirst().id()).isEqualTo("3245e0bad49bdf5d");
        assertThatThrownBy(() -> E001Blind.assign(false, () -> new byte[15]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> E001Blind.assign(false, () -> null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void E가_source에_있어도_AD만_요청하면_REVIEW_AD의_A와_D_네쌍만_쓴다() throws IOException {
        // given
        var assignment = assignment(false);
        var source = E001RunContext.Source.of("fixture", E001RunnerFixture.D, E001RunnerFixture.E);
        Path root = sourcePanels(assignment, source);
        Path staging = Files.createDirectory(directory.resolve("blind-ad"));

        // when
        E001Blind.write(staging, root, source, assignment);

        // then
        var publicRows = E001Csv.read(Files.readString(staging.resolve("reviewer-package/blind-pairs.csv")));
        assertThat(publicRows).hasSize(5);
        assertThat(publicRows.subList(1, 5)).extracting(row -> row.get(1)).containsExactlyInAnyOrder(
                "single-n500", "single-n5000", "grid-equal-n500-per-center", "grid-equal-n5000-per-center"
        );
        assertThat(Files.readString(staging.resolve("reviewer-package/blind-pairs.csv")))
                .doesNotContain("a-d", "review-ad-", "confirmation-", E001RunnerFixture.E.parameterSetId());
        assertThat(Files.readString(staging.resolve("coordinator-only/blind-key.csv")))
                .startsWith("nonce_hex,pair_id,family_id,scenario_id,left_model_id,right_model_id\n")
                .contains("a-d", "review-ad-")
                .doesNotContain("confirmation-");
        var keyRows = E001Csv.read(Files.readString(staging.resolve("coordinator-only/blind-key.csv")));
        assertThat(keyRows.subList(1, keyRows.size())).allSatisfy(row -> {
            assertThat(row).hasSize(6);
            assertThat(row.get(2)).isEqualTo("a-d");
            assertThat(row.subList(4, 6)).containsExactlyInAnyOrder("A", "D");
        });
        try (var files = Files.walk(staging.resolve("reviewer-package"))) {
            assertThat(files.filter(Files::isRegularFile).toList()).hasSize(6);
        }
    }

    @Test
    void 두_family는_서로_다른_D_panel을_쓰고_공개_CSV에는_중립_조건만_남긴다() throws IOException {
        // given
        var assignment = assignment(true);
        var source = E001RunContext.Source.of("fixture", E001RunnerFixture.D, E001RunnerFixture.E);
        Path root = sourcePanels(assignment, source);
        Path staging = Files.createDirectory(directory.resolve("blind-both"));

        // when
        E001Blind.write(staging, root, source, assignment);

        // then
        String publicCsv = Files.readString(staging.resolve("reviewer-package/blind-pairs.csv"));
        assertThat(publicCsv).doesNotContain("review-ad-", "confirmation-", "left_model", "right_model");
        var publicRows = E001Csv.read(publicCsv);
        assertThat(publicRows).hasSize(9);
        assertThat(publicRows.getFirst()).containsExactly("pair_id", "scenario_id", "image_path");
        assertThat(publicRows.subList(1, 9)).allSatisfy(row -> {
            assertThat(row).hasSize(3).doesNotContain("a-d", "d-e");
            assertThat(row.get(0)).matches("[0-9a-f]{16}");
            assertThat(row.get(2)).isEqualTo("renders/blind-pairs/" + row.get(0) + ".png");
        });
        assertThat(publicRows.subList(1, 9)).extracting(row -> row.get(0)).isSorted().doesNotHaveDuplicates();
        assertThat(publicRows.subList(1, 9)).extracting(row -> row.get(1)).containsOnly(
                "single-n500", "single-n5000", "grid-equal-n500-per-center", "grid-equal-n5000-per-center"
        );
        var ballot = E001Csv.read(Files.readString(staging.resolve("reviewer-package/blind-ballot-template.csv")));
        assertThat(ballot).hasSize(9);
        assertThat(ballot.subList(1, 9)).allSatisfy(row ->
                assertThat(row).containsExactly("", row.get(1), "", "", "", "valid", "")
        );

        Set<String> adDScenarios = scenariosWith(assignment, "a-d", "D");
        Set<String> deDScenarios = scenariosWith(assignment, "d-e", "D");
        assertThat(adDScenarios).allMatch(scenario -> scenario.startsWith("review-ad-"))
                .doesNotContainAnyElementsOf(deDScenarios);
        assertThat(deDScenarios).allMatch(scenario -> scenario.startsWith("confirmation-"));
        for (var pair : assignment.pairs()) {
            BufferedImage image = ImageIO.read(
                    staging.resolve("reviewer-package/renders/blind-pairs/" + pair.id() + ".png").toFile()
            );
            assertThat(image.getWidth()).isEqualTo(2432);
            assertThat(image.getHeight()).isEqualTo(1200);
            assertThat(image.getRGB(0, 0) & 0xffffff).isEqualTo(color(pair.familyId(), pair.leftModelId()));
            assertThat(image.getRGB(1232, 0) & 0xffffff).isEqualTo(color(pair.familyId(), pair.rightModelId()));
            assertThat(image.getRGB(1200, 0) & 0xffffff).isZero();
            assertThat(image.getRGB(1231, 0) & 0xffffff).isZero();
        }
    }

    @Test
    void DE_assignment는_E가_없거나_D와_sigma가_다르면_쓰기_전에_거부한다() throws IOException {
        // given
        var assignment = assignment(true);
        Path root = Files.createDirectory(directory.resolve("source-invalid"));
        Path missingStaging = Files.createDirectory(directory.resolve("missing-e"));
        Path mismatchStaging = Files.createDirectory(directory.resolve("mismatch-e"));
        var missing = E001RunContext.Source.of("fixture", E001RunnerFixture.D, null);
        var mismatch = E001RunContext.Source.of("fixture", E001RunnerFixture.D,
                E001Parameters.of(100, E001Noise.GRADIENT, E001FieldProfile.P1, 0.4, "sir16"));

        // when & then
        assertThatThrownBy(() -> E001Blind.write(missingStaging, root, missing, assignment))
                .isInstanceOf(IOException.class).hasMessageContaining("선택된 E");
        assertThatThrownBy(() -> E001Blind.write(mismatchStaging, root, mismatch, assignment))
                .isInstanceOf(IOException.class).hasMessageContaining("같은 sigma");
        assertThat(missingStaging).isEmptyDirectory();
        assertThat(mismatchStaging).isEmptyDirectory();
    }

    @Test
    void 잘못된_크기의_원본_panel은_PNG로_합성하지_않는다() throws IOException {
        // given
        var assignment = assignment(false);
        var source = E001RunContext.Source.of("fixture", E001RunnerFixture.D, null);
        Path root = sourcePanels(assignment, source);
        var pair = assignment.pairs().getFirst();
        Path path = panelPath(root, pair.scenarioId(), parameters(source, pair.leftModelId()));
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", path.toFile());
        Path staging = Files.createDirectory(directory.resolve("invalid-png"));

        // when & then
        assertThatThrownBy(() -> E001Blind.write(staging, root, source, assignment))
                .isInstanceOf(IOException.class).hasMessageContaining("1200×1200 PNG");
    }

    @Test
    void AD와_DE가_모두_통과해야_E를_추천한다() {
        // given
        var assignment = assignment(true);
        var passed = favorable(assignment);
        var failedDe = failShapeAndNatural(favorable(assignment), assignment, "d-e");
        var failedAd = failShapeAndNatural(favorable(assignment), assignment, "a-d");

        // when
        var allPassed = E001Blind.evaluate(assignment, passed);
        var onlyDPassed = E001Blind.evaluate(assignment, failedDe);
        var onlyEPassed = E001Blind.evaluate(assignment, failedAd);

        // then
        assertThat(allPassed).isEqualTo(E001Blind.Review.of(true, true));
        assertThat(allPassed.recommendation()).isEqualTo("recommend-e");
        assertThat(onlyDPassed).isEqualTo(E001Blind.Review.of(true, false));
        assertThat(onlyDPassed.recommendation()).isEqualTo("recommend-d");
        assertThat(onlyEPassed).isEqualTo(E001Blind.Review.of(false, true));
        assertThat(onlyEPassed.recommendation()).isEqualTo("inconclusive");
    }

    @Test
    void shape와_natural은_각각_세화면에서_네표_이상이어야_통과한다() {
        // given
        var assignment = assignment(false);
        var exactlyFour = weakenChoice(favorable(assignment), assignment, "a-d", 2, 1, 4);
        exactlyFour = weakenChoice(exactlyFour, assignment, "a-d", 3, 1, 4);
        var shapeFailure = weakenChoice(favorable(assignment), assignment, "a-d", 2, 2, 2);
        var naturalFailure = weakenChoice(favorable(assignment), assignment, "a-d", 3, 2, 2);

        // when & then
        assertThat(E001Blind.evaluate(assignment, exactlyFour).dPassed()).isTrue();
        assertThat(E001Blind.evaluate(assignment, shapeFailure).dPassed()).isFalse();
        assertThat(E001Blind.evaluate(assignment, naturalFailure).dPassed()).isFalse();
    }

    @Test
    void AD만_평가하면_D_통과_여부만으로_추천한다() {
        // given
        var assignment = assignment(false);

        // when
        var passed = E001Blind.evaluate(assignment, favorable(assignment));
        var failed = E001Blind.evaluate(
                assignment,
                failShapeAndNatural(favorable(assignment), assignment, "a-d")
        );

        // then
        assertThat(passed.ePassed()).isNull();
        assertThat(passed.recommendation()).isEqualTo("recommend-d");
        assertThat(failed.ePassed()).isNull();
        assertThat(failed.recommendation()).isEqualTo("inconclusive");
    }

    @Test
    void challenge_hotspot이_한_화면에서_두표면_family가_실패한다() {
        // given
        var assignment = assignment(true);
        var rows = favorable(assignment);
        String pairId = assignment.pairs().stream()
                .filter(pair -> pair.familyId().equals("d-e"))
                .findFirst()
                .orElseThrow()
                .id();
        int changed = 0;
        for (int index = 0; index < rows.size() && changed < 2; index++) {
            if (rows.get(index).get(1).equals(pairId)) {
                rows.set(index, ballot(rows.get(index), rows.get(index).get(2), rows.get(index).get(3), "both"));
                changed++;
            }
        }

        // when
        var review = E001Blind.evaluate(assignment, rows);

        // then
        assertThat(review).isEqualTo(E001Blind.Review.of(true, false));
        assertThat(review.recommendation()).isEqualTo("recommend-d");
    }

    @Test
    void 전체_20개나_40개와_동일한_다섯_평가자가_아니면_어느_family도_판정하지_않는다() {
        // given
        var assignment = assignment(true);
        var incomplete = favorable(assignment);
        incomplete.removeLast();
        var differentReviewers = favorable(assignment);
        var replaced = new ArrayList<>(differentReviewers.getLast());
        replaced.set(0, "reviewer-extra");
        differentReviewers.set(differentReviewers.size() - 1, List.copyOf(replaced));

        // when & then
        assertThatThrownBy(() -> E001Blind.evaluate(assignment, incomplete))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("모든 화면");
        assertThatThrownBy(() -> E001Blind.evaluate(assignment, differentReviewers))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("다섯 평가자");
    }

    @Test
    void 중복되거나_허용값_밖인_응답은_pending_review를_위해_예외로_남긴다() {
        // given
        var assignment = assignment(false);
        var duplicate = favorable(assignment);
        duplicate.set(duplicate.size() - 1, duplicate.getFirst());
        var malformed = favorable(assignment);
        var row = new ArrayList<>(malformed.getFirst());
        row.set(2, "invalid");
        malformed.set(0, List.copyOf(row));

        // when & then
        assertThatThrownBy(() -> E001Blind.evaluate(assignment, duplicate))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("중복");
        assertThatThrownBy(() -> E001Blind.evaluate(assignment, malformed))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("무효 응답");
        for (int column : List.of(2, 3, 4)) {
            var nullChoice = favorable(assignment);
            var nullRow = new ArrayList<>(nullChoice.getFirst());
            nullRow.set(column, null);
            nullChoice.set(0, nullRow);
            assertThatThrownBy(() -> E001Blind.evaluate(assignment, nullChoice))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("무효 응답");
        }
    }

    @Test
    void nonce에서_도출되지_않은_family나_모델_배치는_assignment가_거부한다() {
        // given
        var assignment = assignment(true);
        var pair = assignment.pairs().getFirst();
        var wrongFamily = new ArrayList<>(assignment.pairs());
        wrongFamily.set(0, E001Blind.Pair.of(
                pair.id(),
                "a-d",
                pair.scenarioId(),
                pair.conditionId(),
                pair.leftModelId(),
                pair.rightModelId()
        ));
        var wrongModels = new ArrayList<>(assignment.pairs());
        wrongModels.set(0, E001Blind.Pair.of(
                pair.id(),
                pair.familyId(),
                pair.scenarioId(),
                pair.conditionId(),
                "D",
                "D"
        ));

        // when & then
        assertThatThrownBy(() -> E001Blind.Assignment.of(assignment.nonceHex(), wrongFamily))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> E001Blind.Assignment.of(assignment.nonceHex(), wrongModels))
                .isInstanceOf(IllegalArgumentException.class);
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

    private static E001Blind.Assignment assignment(boolean includeE) {
        return E001Blind.assign(includeE, () -> HexFormat.of().parseHex(NONCE));
    }

    private Path sourcePanels(E001Blind.Assignment assignment, E001RunContext.Source source) throws IOException {
        Path root = Files.createDirectory(directory.resolve("source"));
        Path panels = Files.createDirectories(root.resolve("coordinator-only/model-panels"));
        for (var pair : assignment.pairs()) {
            for (String model : Set.of(pair.leftModelId(), pair.rightModelId())) {
                E001Parameters parameter = parameters(source, model);
                Path path = panels.resolve(parameter.parameterSetId() + "--" + pair.scenarioId() + ".png");
                BufferedImage image = new BufferedImage(E001Panel.SIZE, E001Panel.SIZE, BufferedImage.TYPE_INT_RGB);
                image.setRGB(0, 0, color(pair.familyId(), model));
                ImageIO.write(image, "png", path.toFile());
            }
        }
        return root;
    }

    private static Path panelPath(Path root, String scenario, E001Parameters parameters) {
        return root.resolve("coordinator-only/model-panels/" + parameters.parameterSetId() + "--" + scenario + ".png");
    }

    private static E001Parameters parameters(E001RunContext.Source source, String model) {
        return switch (model) {
            case "A" -> A;
            case "D" -> source.d();
            case "E" -> source.e();
            default -> throw new IllegalArgumentException("알 수 없는 fixture 모델이에요.");
        };
    }

    private static int color(String family, String model) {
        return switch (family + ":" + model) {
            case "a-d:A" -> 0xffaa00;
            case "a-d:D" -> 0xff0000;
            case "d-e:D" -> 0x00ff00;
            case "d-e:E" -> 0x0000ff;
            default -> throw new IllegalArgumentException("알 수 없는 fixture family·모델이에요.");
        };
    }

    private static String description(E001Blind.Pair pair) {
        return String.join(":", pair.id(), pair.familyId(), pair.conditionId(), pair.leftModelId(),
                pair.rightModelId(), pair.scenarioId());
    }

    private static long challengeLeftCount(E001Blind.Assignment assignment, String family, String challenge) {
        return assignment.pairs().stream().filter(pair -> pair.familyId().equals(family))
                .filter(pair -> pair.leftModelId().equals(challenge)).count();
    }

    private static Set<String> scenariosWith(E001Blind.Assignment assignment, String family, String model) {
        return assignment.pairs().stream().filter(pair -> pair.familyId().equals(family))
                .filter(pair -> pair.leftModelId().equals(model) || pair.rightModelId().equals(model))
                .map(E001Blind.Pair::scenarioId).collect(java.util.stream.Collectors.toSet());
    }

    private static List<List<String>> favorable(E001Blind.Assignment assignment) {
        List<List<String>> rows = new ArrayList<>();
        for (var pair : assignment.pairs()) {
            String challenge = pair.familyId().equals("a-d") ? "D" : "E";
            String side = pair.leftModelId().equals(challenge) ? "left" : "right";
            for (int reviewer = 0; reviewer < 5; reviewer++) {
                rows.add(List.of("reviewer-" + reviewer, pair.id(), side, side, "neither", "valid", ""));
            }
        }
        return rows;
    }

    private static List<List<String>> failShapeAndNatural(
            List<List<String>> rows,
            E001Blind.Assignment assignment,
            String family
    ) {
        weakenChoice(rows, assignment, family, 2, 2, 2);
        return weakenChoice(rows, assignment, family, 3, 2, 2);
    }

    private static List<List<String>> weakenChoice(
            List<List<String>> rows,
            E001Blind.Assignment assignment,
            String family,
            int column,
            int reviewers,
            int pairs
    ) {
        List<String> pairIds = assignment.pairs().stream().filter(pair -> pair.familyId().equals(family))
                .limit(pairs).map(E001Blind.Pair::id).toList();
        for (int index = 0; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            int reviewer = Integer.parseInt(row.get(0).substring("reviewer-".length()));
            if (pairIds.contains(row.get(1)) && reviewer < reviewers) {
                var changed = new ArrayList<>(row);
                changed.set(column, "tie");
                rows.set(index, List.copyOf(changed));
            }
        }
        return rows;
    }

    private static List<String> ballot(List<String> row, String shape, String natural, String hotspot) {
        return List.of(row.get(0), row.get(1), shape, natural, hotspot, "valid", "");
    }
}
