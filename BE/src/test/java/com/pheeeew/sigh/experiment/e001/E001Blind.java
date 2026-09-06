package com.pheeeew.sigh.experiment.e001;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;

final class E001Blind {

    static final String BALLOT_HEADER = "reviewer_id,pair_id,shape_choice,natural_choice,hotspot_choice,status,invalidation_reason\n";

    private static final String FAMILY_AD = "a-d";
    private static final String FAMILY_DE = "d-e";
    private static final List<String> AD_SCENARIOS = List.of(
            E001Scenario.REVIEW_SINGLE_500.id(),
            E001Scenario.REVIEW_SINGLE_5000.id(),
            E001Scenario.REVIEW_GRID_500.id(),
            E001Scenario.REVIEW_GRID_5000.id()
    );
    private static final List<String> DE_SCENARIOS = List.of(
            E001Scenario.CONFIRMATION_SINGLE_500.id(),
            E001Scenario.CONFIRMATION_SINGLE_5000.id(),
            E001Scenario.CONFIRMATION_GRID_500.id(),
            E001Scenario.CONFIRMATION_GRID_5000.id()
    );
    private static final Set<String> SIDE_CHOICES = Set.of("left", "right", "tie");
    private static final Set<String> HOTSPOT_CHOICES = Set.of("left", "right", "both", "neither");
    private static final E001Parameters A = E001Parameters.distance("A", 0);

    private E001Blind() {
    }

    static Assignment assign(boolean includeE) {
        SecureRandom random = new SecureRandom();
        return assign(includeE, () -> {
            byte[] nonce = new byte[16];
            random.nextBytes(nonce);
            return nonce;
        });
    }

    static Assignment assign(boolean includeE, Supplier<byte[]> nonces) {
        Objects.requireNonNull(nonces);
        while (true) {
            byte[] supplied = nonces.get();
            if (supplied == null || supplied.length != 16) {
                throw new IllegalArgumentException("블라인드 nonce는 raw 16 byte여야 해요.");
            }
            byte[] nonce = supplied.clone();
            List<Pair> pairs = createPairs(nonce, includeE);
            if (pairs.stream().map(Pair::id).distinct().count() == pairs.size()) {
                return Assignment.of(HexFormat.of().formatHex(nonce), pairs);
            }
        }
    }

    static void write(Path staging, Path root, E001RunContext.Source source, Assignment assignment) throws IOException {
        validateAssignment(assignment.nonceHex(), assignment.pairs());
        validateSource(source, assignment.includesE());

        Path coordinator = Files.createDirectory(staging.resolve("coordinator-only"));
        Path reviewer = Files.createDirectory(staging.resolve("reviewer-package"));
        Path images = Files.createDirectories(reviewer.resolve("renders/blind-pairs"));
        StringBuilder key = new StringBuilder(
                "nonce_hex,pair_id,family_id,scenario_id,left_model_id,right_model_id\n"
        );
        StringBuilder publicPairs = new StringBuilder("pair_id,scenario_id,image_path\n");
        StringBuilder ballot = new StringBuilder(BALLOT_HEADER);
        for (Pair pair : assignment.pairs()) {
            key.append(E001ArtifactFormat.csv(List.of(assignment.nonceHex(), pair.id(), pair.familyId(),
                    pair.scenarioId(), pair.leftModelId(), pair.rightModelId())));
            publicPairs.append(E001ArtifactFormat.csv(List.of(pair.id(), pair.conditionId(),
                    "renders/blind-pairs/" + pair.id() + ".png")));
            ballot.append(E001ArtifactFormat.csv(List.of("", pair.id(), "", "", "", "valid", "")));

            BufferedImage left = panel(root, pair.scenarioId(), parameters(source, pair.leftModelId()));
            BufferedImage right = panel(root, pair.scenarioId(), parameters(source, pair.rightModelId()));
            BufferedImage combined = new BufferedImage(2 * E001Panel.SIZE + 32, E001Panel.SIZE, BufferedImage.TYPE_INT_RGB);
            copy(left, combined, 0);
            copy(right, combined, E001Panel.SIZE + 32);
            try (var output = Files.newOutputStream(images.resolve(pair.id() + ".png"), StandardOpenOption.CREATE_NEW)) {
                if (!ImageIO.write(combined, "png", output)) {
                    throw new IOException("PNG writer가 없어요.");
                }
            }
        }
        E001ArtifactFormat.write(coordinator.resolve("blind-key.csv"), key.toString());
        E001ArtifactFormat.write(reviewer.resolve("blind-pairs.csv"), publicPairs.toString());
        E001ArtifactFormat.write(reviewer.resolve("blind-ballot-template.csv"), ballot.toString());
    }

