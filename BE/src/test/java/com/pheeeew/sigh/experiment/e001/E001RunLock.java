package com.pheeeew.sigh.experiment.e001;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class E001RunLock {

    private E001RunLock() {
    }

    static Path execute(Path parent, Action action) throws IOException {
        if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("실행 경로가 일반 디렉터리가 아니에요.");
        }
        Files.createDirectories(parent);
        Path path = parent.resolve(".e001-distribution.lock");
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("실행 lock 경로가 일반 파일이 아니에요.");
        }
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) {
                throw new IOException("다른 E001 실행이 진행 중이에요.");
            }
            return action.run();
        } catch (OverlappingFileLockException exception) {
            throw new IOException("다른 E001 실행이 진행 중이에요.", exception);
        }
    }

    @FunctionalInterface
    interface Action {

        Path run() throws IOException;
    }
}
