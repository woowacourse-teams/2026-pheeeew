package com.pheeeew.sigh.experiment.e001;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class E001Checksums {

    static final List<String> REQUIRED = List.of("manifest.json", "coordinates.csv.gz", "metrics.csv", "sampler-failures.csv", "conformance.csv", "boundary-observations.csv");

    private E001Checksums() {
    }

    static void write(Path root) throws IOException {
        E001ArtifactFormat.write(root.resolve("checksums.sha256"), calculate(root));
    }

    static void verify(Path root) throws IOException {
        Path checksum = root.resolve("checksums.sha256");
        if (!Files.isRegularFile(checksum, LinkOption.NOFOLLOW_LINKS)
                || !Files.readString(checksum).equals(calculate(root))) {
            throw new IOException("결정적 산출물 checksum이 일치하지 않아요.");
        }
    }

    private static String calculate(Path root) throws IOException {
        List<String> paths = new ArrayList<>(REQUIRED);
        for (String path : REQUIRED) {
            if (!Files.isRegularFile(root.resolve(path), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("필수 산출물이 없거나 일반 파일이 아니에요: " + path);
            }
        }
        Path panels = root.resolve("coordinator-only/model-panels");
        try (var walk = Files.walk(root)) {
            for (Path path : walk.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("산출물에 symbolic link가 있어요.");
                }
                if (Files.isRegularFile(path) && path.startsWith(panels)) {
                    if (!path.getFileName().toString().endsWith(".png")) {
                        throw new IOException("model panel은 PNG만 허용해요.");
                    }
                    paths.add(root.relativize(path).toString().replace('\\', '/'));
                }
            }
        }
        StringBuilder text = new StringBuilder();
        for (String path : paths.stream().sorted().toList()) {
            text.append(E001ArtifactFormat.sha256(root.resolve(path))).append("  ").append(path).append('\n');
        }
        return text.toString();
    }
}
