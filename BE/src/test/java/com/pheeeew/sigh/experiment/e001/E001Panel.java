package com.pheeeew.sigh.experiment.e001;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;

final class E001Panel {

    static final int SIZE = 1_200;
    static final int BACKGROUND = 0x11131A;

    private E001Panel() {
    }

    static byte[] sprite() throws IOException {
        try (InputStream input = E001Panel.class.getResourceAsStream("/experiments/e001/star-8x8.rgba.hex")) {
            if (input == null) {
                throw new IOException("고정 별 sprite가 없어요.");
            }
            byte[] bytes = HexFormat.of().parseHex(new String(input.readAllBytes(), US_ASCII).replaceAll("\\s", ""));
            if (bytes.length != 8 * 8 * 4) {
                throw new IOException("별 sprite는 8×8 RGBA여야 해요.");
            }
            return bytes;
        }
    }

    static BufferedImage render(List<E001Sample> samples, double originX, double originY) throws IOException {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        int[] background = new int[SIZE * SIZE];
        java.util.Arrays.fill(background, BACKGROUND);
        image.setRGB(0, 0, SIZE, SIZE, background, 0, SIZE);
        byte[] sprite = sprite();
        List<E001Sample> ordered = samples.stream().sorted(Comparator.comparing(E001Sample::centerId)
                .thenComparingLong(E001Sample::sampleSeed).thenComparingLong(E001Sample::pointIndex)).toList();
        for (E001Sample sample : ordered) {
            if (!Double.isFinite(sample.x()) || !Double.isFinite(sample.y())) {
                throw new IOException("non-finite 좌표는 렌더링할 수 없어요.");
            }
            long left = StrictMath.round(sample.x() - (originX - 600.0)) - 4;
            long top = StrictMath.round((originY + 600.0) - sample.y()) - 4;
            if (left < -7 || top < -7 || left >= SIZE || top >= SIZE) {
                continue;
            }
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int px = (int) left + x;
                    int py = (int) top + y;
                    if (px >= 0 && px < SIZE && py >= 0 && py < SIZE) {
                        int offset = (y * 8 + x) * 4;
                        int alpha = Byte.toUnsignedInt(sprite[offset + 3]);
                        int previous = image.getRGB(px, py);
                        int color = 0;
                        for (int channel = 0; channel < 3; channel++) {
                            int shift = (2 - channel) * 8;
                            int source = Byte.toUnsignedInt(sprite[offset + channel]);
                            int destination = (previous >>> shift) & 255;
                            color |= ((source * alpha + destination * (255 - alpha) + 127) / 255) << shift;
                        }
                        image.setRGB(px, py, color);
                    }
                }
            }
        }
        return image;
    }

    static void write(Path path, List<E001Sample> samples, double originX, double originY) throws IOException {
        try (var output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
            if (!ImageIO.write(render(samples, originX, originY), "png", output)) {
                throw new IOException("PNG writer를 사용할 수 없어요.");
            }
        }
    }
}
