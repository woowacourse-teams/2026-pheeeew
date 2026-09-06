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
import java.util.TreeMap;

final class E001SidecarStore {

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

    static void verifyPerformance(Path root, String checksum) throws IOException {
        Path directory = root.resolve("performance");
        var manifest = E001RunContext.read(directory.resolve("manifest.json"));
        if (!manifest.path("status").asString().equals("valid") || !manifest.path("distributionChecksum").asString().equals(checksum)
                || !manifest.path("kind").asString().equals("performance") || manifest.path("machineRows").path("performance.csv").asInt() != 30) {
            throw new IOException("같은 분포의 유효한 전체 성능 측정이 먼저 필요해요.");
        }
        var expected = manifest.path("dataChecksums");
        Map<String, String> actual = dataChecksums(directory);
        if (expected.size() != actual.size()) {
            throw new IOException("성능 파일 개수가 달라요.");
        }
        for (var entry : actual.entrySet()) {
            if (!entry.getValue().equals(expected.path(entry.getKey()).asString())) {
                throw new IOException("성능 측정 파일이 변경됐어요.");
            }
        }
    }

    private static Path lockedPublish(Path parent, String kind, String runId, String reason, E001Artifacts.Metadata metadata,
            Producer producer, E001ArtifactStore.Mover mover) throws IOException {
        Path root = parent.resolve("e001");
        E001RunContext.Source source = E001RunContext.source(root, metadata);
        Path official = root.resolve(kind);
        Path archive = parent.resolve("e001-invalidated");
        Path staging = parent.resolve("e001-" + kind + "-staging-" + runId);
        directory(archive);
        List<Path> interrupted;
        try (var paths = Files.list(parent)) {
            interrupted = paths.filter(path -> path.getFileName().toString().startsWith("e001-" + kind + "-staging-")).sorted().toList();
        }
        // 중단된 전체 실행도 한 번의 시도로 세고, partial 파일을 공식 결과에 섞지 않아요.
        for (Path path : interrupted) {
            if (!path.getFileName().toString().matches("e001-" + kind + "-staging-[0-9a-f]{32}")) {
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
            if (exists(root.resolve("blind")) || validHistory.stream().anyMatch(path -> exists(path.getParent().resolve("blind")))) {
                throw new IOException("블라인드 평가가 시작된 뒤에는 성능 측정을 바꾸지 않아요.");
            }
        } else {
            verifyPerformance(root, source.checksum());
        }
        if ((exists(official) || !validHistory.isEmpty()) && reason.isEmpty()) {
            throw new IOException("기존 결과를 다시 만들려면 명시적인 무효화 이유가 필요해요.");
        }
        Path probe = Files.createTempDirectory(parent, "e001-sidecar-probe-");
        Path movedProbe = probe.resolveSibling(probe.getFileName() + "-moved");
        move(probe, movedProbe, mover);
        Files.delete(movedProbe);
        if (kind.equals("performance") && attempts(history) >= 2) {
            // 두 번째 유효 측정도 오염됐다면 무효 이력으로 남기되 세 번째 측정은 시작하지 않아요.
            for (Path path : validHistory) {
                invalidate(path, archive, kind, reason, mover);
            }
            throw new IOException("성능 측정은 전체 재시도 한 번까지예요. 두 번째 실행도 무효하면 E를 채택하지 않아요.");
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

    private static List<Path> performanceHistory(Path archive, Path official, String checksum) throws IOException {
        List<Path> candidates;
        try (var paths = Files.walk(archive)) {
            candidates = new ArrayList<>();
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("실행 이력에 심볼릭 링크를 사용하지 않아요.");
                }
                String relative = archive.relativize(path).toString().replace('\\', '/');
                if (relative.matches("(performance-[0-9a-f]{32}|distribution-[0-9a-f]{32}/performance)/manifest.json")) {
                    candidates.add(path.getParent());
                }
            }
        }
        if (exists(official)) {
            candidates.add(official);
        }
        List<Path> matching = new ArrayList<>();
        for (Path candidate : candidates) {
            var manifest = E001RunContext.read(candidate.resolve("manifest.json"));
            if (manifest.path("distributionChecksum").asString().equals(checksum)) {
                String id = manifest.path("runId").asString();
                if (!id.matches("[0-9a-f]{32}")) {
                    throw new IOException("성능 실행 이력을 확인해야 해요.");
                }
                matching.add(candidate);
            }
        }
        return List.copyOf(matching);
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
