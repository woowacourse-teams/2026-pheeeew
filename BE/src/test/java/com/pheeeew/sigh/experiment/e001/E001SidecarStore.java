package com.pheeeew.sigh.experiment.e001;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class E001SidecarStore {

    private static final String PROTOCOL_VERSION = "E001-v2";
    private static final int SCHEMA_VERSION = 2;
    private static final int POINTS_PER_BATCH = 100_000;
    private static final int WARMUP_BATCH_COUNT = 5;
    private static final int MEASUREMENT_BATCH_COUNT = 10;
    private static final List<String> PERFORMANCE_HEADER = List.of(
            "model_id", "parameter_set_id", "phase", "batch_index", "points", "elapsed_ns", "ns_per_point"
    );

    private E001SidecarStore() {
    }

    static Path publish(Path parent, String kind, String runId, String reason, E001Artifacts.Metadata metadata, Producer producer) throws IOException {
        return publish(parent, kind, runId, reason, metadata, producer,
                (source, target) -> Files.move(source, target, StandardCopyOption.ATOMIC_MOVE));
    }

    static Path publish(Path parent, String kind, String runId, String reason, E001Artifacts.Metadata metadata,
            Producer producer, E001ArtifactStore.Mover mover) throws IOException {
        if (!List.of("performance", "blind").contains(kind) || !runId.matches("[0-9a-f]{32}")
                || !reason.isEmpty() && !reason.equals(kind.equals("performance") ? "measurement_contaminated" : "blind_exposed")) {
            throw new IllegalArgumentException("sidecar 종류·runId·무효화 이유가 유효하지 않아요.");
        }
        return E001RunLock.execute(parent, () -> lockedPublish(parent, kind, runId, reason, metadata, producer, mover));
    }

    static boolean verifyPerformance(Path root, E001RunContext.Source source) throws IOException {
        verifyDistribution(root, source);
        Path directory = root.resolve("performance");
        var distributionManifest = E001RunContext.read(root.resolve("manifest.json"));
        var manifest = E001RunContext.read(directory.resolve("manifest.json"));
        int expectedRows = source.e() == null ? 15 : 30;
        if (!manifest.path("status").asString().equals("valid")
                || !manifest.path("kind").asString().equals("performance")
                || !manifest.path("protocolVersion").asString().equals(PROTOCOL_VERSION)
                || manifest.path("schemaVersion").asInt() != SCHEMA_VERSION
                || !manifest.path("protocolVersion").equals(distributionManifest.path("protocolVersion"))
                || !manifest.path("schemaVersion").equals(distributionManifest.path("schemaVersion"))
                || !manifest.path("protocolSha").equals(distributionManifest.path("protocolSha"))
                || !manifest.path("runnerSha").equals(distributionManifest.path("runnerSha"))
                || !manifest.path("distributionChecksum").asString().equals(source.checksum())
                || !manifest.path("runId").asString().matches("[0-9a-f]{32}")
                || !manifest.path("invalidationReason").asString().isEmpty()
                || manifest.path("machineRows").size() != 1
                || manifest.path("machineRows").path("performance.csv").asInt() != expectedRows
                || manifest.path("machineFileCount").asInt() != 3) {
            throw new IOException("같은 분포의 유효한 전체 성능 측정이 먼저 필요해요.");
        }
        verifyDataChecksums(directory, manifest);

        List<E001Performance.Batch> batches = readPerformance(directory.resolve("performance.csv"), source, expectedRows);
        E001Performance.Gate dGate = evaluatePerformance(batches, "D");
        if (!dGate.passed()) {
            throw new IOException("D 성능 조건을 통과하지 못해 결과는 inconclusive예요.");
        }
        if (source.e() == null) {
            return false;
        }
        return evaluatePerformance(batches, "E").passed();
    }

    private static void verifyDistribution(Path root, E001RunContext.Source source) throws IOException {
        if (source == null || source.d() == null || !source.d().modelId().equals("D")
                || source.e() != null && (!source.e().modelId().equals("E") || source.d().sigma() != source.e().sigma())) {
            throw new IOException("성능 측정의 D·E 선택 정보를 확인해야 해요.");
        }
        E001Checksums.verify(root);
        if (Files.mismatch(root.resolve("checksums.sha256"), root.resolve("verification-run1-checksums.sha256")) != -1
                || !E001ArtifactFormat.sha256(root.resolve("checksums.sha256")).equals(source.checksum())) {
            throw new IOException("성능 측정과 분포 checksum이 일치하지 않아요.");
        }
        var manifest = E001RunContext.read(root.resolve("manifest.json"));
        if (!manifest.path("protocolVersion").asString().equals(PROTOCOL_VERSION)
                || manifest.path("schemaVersion").asInt() != SCHEMA_VERSION) {
            throw new IOException("E001-v2 schema 2 분포가 필요해요.");
        }
    }

    private static void verifyDataChecksums(Path directory, tools.jackson.databind.JsonNode manifest) throws IOException {
        var expected = manifest.path("dataChecksums");
        Map<String, String> actual = dataChecksums(directory);
        if (!actual.keySet().equals(Set.of("environment.json", "performance.csv"))
                || expected.size() != actual.size()) {
            throw new IOException("성능 파일 개수가 달라요.");
        }
        for (var entry : actual.entrySet()) {
            String expectedChecksum = expected.path(entry.getKey()).asString();
            if (!expectedChecksum.matches("[0-9a-f]{64}") || !entry.getValue().equals(expectedChecksum)) {
                throw new IOException("성능 측정 파일이 변경됐어요.");
            }
        }
    }

    private static List<E001Performance.Batch> readPerformance(
            Path path,
            E001RunContext.Source source,
            int expectedRows
    ) throws IOException {
        List<List<String>> csv = E001Csv.read(Files.readString(path));
        if (csv.size() != expectedRows + 1 || !csv.getFirst().equals(PERFORMANCE_HEADER)) {
            throw new IOException("성능 CSV header 또는 행 수가 유효하지 않아요.");
        }

        Map<String, String> parameters = source.e() == null
                ? Map.of("D", source.d().parameterSetId())
                : Map.of("D", source.d().parameterSetId(), "E", source.e().parameterSetId());
        List<String> expectedOrder = performanceOrder(source.e() != null);
        List<E001Performance.Batch> batches = new ArrayList<>();
        var observed = new HashSet<String>();
        for (List<String> cells : csv.subList(1, csv.size())) {
            E001Performance.Batch batch = readPerformanceBatch(cells, parameters, observed);
            if (!batchKey(batch).equals(expectedOrder.get(batches.size()))) {
                throw new IOException("성능 CSV batch 순서가 사전등록과 달라요.");
            }
            batches.add(batch);
        }
        if (observed.size() != expectedRows) {
            throw new IOException("성능 CSV batch 구성이 중복되거나 누락됐어요.");
        }
        return List.copyOf(batches);
    }

    private static List<String> performanceOrder(boolean includeE) {
        List<String> order = new ArrayList<>();
        for (String phase : List.of("warmup", "measurement")) {
            int batchCount = phase.equals("warmup") ? WARMUP_BATCH_COUNT : MEASUREMENT_BATCH_COUNT;
            for (int index = 0; index < batchCount; index++) {
                List<String> models = !includeE ? List.of("D")
                        : index % 2 == 0 ? List.of("D", "E") : List.of("E", "D");
                for (String model : models) {
                    order.add(model + "|" + phase + "|" + index);
                }
            }
        }
        return List.copyOf(order);
    }

    private static E001Performance.Batch readPerformanceBatch(
            List<String> cells,
            Map<String, String> parameters,
            HashSet<String> observed
    ) throws IOException {
        if (cells.size() != PERFORMANCE_HEADER.size()) {
            throw new IOException("성능 CSV 열 수가 유효하지 않아요.");
        }
        try {
            String modelId = cells.get(0);
            String parameterSetId = cells.get(1);
            String phase = cells.get(2);
            int index = Integer.parseInt(cells.get(3));
            int points = Integer.parseInt(cells.get(4));
            long elapsed = Long.parseLong(cells.get(5));
            int batchCount = switch (phase) {
                case "warmup" -> WARMUP_BATCH_COUNT;
                case "measurement" -> MEASUREMENT_BATCH_COUNT;
                default -> throw new IOException("성능 CSV phase가 유효하지 않아요.");
            };
            String key = modelId + "|" + phase + "|" + index;
            if (!parameterSetId.equals(parameters.get(modelId)) || index < 0 || index >= batchCount
                    || points != POINTS_PER_BATCH || elapsed < 0 || !observed.add(key)
                    || !cells.get(3).equals(Integer.toString(index))
                    || !cells.get(4).equals(Integer.toString(points))
                    || !cells.get(5).equals(Long.toString(elapsed))) {
                throw new IOException("성능 CSV model·parameter·batch 값이 유효하지 않아요.");
            }

            E001Performance.Batch batch = E001Performance.Batch.of(
                    modelId, parameterSetId, phase, index, points, elapsed
            );
            double recordedNsPerPoint = Double.parseDouble(cells.get(6));
            if (!Double.isFinite(recordedNsPerPoint)
                    || Double.doubleToRawLongBits(recordedNsPerPoint)
                    != Double.doubleToRawLongBits(batch.nsPerPoint())
                    || !cells.get(6).equals(E001ArtifactFormat.decimal(batch.nsPerPoint()))) {
                throw new IOException("성능 CSV ns_per_point가 elapsed_ns와 일치하지 않아요.");
            }
            return batch;
        } catch (NumberFormatException exception) {
            throw new IOException("성능 CSV 숫자 형식이 유효하지 않아요.", exception);
        }
    }

    private static E001Performance.Gate evaluatePerformance(List<E001Performance.Batch> batches, String modelId)
            throws IOException {
        try {
            return E001Performance.evaluate(batches, modelId);
        } catch (IllegalArgumentException exception) {
            throw new IOException("성능 측정 batch 구성이 유효하지 않아요.", exception);
        }
    }

    private static String batchKey(E001Performance.Batch batch) {
        return batch.modelId() + "|" + batch.phase() + "|" + batch.index();
    }

    private static Path lockedPublish(Path parent, String kind, String runId, String reason, E001Artifacts.Metadata metadata,
            Producer producer, E001ArtifactStore.Mover mover) throws IOException {
        try (var paths = Files.list(parent)) {
            if (paths.anyMatch(path -> path.getFileName().toString().startsWith("e001-v2-previous-"))) {
                throw new IOException("분포 previous의 복구·보존을 마친 뒤 sidecar를 실행해야 해요.");
            }
        }
        Path root = parent.resolve("e001-v2");
        E001RunContext.Source source = E001RunContext.source(root, metadata);
        Path official = root.resolve(kind);
        Path archive = parent.resolve("e001-v2-invalidated");
        Path staging = parent.resolve("e001-v2-" + kind + "-staging-" + runId);
        directory(archive);
        List<Path> interrupted;
        try (var paths = Files.list(parent)) {
            interrupted = paths.filter(path -> path.getFileName().toString().startsWith("e001-v2-" + kind + "-staging-"))
                    .sorted()
                    .toList();
        }
        // 중단된 전체 실행도 한 번의 시도로 세고, partial 파일을 공식 결과에 섞지 않아요.
        for (Path path : interrupted) {
            if (!path.getFileName().toString().matches("e001-v2-" + kind + "-staging-[0-9a-f]{32}")) {
                throw new IOException("중단된 staging 이름을 확인해야 해요.");
            }
            invalidate(path, archive, kind, "interrupted", mover);
        }
        List<Path> history = kind.equals("performance") ? performanceHistory(archive, official, source.checksum()) : List.of();
        List<Path> validHistory = new ArrayList<>();
        for (Path path : history) {
            if (E001RunContext.read(path.resolve("manifest.json")).path("status").asString().equals("valid")) {
                validHistory.add(path);
            }
        }
        if (kind.equals("performance")) {
            if (blindStarted(parent, root, archive, source.checksum())) {
                throw new IOException("블라인드 평가가 시작된 뒤에는 성능 측정을 바꾸지 않아요.");
            }
        } else {
            verifyPerformance(root, source);
        }
        if ((exists(official) || !validHistory.isEmpty()) && reason.isEmpty()) {
            throw new IOException("기존 결과를 다시 만들려면 명시적인 무효화 이유가 필요해요.");
        }
        Path probe = Files.createTempDirectory(parent, "e001-v2-sidecar-probe-");
        Path movedProbe = probe.resolveSibling(probe.getFileName() + "-moved");
        move(probe, movedProbe, mover);
        Files.delete(movedProbe);
        if (kind.equals("performance") && attempts(history) >= 2) {
            // 두 번째 유효 측정도 오염됐다면 무효 이력으로 남기되 전체 결과를 inconclusive로 끝내요.
            for (Path path : validHistory) {
                invalidate(path, archive, kind, reason, mover);
            }
            throw new IOException("성능 측정은 전체 재시도 한 번까지예요. 두 번째 실행도 무효하면 결과는 inconclusive예요.");
        }
        for (Path path : validHistory) {
            if (!path.equals(official)) {
                invalidate(path, archive, kind, reason, mover);
            }
        }
        if (exists(official)) {
            invalidate(official, archive, kind, reason, mover);
        }
        Files.createDirectory(staging);
        Map<String, Object> manifest = new HashMap<>();
        manifest.put("kind", kind);
        manifest.put("runId", runId);
        manifest.put("status", "running");
        manifest.put("distributionChecksum", source.checksum());
        manifest.put("invalidationReason", "");
        manifest.put("machineRows", Map.of());
        manifest.put("machineFileCount", 1);
        protectMetadata(manifest, metadata);
        E001ArtifactFormat.write(staging.resolve("manifest.json"), E001ArtifactFormat.json(manifest));
        try {
            Map<String, Object> result = producer.write(staging, root, source);
            if (!source.equals(E001RunContext.source(root, metadata))) {
                throw new IOException("실행 도중 분포 결과가 바뀌었어요.");
            }
            manifest.putAll(result);
            manifest.put("kind", kind);
            manifest.put("runId", runId);
            manifest.put("distributionChecksum", source.checksum());
            manifest.put("status", "valid");
            manifest.put("invalidationReason", "");
            protectMetadata(manifest, metadata);
            Map<String, String> files = dataChecksums(staging);
            manifest.put("dataChecksums", files);
            manifest.put("machineFileCount", files.size() + 1);
            replaceManifest(staging, manifest);
            move(staging, official, mover);
            return official;
        } catch (IOException | RuntimeException exception) {
            try {
                if (exists(staging)) {
                    invalidate(staging, archive, kind, "failed_" + exception.getClass().getSimpleName(), mover);
                }
            } catch (IOException preservationFailure) {
                exception.addSuppressed(preservationFailure);
            }
            throw exception;
        }
    }

    private static boolean blindStarted(Path parent, Path root, Path archive, String checksum) throws IOException {
        if (exists(root.resolve("blind"))) {
            return true;
        }

        List<Path> manifests = new ArrayList<>();
        try (var paths = Files.walk(archive)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("블라인드 실행 이력에 심볼릭 링크를 사용하지 않아요.");
                }
                String relative = archive.relativize(path).toString().replace('\\', '/');
                if (relative.matches("blind-[0-9a-f]{32}|distribution-[0-9a-f]{32}/blind")) {
                    manifests.add(path.resolve("manifest.json"));
                }
            }
        }
        try (var paths = Files.list(parent)) {
            for (Path path : paths.toList()) {
                String name = path.getFileName().toString();
                if (!name.startsWith("e001-v2-blind-staging-")) {
                    continue;
                }
                if (!name.matches("e001-v2-blind-staging-[0-9a-f]{32}")) {
                    throw new IOException("블라인드 staging 이름을 확인해야 해요.");
                }
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("블라인드 staging에 심볼릭 링크를 사용하지 않아요.");
                }
                Path manifest = path.resolve("manifest.json");
                if (!exists(manifest)) {
                    return true;
                }
                manifests.add(manifest);
            }
        }
        for (Path manifest : manifests) {
            var value = historyManifest(manifest.getParent(), "blind");
            if (value.path("distributionChecksum").asString().equals(checksum)) {
                return true;
            }
        }
        return false;
    }

    private static void protectMetadata(Map<String, Object> manifest, E001Artifacts.Metadata metadata) {
        manifest.put("protocolVersion", PROTOCOL_VERSION);
        manifest.put("schemaVersion", SCHEMA_VERSION);
        manifest.put("protocolSha", metadata.protocolSha());
        manifest.put("runnerSha", metadata.runnerSha());
    }

    private static List<Path> performanceHistory(Path archive, Path official, String checksum) throws IOException {
        List<Path> candidates;
        try (var paths = Files.walk(archive)) {
            candidates = new ArrayList<>();
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("실행 이력에 심볼릭 링크를 사용하지 않아요.");
                }
                String relative = archive.relativize(path).toString().replace('\\', '/');
                if (relative.matches("performance-[0-9a-f]{32}|distribution-[0-9a-f]{32}/performance")) {
                    candidates.add(path);
                }
            }
        }
        if (exists(official)) {
            candidates.add(official);
        }
        List<Path> matching = new ArrayList<>();
        for (Path candidate : candidates) {
            var manifest = historyManifest(candidate, "performance");
            if (manifest.path("distributionChecksum").asString().equals(checksum)) {
                matching.add(candidate);
            }
        }
        return List.copyOf(matching);
    }

    private static tools.jackson.databind.JsonNode historyManifest(Path directory, String kind) throws IOException {
        String label = kind.equals("blind") ? "블라인드" : "성능";
        Path manifest = directory.resolve("manifest.json");
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " 실행 manifest는 일반 파일이어야 해요.");
        }
        var value = E001RunContext.read(manifest);
        String name = directory.getFileName().toString();
        String runId = value.path("runId").asString();
        if (!value.path("kind").asString().equals(kind) || !runId.matches("[0-9a-f]{32}")
                || !name.equals(kind) && !name.endsWith("-" + runId)
                || !value.path("distributionChecksum").asString().matches("[0-9a-f]{64}")) {
            throw new IOException(label + " 실행 이력을 확인해야 해요.");
        }
        return value;
    }

    private static int attempts(List<Path> history) throws IOException {
        var runIds = new HashSet<String>();
        for (Path path : history) {
            runIds.add(E001RunContext.read(path.resolve("manifest.json")).path("runId").asString());
        }
        return runIds.size();
    }

    private static void invalidate(Path source, Path archive, String kind, String reason, E001ArtifactStore.Mover mover) throws IOException {
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("무효화 대상이 일반 디렉터리가 아니에요.");
        }
        Map<String, String> preserved = dataChecksums(source);
        Map<String, Object> manifest = E001RunContext.readMap(source.resolve("manifest.json"));
        String originalId = String.valueOf(manifest.get("runId"));
        if (!originalId.matches("[0-9a-f]{32}") || !kind.equals(manifest.get("kind"))) {
            throw new IOException("무효화 대상의 실행 식별자가 유효하지 않아요.");
        }
        Path target = archive.resolve(kind + "-" + originalId);
        if (exists(target)) {
            throw new IOException("기존 무효 실행을 덮어쓰지 않아요.");
        }
        if (!exists(source.resolve("manifest-before-invalidation.json"))) {
            Files.copy(source.resolve("manifest.json"), source.resolve("manifest-before-invalidation.json"));
        }
        if (manifest.get("status").equals("running")) {
            Map<String, Object> rows = new TreeMap<>();
            for (String file : preserved.keySet()) {
                if (file.endsWith(".csv")) {
                    try {
                        rows.put(file, StrictMath.max(0, E001Csv.read(Files.readString(source.resolve(file))).size() - 1));
                    } catch (IOException exception) {
                        // 쓰는 도중 중단된 CSV의 행 수는 확정하지 않고 원본 그대로 보존해요.
                        rows.put(file, null);
                    }
                }
            }
            manifest.put("machineRows", rows);
            manifest.put("machineFileCount", preserved.size() + 1);
        }
        manifest.put("status", "invalidated");
        manifest.put("invalidationReason", reason);
        replaceManifest(source, manifest);
        if (kind.equals("blind") && reason.equals("blind_exposed")) {
            invalidateResponses(source);
        }
        move(source, target, mover);
    }

    private static void invalidateResponses(Path source) throws IOException {
        Path responses = source.resolve("reviewer-package/blind-responses.csv");
        if (!exists(responses)) {
            return;
        }
        List<List<String>> rows = E001Csv.read(Files.readString(responses));
        if (rows.isEmpty() || !E001ArtifactFormat.csv(rows.getFirst()).equals(E001Blind.BALLOT_HEADER)
                || rows.stream().anyMatch(row -> row.size() != 7)) {
            throw new IOException("노출된 응답 CSV 형식을 확인한 뒤 무효화를 다시 실행해야 해요.");
        }
        Path original = source.resolve("coordinator-only/blind-responses-before-invalidation.csv");
        if (!exists(original)) {
            Files.copy(responses, original);
        }
        StringBuilder text = new StringBuilder(E001Blind.BALLOT_HEADER);
        for (List<String> row : rows.subList(1, rows.size())) {
            List<String> invalid = new ArrayList<>(row);
            invalid.set(5, "invalidated");
            invalid.set(6, "blind_exposed");
            text.append(E001ArtifactFormat.csv(invalid));
        }
        Path temporary = responses.resolveSibling("blind-responses-invalidated-" + E001RunContext.runId() + ".csv");
        E001ArtifactFormat.write(temporary, text.toString());
        Files.move(temporary, responses, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static Map<String, String> dataChecksums(Path directory) throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("sidecar에 심볼릭 링크를 사용하지 않아요.");
                }
                String relative = directory.relativize(path).toString().replace('\\', '/');
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !relative.equals("manifest.json")
                        && !relative.equals("reviewer-package/blind-responses.csv")) {
                    files.put(relative, E001ArtifactFormat.sha256(path));
                }
            }
        }
        return files;
    }

    private static void replaceManifest(Path directory, Map<String, Object> manifest) throws IOException {
        Path temporary = directory.resolve("manifest-next-" + E001RunContext.runId() + ".json");
        E001ArtifactFormat.write(temporary, E001ArtifactFormat.json(manifest));
        Files.move(temporary, directory.resolve("manifest.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void directory(Path path) throws IOException {
        if (exists(path) && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("보존 경로가 일반 디렉터리가 아니에요.");
        }
        Files.createDirectories(path);
    }

    private static boolean exists(Path path) {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static void move(Path source, Path target, E001ArtifactStore.Mover mover) throws IOException {
        if (exists(target)) {
            throw new IOException("이동 대상을 덮어쓰지 않아요.");
        }
        mover.move(source, target);
    }

    @FunctionalInterface
    interface Producer {

        Map<String, Object> write(Path staging, Path root, E001RunContext.Source source) throws IOException;
    }
}
