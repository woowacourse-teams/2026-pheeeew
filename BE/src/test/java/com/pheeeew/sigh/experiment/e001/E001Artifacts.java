package com.pheeeew.sigh.experiment.e001;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;

final class E001Artifacts {

    private static final String VERSION = "E001-v2";
    private static final Comparator<E001Trial> TRIAL_ORDER = Comparator.comparing((E001Trial trial) -> trial.batch().plan().scenario().phase())
            .thenComparing(trial -> trial.batch().parameters().modelId())
            .thenComparing(trial -> trial.batch().parameters().parameterSetId())
            .thenComparing(trial -> trial.batch().plan().scenario().id());

    private E001Artifacts() {
    }

    static void write(Path root, E001Distribution.Result result, Metadata metadata, List<E001Conformance.Vector> vectors) throws IOException {
        result.decision().distributionOutcome();
        if (!Files.isDirectory(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("비어 있는 staging 디렉터리가 필요해요.");
        }
        try (var files = Files.list(root)) {
            if (files.findAny().isPresent()) {
                throw new IOException("기존 staging 파일을 덮어쓰지 않아요.");
            }
        }
        List<E001Trial> trials = result.trials().stream().sorted(TRIAL_ORDER).toList();
        Map<String, Long> rows = new TreeMap<>();
        rows.put("coordinates.csv.gz", coordinates(root, trials));
        rows.put("metrics.csv", metrics(root, trials, result.intensities()));
        rows.put("boundary-observations.csv", boundaries(root, trials));
        rows.put("sampler-failures.csv", failures(root, trials));
        rows.put("conformance.csv", conformance(root, result, vectors));
        List<String> panels = panels(root, result, trials);
        Map<String, Object> manifest = new TreeMap<>();
        manifest.put("protocolVersion", VERSION);
        manifest.put("schemaVersion", 2);
        manifest.put("baseProtocolBlobId", E001RunContext.BASE_PROTOCOL_BLOB_ID);
        manifest.put("numberFormat", "Double.toString;negative-zero-normalized;raw-bits-preserved");
        manifest.put("protocolSha", metadata.protocolSha());
        manifest.put("runnerSha", metadata.runnerSha());
        manifest.put("distributionOutcome", result.decision().distributionOutcome());
        manifest.put("outcomeReason", result.decision().reason());
        manifest.put("selectedD", result.decision().d() == null ? null : result.decision().d().parameterSetId());
        manifest.put("selectedE", result.decision().e() == null ? null : result.decision().e().parameterSetId());
        manifest.put("sampleSeeds", E001Evaluation.SAMPLE_SEEDS.stream().sorted().toList());
        manifest.put("parameters", result.intensities().keySet().stream().sorted(Comparator.comparing(E001Parameters::parameterSetId))
                .map(E001Artifacts::parameterValues).toList());
        manifest.put("scenarios", trials.stream().map(E001Artifacts::trialValues).toList());
        manifest.put("requestedCount", result.requestedCount());
        manifest.put("generatedCount", result.generatedCount());
        manifest.put("samplerFailureCount", result.failureCount());
        manifest.put("rows", rows);
        manifest.put("panels", panels);
        manifest.put("checksumFileCount", E001Checksums.REQUIRED.size() + panels.size());
        E001ArtifactFormat.write(root.resolve("manifest.json"), E001ArtifactFormat.json(manifest));
        E001ArtifactFormat.write(root.resolve("environment.json"), E001ArtifactFormat.json(metadata.environment()));
        E001Checksums.write(root);
    }

    private static long coordinates(Path root, List<E001Trial> trials) throws IOException {
        long count = 0;
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(
                Files.newOutputStream(root.resolve("coordinates.csv.gz"), StandardOpenOption.CREATE_NEW)), UTF_8))) {
            writer.write("protocol_version,phase,model_id,parameter_set_id,scenario_id,requested_scenario_count,requested_center_count,sample_seed,center_id,point_index,center_easting_m,center_northing_m,offset_easting_m,offset_northing_m,radius_m\n");
            for (E001Trial trial : trials) {
                E001Batch batch = trial.batch();
                Map<String, Long> centerCounts = new HashMap<>();
                batch.plan().centers().forEach(center -> centerCounts.put(center.id(), center.perSeedCount() * 5));
                for (E001Sample point : batch.samples().stream().sorted(E001Sample.ORDER).toList()) {
                    List<String> row = new ArrayList<>(identity(batch));
                    row.addAll(List.of(Long.toString(batch.plan().requestedCount()), Long.toString(centerCounts.get(point.centerId())),
                            Long.toString(point.sampleSeed()), point.centerId(), Long.toString(point.pointIndex()),
                            E001ArtifactFormat.decimal(point.centerEasting()), E001ArtifactFormat.decimal(point.centerNorthing()),
                            E001ArtifactFormat.decimal(point.offset().eastingMeters()), E001ArtifactFormat.decimal(point.offset().northingMeters()),
                            E001ArtifactFormat.decimal(point.radius())));
                    writer.write(E001ArtifactFormat.csv(row));
                    count++;
                }
            }
        }
        return count;
    }

    private static long metrics(Path root, List<E001Trial> trials, Map<E001Parameters, Double> intensities) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (E001Trial trial : trials) {
            List<String> prefix = identity(trial.batch());
            trial.evaluation().bySeed().forEach((seed, values) -> values.values().forEach((key, value) ->
                    rows.add(metric(prefix, Long.toString(seed), key, "", value))));
            trial.evaluation().bySeed().values().stream().flatMap(values -> values.values().keySet().stream()).distinct().forEach(key -> {
                rows.add(metric(prefix, "", key, "median", trial.evaluation().median(key)));
                rows.add(metric(prefix, "", key, "min", trial.evaluation().minimum(key)));
                rows.add(metric(prefix, "", key, "max", trial.evaluation().maximum(key)));
            });
            trial.evaluation().pooled().values().forEach((key, value) -> rows.add(metric(prefix, "", key, "", value)));
        }
        intensities.forEach((parameters, value) -> {
            if (parameters.modelId().equals("E")) {
                rows.add(metric(List.of(VERSION, "calibration", "E", parameters.parameterSetId(), "field-calibration-cal-41x41"),
                        "", "logIntensityStd", "", value));
            }
        });
        rows.sort(Comparator.comparing(row -> String.join("\u0000", row.subList(0, 8))));
        writeRows(root.resolve("metrics.csv"), "protocol_version,phase,model_id,parameter_set_id,scenario_id,sample_seed,metric_id,statistic,value,unit,status\n", rows);
        return rows.size();
    }

    private static long boundaries(Path root, List<E001Trial> trials) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (E001Trial trial : trials) {
            for (E001BoundaryObservation.Row observation : trial.boundaryObservations()) {
                E001BoundaryObservation value = observation.observation();
                List<String> row = new ArrayList<>(identity(trial.batch()));
                row.addAll(List.of(integer(observation.sampleSeed()), Long.toString(observation.requestedCount()),
                        Long.toString(value.generatedCount()), integer(value.outerCount()), integer(value.innerCount()),
                        E001ArtifactFormat.decimal(value.outerArea()), E001ArtifactFormat.decimal(value.innerArea()),
                        optionalDecimal(value.outerShare()), optionalDecimal(value.innerShare()), optionalDecimal(value.rawDensityRatio()),
                        value.status().id(), value.interpretation()));
                rows.add(row);
            }
        }
        writeRows(root.resolve("boundary-observations.csv"), "protocol_version,phase,model_id,parameter_set_id,scenario_id,sample_seed,requested_count,generated_count,outer_count,inner_count,outer_area_m2,inner_area_m2,outer_share,inner_share,raw_density_ratio,observation_status,interpretation\n", rows);
        return rows.size();
    }

    private static String integer(Long value) {
        return value == null ? "" : Long.toString(value);
    }

    private static String optionalDecimal(Double value) {
        return value == null ? "" : E001ArtifactFormat.decimal(value);
    }

    private static List<String> metric(List<String> prefix, String seed, String key, String aggregate, double value) {
        int separator = key.indexOf('.');
        String id = separator < 0 ? key : key.substring(0, separator);
        String statistic = separator < 0 ? "value" : key.substring(separator + 1);
        if (!aggregate.isEmpty()) {
            statistic = statistic.equals("value") ? aggregate : statistic + "." + aggregate;
        }
        String unit = key.startsWith("radius.") && !key.equals("radius.violations") ? "m"
                : key.equals("neighbors10") || key.equals("samplerFailureCount") || key.equals("radius.violations")
                || key.equals("hotspot50.p99") || key.equals("hotspot50.max") ? "count" : "ratio";
        List<String> cells = new ArrayList<>(prefix);
        cells.addAll(List.of(seed, id, statistic, Double.isFinite(value) ? E001ArtifactFormat.decimal(value) : "",
                unit, Double.isFinite(value) ? "valid" : "undefined"));
        return cells;
    }

    private static long failures(Path root, List<E001Trial> trials) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (E001Trial trial : trials) {
            for (E001Batch.Failure failure : trial.batch().failures().stream()
                    .sorted(Comparator.comparingLong(E001Batch.Failure::sampleSeed).thenComparing(E001Batch.Failure::centerId)
                            .thenComparingLong(E001Batch.Failure::pointIndex)).toList()) {
                List<String> row = new ArrayList<>(identity(trial.batch()));
                E001Parameters parameters = trial.batch().parameters();
                row.addAll(List.of(Long.toString(failure.sampleSeed()), failure.centerId(), Long.toString(failure.pointIndex()),
                        parameters.modelId().equals("E") ? parameters.sampler() : parameters.parameterSetId(), Integer.toString(failure.attemptLimit())));
                rows.add(row);
            }
        }
        writeRows(root.resolve("sampler-failures.csv"), "protocol_version,phase,model_id,parameter_set_id,scenario_id,sample_seed,center_id,point_index,sampler_id,attempt_limit\n", rows);
        return rows.size();
    }

    private static long conformance(Path root, E001Distribution.Result result, List<E001Conformance.Vector> vectors) throws IOException {
        List<E001Parameters> parameters = E001Conformance.selected(result);
        Map<String, E001Parameters> selected = new HashMap<>();
        parameters.forEach(parameter -> selected.put(parameter.parameterSetId(), parameter));
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (E001Conformance.Vector vector : vectors) {
            E001Parameters parameter = selected.get(vector.parameterSetId());
            if (parameter == null || !parameter.modelId().equals(vector.modelId()) || !E001Evaluation.SAMPLE_SEEDS.contains(vector.sampleSeed())
                    || vector.pointIndex() < 0 || vector.pointIndex() >= 64 || !keys.add(vector.parameterSetId() + ":" + vector.sampleSeed() + ":" + vector.pointIndex())
                    || vector.pointSeed() != E001PointSeed.derive("conformance-cal-n320-per-model", "CAL", 0, 0, vector.sampleSeed(), vector.pointIndex())) {
                throw new IOException("conformance 식별자가 선택 모델 계약과 맞지 않아요.");
            }
            double x = Double.longBitsToDouble(vector.eastingBits());
            double y = Double.longBitsToDouble(vector.northingBits());
            if (!Double.isFinite(x) || !Double.isFinite(y) || StrictMath.hypot(x, y) >= 300.0) {
                throw new IOException("conformance 좌표가 유효하지 않아요.");
            }
        }
        if (vectors.size() != parameters.size() * 320) {
            throw new IOException("conformance 개수는 선택 모델마다 320개여야 해요.");
        }
        List<List<String>> rows = vectors.stream().sorted(Comparator.comparing(E001Conformance.Vector::modelId)
                .thenComparing(E001Conformance.Vector::parameterSetId).thenComparingLong(E001Conformance.Vector::sampleSeed)
                .thenComparingLong(E001Conformance.Vector::pointIndex)).map(E001Conformance.Vector::cells).toList();
        writeRows(root.resolve("conformance.csv"), "model_id,parameter_set_id,sample_seed,point_index,point_seed_hex,offset_easting_bits,offset_northing_bits\n", rows);
        return rows.size();
    }

    private static List<String> panels(Path root, E001Distribution.Result result, List<E001Trial> trials) throws IOException {
        if (result.decision().status() == E001Selection.Status.INCONCLUSIVE) {
            return List.of();
        }
        List<String> selected = E001Conformance.selected(result).stream().map(E001Parameters::parameterSetId).toList();
        boolean includeE = result.decision().status() == E001Selection.Status.E_REVIEW_READY;
        Path directory = root.resolve("coordinator-only/model-panels");
        Files.createDirectories(directory);
        List<String> names = new ArrayList<>();
        for (E001Trial trial : trials) {
            E001Scenario scenario = trial.batch().plan().scenario();
            String parameterId = trial.batch().parameters().parameterSetId();
            boolean reviewPanel = scenario.phase().equals("review")
                    && (trial.batch().parameters().modelId().equals("A") || parameterId.equals(result.decision().d().parameterSetId()));
            boolean confirmationPanel = includeE && scenario.phase().equals("confirmation") && selected.contains(parameterId);
            if (reviewPanel || confirmationPanel) {
                String name = trial.batch().parameters().parameterSetId() + "--" + scenario.id() + ".png";
                E001Panel.write(directory.resolve(name), trial.batch().samples(), scenario.originX(), scenario.originY());
                names.add("coordinator-only/model-panels/" + name);
            }
        }
        if (names.size() != (includeE ? 16 : 8)) {
            throw new IOException("블라인드 원본 panel은 A/D 여덟 장과 조건부 D/E 여덟 장이어야 해요.");
        }
        return names.stream().sorted().toList();
    }

    private static List<String> identity(E001Batch batch) {
        return List.of(VERSION, batch.plan().scenario().phase(), batch.parameters().modelId(),
                batch.parameters().parameterSetId(), batch.plan().scenario().id());
    }

    private static Map<String, Object> parameterValues(E001Parameters parameter) {
        Map<String, Object> values = new TreeMap<>();
        values.put("modelId", parameter.modelId());
        values.put("parameterSetId", parameter.parameterSetId());
        values.put("sigmaMeters", parameter.sigma());
        values.put("support", parameter.modelId().equals("A") ? "square[-150,150)" : "radius[0,300)");
        if (parameter.modelId().equals("E")) {
            values.put("noise", parameter.noise().name());
            values.put("profile", parameter.profile().name());
            values.put("beta", parameter.beta());
            values.put("sampler", parameter.sampler());
            values.put("fieldSeedHex", "5048454545455701");
            values.put("lengthMeters", List.of(parameter.profile().length(0), parameter.profile().length(1), parameter.profile().length(2)));
            values.put("weights", List.of(parameter.profile().weight(0), parameter.profile().weight(1), parameter.profile().weight(2)));
            values.put("rotationDegrees", List.of(17, 71, 137));
        }
        return values;
    }

    private static Map<String, Object> trialValues(E001Trial trial) {
        E001Scenario scenario = trial.batch().plan().scenario();
        return Map.of("scenarioId", scenario.id(), "parameterSetId", trial.batch().parameters().parameterSetId(),
                "originId", scenario.originId(), "originEastingMeters", scenario.originX(), "originNorthingMeters", scenario.originY(),
                "requestedCount", trial.batch().plan().requestedCount(), "generatedCount", trial.batch().samples().size(),
                "failureCount", trial.batch().failures().size(), "centers", trial.batch().plan().centers().stream().map(center ->
                        Map.of("centerId", center.id(), "i", center.i(), "j", center.j(), "perSeedCount", center.perSeedCount())).toList());
    }

    private static void writeRows(Path path, String header, List<List<String>> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, UTF_8, StandardOpenOption.CREATE_NEW)) {
            writer.write(header);
            for (List<String> row : rows) {
                writer.write(E001ArtifactFormat.csv(row));
            }
        }
    }

    record Metadata(String protocolSha, String runnerSha, Map<String, Object> environment) {

        Metadata {
            if (!protocolSha.matches("[0-9a-f]{40}") || !runnerSha.matches("[0-9a-f]{40}")) {
                throw new IllegalArgumentException("protocol과 runner의 전체 Git SHA가 필요해요.");
            }
            List<String> allowed = List.of("jdkVendor", "jdkVersion", "osName", "osVersion", "osArch", "processors",
                    "gradleVersion", "locale", "timezone", "protocolSha", "runnerSha", "gitDirty");
            if (!allowed.containsAll(environment.keySet())) {
                throw new IllegalArgumentException("실행 환경은 허용된 정보만 기록해요.");
            }
            Map<String, Object> copy = new HashMap<>(environment);
            copy.put("protocolSha", protocolSha);
            copy.put("runnerSha", runnerSha);
            environment = Map.copyOf(copy);
        }

        static Metadata of(String protocolSha, String runnerSha, Map<String, Object> environment) {
            return new Metadata(protocolSha, runnerSha, environment);
        }
    }
}
