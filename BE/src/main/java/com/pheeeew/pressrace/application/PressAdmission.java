package com.pheeeew.pressrace.application;

import com.pheeeew.pressrace.exception.PressRaceException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** 컨테이너 재시작에도 유지되는 운영 입력 차단. 저장 자격 자체는 DB 세대 울타리가 검사한다. */
final class PressAdmission {

    private final Path file;
    private final UUID serverGeneration;

    PressAdmission(String configuredFile, UUID serverGeneration, boolean required) {
        if (configuredFile.isBlank() && required) {
            throw new IllegalStateException("Required press admission file is not configured");
        }
        this.file = configuredFile.isBlank() ? null : Path.of(configuredFile);
        this.serverGeneration = serverGeneration;
        if (file != null) {
            if (!file.isAbsolute()) {
                throw new IllegalStateException("Press admission file must be absolute");
            }
            initialize();
        }
    }

    String openRevision() {
        State state = read();
        if (!state.mode().equals("OPEN")) {
            throw new PressRaceException("SERVER_DRAINING");
        }
        return state.revision();
    }

    void requireOpen(String revision) {
        State state = read();
        if (!state.mode().equals("OPEN") || !state.revision().equals(revision)) {
            throw new PressRaceException("SERVER_DRAINING");
        }
    }

    boolean isClosed() {
        return read().mode().equals("CLOSED");
    }

    void acknowledgeClosed() {
        State state = read();
        if (file == null || !state.mode().equals("CLOSED") || state.revision().isEmpty()) {
            return;
        }
        String contents = "CLOSED " + state.revision() + " " + serverGeneration + "\n";
        Path acknowledgement = file.resolveSibling(file.getFileName() + ".closed");
        Path temporary = file.resolveSibling(file.getFileName() + ".closed." + UUID.randomUUID());
        try {
            if (Files.exists(acknowledgement)
                    && Files.readString(acknowledgement, StandardCharsets.US_ASCII).equals(contents)) {
                return;
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer bytes = ByteBuffer.wrap(contents.getBytes(StandardCharsets.US_ASCII));
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                channel.force(true);
            }
            Files.move(temporary, acknowledgement, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            forceDirectory();
        } catch (IOException ignored) {
            // 확인 응답이 없으면 운영 도구가 다음 단계로 가지 않는다. 차단 파일을 열지 않는다.
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // 실패한 확인 파일은 입력 자격을 주지 않는다.
            }
        }
    }

    private void initialize() {
        byte[] initial = ("CLOSED " + UUID.randomUUID() + " " + serverGeneration + "\n")
                .getBytes(StandardCharsets.US_ASCII);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer contents = ByteBuffer.wrap(initial);
            while (contents.hasRemaining()) {
                channel.write(contents);
            }
            channel.force(true);
            forceDirectory();
        } catch (FileAlreadyExistsException ignored) {
            // OPEN도 CLOSED도 초기화하지 않는다. 재시작은 이전 운영 상태를 그대로 읽는다.
        } catch (IOException exception) {
            throw new IllegalStateException("Press admission file could not be initialized", exception);
        }
    }

    private void forceDirectory() throws IOException {
        try (FileChannel directory = FileChannel.open(file.getParent(), StandardOpenOption.READ)) {
            directory.force(true);
        }
    }

    private State read() {
        if (file == null) {
            return State.of("OPEN", "");
        }
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(256);
            String contents = new String(bytes, StandardCharsets.US_ASCII);
            if (bytes.length == 256 || !contents.endsWith("\n")) {
                return State.of("CLOSED", "");
            }
            String[] fields = contents.substring(0, contents.length() - 1).split(" ", -1);
            if (fields.length != 3 || !fields[2].equals(serverGeneration.toString())
                    || !UUID.fromString(fields[1]).toString().equals(fields[1])) {
                return State.of("CLOSED", "");
            }
            if (!fields[0].equals("OPEN") && !fields[0].equals("PAUSED") && !fields[0].equals("CLOSED")) {
                return State.of("CLOSED", "");
            }
            return State.of(fields[0], fields[1]);
        } catch (IOException | IllegalArgumentException exception) {
            return State.of("CLOSED", "");
        }
    }

    private record State(String mode, String revision) {
        private static State of(String mode, String revision) {
            return new State(mode, revision);
        }
    }
}
