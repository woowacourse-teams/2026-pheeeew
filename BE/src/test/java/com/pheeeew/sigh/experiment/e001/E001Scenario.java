package com.pheeeew.sigh.experiment.e001;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

enum E001Scenario {
    TUNING_SINGLE("tuning-single-n500", "tuning", "CAL", 971_850.0, 1_969_950.0, 0, 500, false),
    TUNING_GRID("tuning-grid-imbalanced-n4500", "tuning", "CAL", 971_850.0, 1_969_950.0, 1, 4_500, true),
    CONFIRMATION_SINGLE_500("confirmation-single-n500", "confirmation", "HOLDOUT", 980_850.0, 1_969_950.0, 0, 500, false),
    CONFIRMATION_SINGLE_5000("confirmation-single-n5000", "confirmation", "HOLDOUT", 980_850.0, 1_969_950.0, 0, 5_000, false),
    CONFIRMATION_GRID_500("confirmation-grid-equal-n500-per-center", "confirmation", "HOLDOUT", 980_850.0, 1_969_950.0, 1, 4_500, false),
    CONFIRMATION_GRID_5000("confirmation-grid-equal-n5000-per-center", "confirmation", "HOLDOUT", 980_850.0, 1_969_950.0, 1, 45_000, false),
    SPECTRAL_EQUAL("spectral-grid-equal-n500-per-center", "spectral", "SPECTRAL", 971_850.0, 1_978_950.0, 5, 60_500, false),
    SPECTRAL_IMBALANCED("spectral-grid-imbalanced-n60500", "spectral", "SPECTRAL", 971_850.0, 1_978_950.0, 5, 60_500, true),
    REVIEW_SINGLE_500("review-ad-single-n500", "review", "REVIEW_AD", 989_850.0, 1_969_950.0, 0, 500, false),
    REVIEW_SINGLE_5000("review-ad-single-n5000", "review", "REVIEW_AD", 989_850.0, 1_969_950.0, 0, 5_000, false),
    REVIEW_GRID_500("review-ad-grid-equal-n500-per-center", "review", "REVIEW_AD", 989_850.0, 1_969_950.0, 1, 4_500, false),
    REVIEW_GRID_5000("review-ad-grid-equal-n5000-per-center", "review", "REVIEW_AD", 989_850.0, 1_969_950.0, 1, 45_000, false);

    private final String id;
    private final String phase;
    private final String originId;
    private final double originX;
    private final double originY;
    private final int gridRadius;
    private final long total;
    private final boolean imbalanced;

    E001Scenario(String id, String phase, String originId, double originX, double originY,
                 int gridRadius, long total, boolean imbalanced) {
        this.id = id;
        this.phase = phase;
        this.originId = originId;
        this.originX = originX;
        this.originY = originY;
        this.gridRadius = gridRadius;
        this.total = total;
        this.imbalanced = imbalanced;
    }

    String id() {
        return id;
    }

    String phase() {
        return phase;
    }

    String originId() {
        return originId;
    }

    double originX() {
        return originX;
    }

    double originY() {
        return originY;
    }

    int halfWidth() {
        return gridRadius == 0 ? 300 : gridRadius == 1 ? 600 : 1_350;
    }

    Plan plan() {
        Map<String, Double> weights = new LinkedHashMap<>();
        double[] tuningWeights = {0.55, 0.85, 0.40, 0.75, 2.40, 1.10, 0.30, 0.95, 1.70};
        for (int j = gridRadius; j >= -gridRadius; j--) {
            for (int i = -gridRadius; i <= gridRadius; i++) {
                double weight = !imbalanced ? 1.0 : gridRadius == 1
                        ? tuningWeights[weights.size()] : spectralWeight(i, j);
                weights.put(centerId(i, j), weight);
            }
        }
        boolean allocateBeforeSplitting = this == TUNING_GRID;
        Map<String, Long> counts = E001Allocation.distribute(weights, allocateBeforeSplitting ? total : total / 5);
        List<Center> centers = new ArrayList<>();
        for (int j = gridRadius; j >= -gridRadius; j--) {
            for (int i = -gridRadius; i <= gridRadius; i++) {
                long count = counts.get(centerId(i, j));
                if (allocateBeforeSplitting && count % 5 != 0) {
                    throw new IllegalStateException("튜닝 중심별 개수는 다섯 seed로 균등 분할되어야 해요.");
                }
                centers.add(Center.of(centerId(i, j), i, j, allocateBeforeSplitting ? count / 5 : count));
            }
        }
        return Plan.of(this, centers);
    }

    static double spectralWeight(int i, int j) {
        String input = "E001-v2|spectral-profile|" + i + "|" + j + "|E001300000000001";
        try {
            long bits = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(input.getBytes(UTF_8))).getLong();
            double u = (bits >>> 11) * 0x1.0p-53;
            return 0.25 + (1.75 * u);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없어요.", exception);
        }
    }

    private String centerId(int i, int j) {
        return gridRadius == 0 ? "single" : String.format(Locale.ROOT, "r%02dc%02d", gridRadius - j, i + gridRadius);
    }

    record Center(String id, int i, int j, long perSeedCount) {

        static Center of(String id, int i, int j, long perSeedCount) {
            return new Center(id, i, j, perSeedCount);
        }
    }

    record Plan(E001Scenario scenario, List<Center> centers) {

        Plan {
            centers = centers.stream().sorted(java.util.Comparator.comparing(Center::id)).toList();
            if (centers.isEmpty() || centers.stream().anyMatch(center -> center.perSeedCount() <= 0)
                    || centers.stream().map(Center::id).distinct().count() != centers.size()) {
                throw new IllegalArgumentException("중심 ID는 중복되지 않고 표본 수는 양수여야 해요.");
            }
        }

        static Plan of(E001Scenario scenario, List<Center> centers) {
            return new Plan(scenario, centers);
        }

        long requestedCount() {
            return centers.stream().mapToLong(Center::perSeedCount).sum() * 5;
        }

        Map<E001Integrity.Shard, Long> requested() {
            Map<E001Integrity.Shard, Long> counts = new LinkedHashMap<>();
            for (long seed : E001Evaluation.SAMPLE_SEEDS.stream().sorted().toList()) {
                for (Center center : centers) {
                    counts.put(E001Integrity.Shard.of(seed, center.id()), center.perSeedCount());
                }
            }
            return java.util.Collections.unmodifiableMap(counts);
        }
    }
}
