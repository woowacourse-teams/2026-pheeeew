package com.pheeeew.sigh.experiment.e001;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.json.JsonMapper;

final class E001ArtifactFormat {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private E001ArtifactFormat() {
    }

    static String decimal(double value) {
        return value == 0.0 ? "0.0" : Double.toString(value);
    }

    static String csv(List<String> cells) {
        return cells.stream().map(value -> value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
                ? "\"" + value.replace("\"", "\"\"") + "\"" : value).collect(java.util.stream.Collectors.joining(",")) + "\n";
    }

    static String json(Map<String, ?> value) {
        return JSON.writeValueAsString(sorted(value)) + "\n";
    }

    static void write(Path path, String text) throws IOException {
        Files.writeString(path, text, UTF_8, StandardOpenOption.CREATE_NEW);
    }

    static String sha256(Path path) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8_192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(digest().digest(bytes));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Object sorted(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> ordered = new TreeMap<>();
            map.forEach((key, item) -> ordered.put((String) key, sorted(item)));
            return ordered;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(E001ArtifactFormat::sorted).toList();
        }
        if (value instanceof Double number && !Double.isFinite(number)) {
            return null;
        }
        return value;
    }
}