    static Review evaluate(Assignment assignment, List<List<String>> responses) {
        validateAssignment(assignment.nonceHex(), assignment.pairs());
        if (responses == null) {
            throw new IllegalArgumentException("블라인드 응답이 필요해요.");
        }

        Map<String, Pair> pairs = assignment.pairs().stream().collect(Collectors.toMap(Pair::id, pair -> pair));
        Set<String> reviewers = new HashSet<>();
        Set<ResponseIdentity> identities = new HashSet<>();
        List<Response> validated = new ArrayList<>();
        for (List<String> row : responses) {
            if (row == null || row.size() != 7 || row.stream().anyMatch(Objects::isNull)
                    || row.get(0).isBlank() || !pairs.containsKey(row.get(1))
                    || !SIDE_CHOICES.contains(row.get(2)) || !SIDE_CHOICES.contains(row.get(3))
                    || !HOTSPOT_CHOICES.contains(row.get(4)) || !row.get(5).equals("valid") || !row.get(6).isEmpty()) {
                throw new IllegalArgumentException("누락·중복·무효 응답을 정정하기 전에는 판정하지 않아요.");
            }
            ResponseIdentity identity = ResponseIdentity.of(row.get(0), row.get(1));
            if (!identities.add(identity)) {
                throw new IllegalArgumentException("누락·중복·무효 응답을 정정하기 전에는 판정하지 않아요.");
            }
            reviewers.add(row.get(0));
            validated.add(Response.of(row));
        }

        int expected = assignment.pairs().size() * 5;
        if (reviewers.size() != 5 || validated.size() != expected || identities.size() != expected
                || reviewers.stream().anyMatch(reviewer -> validated.stream()
                .filter(response -> response.reviewerId().equals(reviewer)).count() != assignment.pairs().size())) {
            throw new IllegalArgumentException("같은 다섯 평가자의 모든 화면 응답이 필요해요.");
        }

        Map<String, int[]> votes = new HashMap<>();
        pairs.keySet().forEach(id -> votes.put(id, new int[3]));
        for (Response response : validated) {
            Pair pair = pairs.get(response.pairId());
            String challengeSide = pair.leftModelId().equals(challenge(pair.familyId())) ? "left" : "right";
            int[] count = votes.get(pair.id());
            count[0] += response.shapeChoice().equals(challengeSide) ? 1 : 0;
            count[1] += response.naturalChoice().equals(challengeSide) ? 1 : 0;
            count[2] += response.hotspotChoice().equals(challengeSide) || response.hotspotChoice().equals("both") ? 1 : 0;
        }

        boolean dPassed = familyPassed(FAMILY_AD, assignment.pairs(), votes);
        Boolean ePassed = assignment.includesE() ? familyPassed(FAMILY_DE, assignment.pairs(), votes) : null;
        return Review.of(dPassed, ePassed);
    }

    private static List<Pair> createPairs(byte[] nonce, boolean includeE) {
        List<Pair> pairs = new ArrayList<>(familyPairs(nonce, FAMILY_AD, AD_SCENARIOS));
        if (includeE) {
            pairs.addAll(familyPairs(nonce, FAMILY_DE, DE_SCENARIOS));
        }
        return pairs.stream().sorted(Comparator.comparing(Pair::id)).toList();
    }

