package com.pheeeew.sigh.experiment.e001;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

final class E001ArtifactStore {

    private E001ArtifactStore() {
    }

    static Path publish(Path parent, String runId, Producer producer) throws IOException {
        return publish(parent, runId, producer, (source, target) -> Files.move(source, target, StandardCopyOption.ATOMIC_MOVE));
    }

    static Path publish(Path parent, String runId, Producer producer, Mover mover) throws IOException {
        if (!runId.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("runId는 16 byte 소문자 hexadecimal이어야 해요.");
        }
        directory(parent);
        Path lockPath = parent.resolve(".e001-distribution.lock");
        if (Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("실행 lock 경로가 일반 파일이 아니에요.");
        }
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) {
                throw new IOException("다른 분포 실행이 진행 중이에요.");
            }
            return lockedPublish(parent, runId, producer, mover);
        } catch (OverlappingFileLockException exception) {
            throw new IOException("다른 분포 실행이 진행 중이에요.", exception);
        }
    }

    private static Path lockedPublish(Path parent, String runId, Producer producer, Mover mover) throws IOException {
        Path official = parent.resolve("e001");
        Path archive = parent.resolve("e001-invalidated");
        Path first = parent.resolve("e001-run1-staging");
        Path second = parent.resolve("e001-run2-staging");
        Path previous = parent.resolve("e001-previous-" + runId);
        List<Path> recoverable;
        try (var paths = Files.list(parent)) {
            recoverable = paths.filter(path -> path.getFileName().toString().startsWith("e001-previous-")).sorted().toList();
        }
        if (recoverable.size() > 1) {
            throw new IOException("복구할 previous가 둘 이상이라 자동 이동하지 않아요.");
        }
        for (Path path : List.of(official, archive, first, second)) {
            checkDirectoryIfPresent(path);
        }
        for (Path path : recoverable) {
            checkDirectoryIfPresent(path);
            if (!path.getFileName().toString().matches("e001-previous-[0-9a-f]{32}")) {
                throw new IOException("previous 경로의 runId가 유효하지 않아요.");
            }
        }
        directory(archive);
        if (!recoverable.isEmpty()) {
            Path interrupted = recoverable.getFirst();
            if (!exists(official)) {
                move(interrupted, official, mover);
            } else {
                move(interrupted, archive.resolve("distribution-" + interrupted.getFileName().toString().substring(14)), mover);
            }
        }
        for (int index = 0; index < 2; index++) {
            Path staging = index == 0 ? first : second;
            if (exists(staging)) {
                move(staging, archive.resolve("distribution-staging-" + runId + "-" + (index + 1)), mover);
            }
        }
        Path active = first;
        boolean promoted = false;
        try {
            Files.createDirectory(first);
            producer.write(first);
            E001Checksums.verify(first);
            active = second;
            Files.createDirectory(second);
            producer.write(second);
            E001Checksums.verify(second);
            if (Files.mismatch(first.resolve("checksums.sha256"), second.resolve("checksums.sha256")) != -1) {
                throw new IOException("두 실행의 결정적 산출물이 달라요.");
            }
            Files.copy(first.resolve("checksums.sha256"), second.resolve("verification-run1-checksums.sha256"));
            Path probe = Files.createTempDirectory(parent, "e001-atomic-probe-");
            Path probeMoved = probe.resolveSibling(probe.getFileName() + "-moved");
            move(probe, probeMoved, mover);
            Files.delete(probeMoved);
            if (exists(official)) {
                move(official, previous, mover);
            }
            try {
                move(second, official, mover);
            } catch (IOException exception) {
                if (exists(previous) && !exists(official)) {
                    try {
                        move(previous, official, mover);
                    } catch (IOException restoreFailure) {
                        exception.addSuppressed(restoreFailure);
                    }
                }
                throw exception;
            }
            promoted = true;
            active = first;
            if (exists(previous)) {
                move(previous, archive.resolve("distribution-" + runId), mover);
            }
            return official;
        } catch (IOException | RuntimeException exception) {
            if (Files.isDirectory(active, LinkOption.NOFOLLOW_LINKS) && !exists(active.resolve("failure.json"))) {
                try {
                    E001ArtifactFormat.write(active.resolve("failure.json"), E001ArtifactFormat.json(Map.of(
                            "status", "failed", "reason", exception.getClass().getSimpleName(), "officialPromoted", promoted)));
                } catch (IOException markerFailure) {
                    exception.addSuppressed(markerFailure);
                }
            }
            throw exception;
        }
    }

    private static void directory(Path path) throws IOException {
        checkDirectoryIfPresent(path);
        Files.createDirectories(path);
    }

    private static void checkDirectoryIfPresent(Path path) throws IOException {
        if (exists(path) && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("산출물 경로가 일반 디렉터리가 아니에요.");
        }
    }

    private static boolean exists(Path path) {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static void move(Path source, Path target, Mover mover) throws IOException {
        if (exists(target)) {
            throw new IOException("이동 대상이 이미 존재해 덮어쓰지 않아요.");
        }
        mover.move(source, target);
    }

    @FunctionalInterface
    interface Producer {

        void write(Path staging) throws IOException;
    }

    @FunctionalInterface
    interface Mover {

        void move(Path source, Path target) throws IOException;
    }
}
