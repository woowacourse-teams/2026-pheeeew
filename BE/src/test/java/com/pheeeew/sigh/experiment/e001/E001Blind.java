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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;

final class E001Blind {

    static final String BALLOT_HEADER = "reviewer_id,pair_id,shape_choice,natural_choice,hotspot_choice,status,invalidation_reason\n";
    static final List<String> SCENARIOS = Arrays.stream(E001Scenario.values()).filter(value -> value.phase().equals("confirmation"))
            .map(E001Scenario::id).sorted().toList();

    private E001Blind() {
    }

    static Assignment assign() {
        SecureRandom random = new SecureRandom();
        return assign(() -> {
            byte[] nonce = new byte[16];
            random.nextBytes(nonce);
            return nonce;
        });
    }

    static Assignment assign(Supplier<byte[]> nonces) {
        while (true) {
            byte[] nonce = nonces.get().clone();
            if (nonce.length != 16) {
                throw new IllegalArgumentException("블라인드 nonce는 raw 16 byte여야 해요.");
            }
            List<String> ordered = SCENARIOS.stream().sorted((left, right) -> {
                int comparison = Arrays.compareUnsigned(hmac(nonce, "assignment|" + left), hmac(nonce, "assignment|" + right));
                return comparison == 0 ? Arrays.compareUnsigned(left.getBytes(UTF_8), right.getBytes(UTF_8)) : comparison;
            }).toList();
            List<Pair> pairs = new ArrayList<>();
            for (int index = 0; index < ordered.size(); index++) {
                String scenario = ordered.get(index);
                String id = HexFormat.of().formatHex(hmac(nonce, "pair-id|" + scenario), 0, 8);
                pairs.add(Pair.of(id, scenario, index < 2 ? "E" : "D", index < 2 ? "D" : "E"));
            }
            if (pairs.stream().map(Pair::id).distinct().count() == 4) {
                return Assignment.of(HexFormat.of().formatHex(nonce), pairs.stream().sorted(Comparator.comparing(Pair::id)).toList());
            }
        }
    }

    static void write(Path staging, Path root, E001RunContext.Source source, Assignment assignment) throws IOException {
        Path coordinator = Files.createDirectory(staging.resolve("coordinator-only"));
        Path reviewer = Files.createDirectory(staging.resolve("reviewer-package"));
        Path images = Files.createDirectories(reviewer.resolve("renders/blind-pairs"));
        StringBuilder key = new StringBuilder("nonce_hex,pair_id,scenario_id,left_model_id,right_model_id\n");
        StringBuilder publicPairs = new StringBuilder("pair_id,scenario_id,image_path\n");
        StringBuilder ballot = new StringBuilder(BALLOT_HEADER);
        for (Pair pair : assignment.pairs()) {
            key.append(E001ArtifactFormat.csv(List.of(assignment.nonceHex(), pair.id(), pair.scenario(), pair.left(), pair.right())));
            publicPairs.append(E001ArtifactFormat.csv(List.of(pair.id(), pair.scenario(), "renders/blind-pairs/" + pair.id() + ".png")));
            ballot.append(E001ArtifactFormat.csv(List.of("", pair.id(), "", "", "", "valid", "")));
            BufferedImage left = panel(root, pair.scenario(), pair.left().equals("D") ? source.d() : source.e());
            BufferedImage right = panel(root, pair.scenario(), pair.right().equals("D") ? source.d() : source.e());
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

    static boolean evaluate(Assignment assignment, List<List<String>> responses) {
        Map<String, Pair> pairs = assignment.pairs().stream().collect(Collectors.toMap(Pair::id, pair -> pair));
        Set<String> reviewers = new HashSet<>();
        Set<List<String>> identities = new HashSet<>();
        Map<String, int[]> votes = pairs.keySet().stream().collect(Collectors.toMap(id -> id, id -> new int[3]));
        for (List<String> row : responses) {
            if (row.size() != 7 || row.get(0).isBlank() || !pairs.containsKey(row.get(1))
                    || !Set.of("left", "right", "tie").contains(row.get(2)) || !Set.of("left", "right", "tie").contains(row.get(3))
                    || !Set.of("left", "right", "both", "neither").contains(row.get(4))
                    || !row.get(5).equals("valid") || !row.get(6).isEmpty() || !identities.add(row.subList(0, 2))) {
                throw new IllegalArgumentException("누락·중복·무효 응답을 정정하기 전에는 판정하지 않아요.");
            }
            reviewers.add(row.get(0));
            String eSide = pairs.get(row.get(1)).left().equals("E") ? "left" : "right";
            int[] count = votes.get(row.get(1));
            count[0] += row.get(2).equals(eSide) ? 1 : 0;
            count[1] += row.get(3).equals(eSide) ? 1 : 0;
            count[2] += row.get(4).equals(eSide) || row.get(4).equals("both") ? 1 : 0;
        }
        if (pairs.size() != 4 || reviewers.size() != 5 || identities.size() != 20) {
            throw new IllegalArgumentException("다섯 평가자의 네 화면 응답이 모두 필요해요.");
        }
        return votes.values().stream().filter(count -> count[0] >= 4).count() >= 3
                && votes.values().stream().filter(count -> count[1] >= 4).count() >= 3
                && votes.values().stream().noneMatch(count -> count[2] >= 2);
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

    record Pair(String id, String scenario, String left, String right) {

        static Pair of(String id, String scenario, String left, String right) {
            return new Pair(id, scenario, left, right);
        }
    }

    record Assignment(String nonceHex, List<Pair> pairs) {

        Assignment {
            pairs = List.copyOf(pairs);
        }

        static Assignment of(String nonceHex, List<Pair> pairs) {
            return new Assignment(nonceHex, pairs);
        }
    }
}