    private static List<Pair> familyPairs(byte[] nonce, String familyId, List<String> scenarios) {
        List<String> ordered = scenarios.stream().sorted((left, right) -> {
            int comparison = Arrays.compareUnsigned(
                    hmac(nonce, "assignment|" + familyId + "|" + left),
                    hmac(nonce, "assignment|" + familyId + "|" + right)
            );
            return comparison == 0 ? Arrays.compareUnsigned(left.getBytes(UTF_8), right.getBytes(UTF_8)) : comparison;
        }).toList();

        List<Pair> pairs = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            String scenarioId = ordered.get(index);
            String id = HexFormat.of().formatHex(hmac(nonce, "pair-id|" + familyId + "|" + scenarioId), 0, 8);
            boolean challengeOnLeft = index < 2;
            String left = challengeOnLeft ? challenge(familyId) : baseline(familyId);
            String right = challengeOnLeft ? baseline(familyId) : challenge(familyId);
            pairs.add(Pair.of(id, familyId, scenarioId, conditionId(scenarioId), left, right));
        }
        return List.copyOf(pairs);
    }

    private static void validateAssignment(String nonceHex, List<Pair> pairs) {
        if (nonceHex == null || !nonceHex.matches("[0-9a-f]{32}") || pairs == null || pairs.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("블라인드 assignment 형식이 유효하지 않아요.");
        }

        Set<String> families = pairs.stream().map(Pair::familyId).collect(Collectors.toSet());
        boolean includeE;
        if (families.equals(Set.of(FAMILY_AD))) {
            includeE = false;
        } else if (families.equals(Set.of(FAMILY_AD, FAMILY_DE))) {
            includeE = true;
        } else {
            throw new IllegalArgumentException("블라인드 assignment family 구성이 유효하지 않아요.");
        }

        List<Pair> expected = createPairs(HexFormat.of().parseHex(nonceHex), includeE);
        if (expected.stream().map(Pair::id).distinct().count() != expected.size() || !expected.equals(pairs)) {
            throw new IllegalArgumentException("블라인드 assignment의 pair·scenario·모델 배치가 유효하지 않아요.");
        }
    }

    private static void validateSource(E001RunContext.Source source, boolean includeE) throws IOException {
        if (source == null || source.d() == null || !source.d().modelId().equals("D")) {
            throw new IOException("블라인드에는 선택된 D가 필요해요.");
        }
        if (includeE && (source.e() == null || !source.e().modelId().equals("E") || source.d().sigma() != source.e().sigma())) {
            throw new IOException("D/E 블라인드에는 같은 sigma의 선택된 E가 필요해요.");
        }
    }

    private static boolean familyPassed(String familyId, List<Pair> pairs, Map<String, int[]> votes) {
        List<int[]> familyVotes = pairs.stream().filter(pair -> pair.familyId().equals(familyId))
                .map(pair -> votes.get(pair.id())).toList();
        return familyVotes.size() == 4
                && familyVotes.stream().filter(count -> count[0] >= 4).count() >= 3
                && familyVotes.stream().filter(count -> count[1] >= 4).count() >= 3
                && familyVotes.stream().noneMatch(count -> count[2] >= 2);
    }

    private static String challenge(String familyId) {
        return switch (familyId) {
            case FAMILY_AD -> "D";
            case FAMILY_DE -> "E";
            default -> throw new IllegalArgumentException("알 수 없는 블라인드 family예요.");
        };
    }

    private static String baseline(String familyId) {
        return switch (familyId) {
            case FAMILY_AD -> "A";
            case FAMILY_DE -> "D";
            default -> throw new IllegalArgumentException("알 수 없는 블라인드 family예요.");
        };
    }

    private static String conditionId(String scenarioId) {
        return switch (scenarioId) {
            case "review-ad-single-n500", "confirmation-single-n500" -> "single-n500";
            case "review-ad-single-n5000", "confirmation-single-n5000" -> "single-n5000";
            case "review-ad-grid-equal-n500-per-center", "confirmation-grid-equal-n500-per-center" ->
                    "grid-equal-n500-per-center";
            case "review-ad-grid-equal-n5000-per-center", "confirmation-grid-equal-n5000-per-center" ->
                    "grid-equal-n5000-per-center";
            default -> throw new IllegalArgumentException("알 수 없는 블라인드 scenario예요.");
        };
    }

    private static byte[] hmac(byte[] nonce, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(nonce, "HmacSHA256"));
            return mac.doFinal(message.getBytes(UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256을 사용할 수 없어요.", exception);
        }
    }

    private static E001Parameters parameters(E001RunContext.Source source, String modelId) throws IOException {
        return switch (modelId) {
            case "A" -> A;
            case "D" -> source.d();
            case "E" -> {
                if (source.e() == null) {
                    throw new IOException("D/E 블라인드 원본 E가 없어요.");
                }
                yield source.e();
            }
            default -> throw new IOException("블라인드 모델 ID가 유효하지 않아요.");
        };
    }

    private static BufferedImage panel(Path root, String scenario, E001Parameters parameters) throws IOException {
        Path path = root.resolve("coordinator-only/model-panels/" + parameters.parameterSetId() + "--" + scenario + ".png");
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null || image.getWidth() != E001Panel.SIZE || image.getHeight() != E001Panel.SIZE) {
            throw new IOException("블라인드 원본은 1200×1200 PNG여야 해요.");
        }
        return image;
    }

    private static void copy(BufferedImage source, BufferedImage target, int offset) {
        int size = E001Panel.SIZE;
        target.setRGB(offset, 0, size, size, source.getRGB(0, 0, size, size, null, 0, size), 0, size);
    }

    record Pair(String id, String familyId, String scenarioId, String conditionId,
                String leftModelId, String rightModelId) {

        static Pair of(String id, String familyId, String scenarioId, String conditionId,
                       String leftModelId, String rightModelId) {
            return new Pair(id, familyId, scenarioId, conditionId, leftModelId, rightModelId);
        }
    }

    record Assignment(String nonceHex, List<Pair> pairs) {

        Assignment {
            if (pairs == null || pairs.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("블라인드 assignment pair가 필요해요.");
            }
            pairs = List.copyOf(pairs);
            validateAssignment(nonceHex, pairs);
        }

        static Assignment of(String nonceHex, List<Pair> pairs) {
            return new Assignment(nonceHex, pairs);
        }

        boolean includesE() {
            return pairs.stream().anyMatch(pair -> pair.familyId().equals(FAMILY_DE));
        }
    }

    record Review(boolean dPassed, Boolean ePassed) {

        static Review of(boolean dPassed, Boolean ePassed) {
            return new Review(dPassed, ePassed);
        }

        String recommendation() {
            if (!dPassed) {
                return "inconclusive";
            }
            return Boolean.TRUE.equals(ePassed) ? "recommend-e" : "recommend-d";
        }
    }

    private record ResponseIdentity(String reviewerId, String pairId) {

        private static ResponseIdentity of(String reviewerId, String pairId) {
            return new ResponseIdentity(reviewerId, pairId);
        }
    }

    private record Response(String reviewerId, String pairId, String shapeChoice,
                            String naturalChoice, String hotspotChoice) {

        private static Response of(List<String> row) {
            return new Response(row.get(0), row.get(1), row.get(2), row.get(3), row.get(4));
        }
    }
}
