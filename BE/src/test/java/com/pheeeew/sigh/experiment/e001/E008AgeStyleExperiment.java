package com.pheeeew.sigh.experiment.e001;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadataNode;

/** 24시간 노출을 모의하는 탐색 렌더러예요. 실제 조회 정책을 변경하지 않아요. */
public final class E008AgeStyleExperiment {

    static final int GOLD = 0xFFE2A3;
    static final int BLUE = 0xA8D8FF;
    static final int RED = 0xFF8C78;
    private static final int WIDTH = 1600;
    private static final int HEIGHT = 1860;
    private static final int PANEL = 700;
    private static final int[] HOURS = {0, 6, 12, 18, 24};
    private static final String[] SCENES = {"hotspots-4500", "hotspots-45000", "uniform-45000"};

    record Star(String scene, String model, String seed, String index, double tx, double ty, double x, double y, double birth) {
        static Star of(String[] row) {
            return new Star(row[0], row[1], row[2], row[3], Double.parseDouble(row[4]), Double.parseDouble(row[5]),
                    Double.parseDouble(row[8]), Double.parseDouble(row[9]), E008AgeStyleExperiment.birth(row[0] + "|" + row[2] + "|" + row[3]));
        }
        String identity() { return scene + "|" + seed + "|" + index; }
    }

    private E008AgeStyleExperiment() {
    }

    static double alpha(double age) {
        if (!Double.isFinite(age)) { throw new IllegalArgumentException("유한한 나이가 필요해요."); }
        if (age < 0 || age >= 24) { return 0; }
        if (age <= 2) { return 1; }
        double u = (age - 2) / 22;
        return 1 - 3 * u * u + 2 * u * u * u;
    }

    static int color(boolean colored, double age) {
        if (!Double.isFinite(age)) { throw new IllegalArgumentException("유한한 나이가 필요해요."); }
        if (!colored) { return GOLD; }
        double a = Math.max(0, Math.min(24, age));
        int from = a <= 12 ? BLUE : GOLD, to = a <= 12 ? GOLD : RED;
        double t = a <= 12 ? a / 12 : (a - 12) / 12;
        int result = 0;
        for (int shift : new int[]{16, 8, 0}) {
            int c = (int) StrictMath.round(((from >> shift) & 255) * (1 - t) + ((to >> shift) & 255) * t);
            result |= c << shift;
        }
        return result;
    }

    static double birth(String identity) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(("e008|birth|" + identity).getBytes(StandardCharsets.UTF_8));
            return 48 * new SplittableRandom(ByteBuffer.wrap(hash).getLong()).nextDouble() - 24;
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) { throw new IllegalArgumentException("E007 입력과 새 출력 경로를 지정해요."); }
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        String inputHash = sha(input);
        List<Star> stars = read(input);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.createDirectory(output);
        Map<String, String> first = run(Files.createDirectory(output.resolve("run1")), stars);
        Map<String, String> second = run(Files.createDirectory(output.resolve("run2")), stars);
        if (!first.equals(second) || !inputHash.equals(sha(input))) { throw new IllegalStateException("재현 결과 또는 입력이 달라요."); }
        write(output.resolve("verification.txt"), "two_runs_identical=true\ninput_unchanged=true\nstar_rows=189000\n"
                + "unique_event_ids=94500\nmetrics_rows=120\nstatic_frames=30\ntimelines=6\ngifs=6\nlegends=1\n"
                + "clipped_points=0\ncohort_at_24h_empty=true\ncohort_at_12h_ab_pixels_equal=true\n"
                + "files_per_run=" + first.size() + "\n");
        System.out.println("E008 complete: two identical runs; 30 frames, 6 timelines, 6 GIFs, legend; " + output);
    }

    private static List<Star> read(Path input) throws Exception {
        List<Star> stars = new ArrayList<>();
        Map<String, Star> pairs = new HashMap<>();
        var ids = new java.util.HashSet<String>();
        try (var reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(input)), StandardCharsets.UTF_8))) {
            if (!"scene,model,seed,index,true_x,true_y,grid_x,grid_y,shown_x,shown_y,error_m".equals(reader.readLine())) {
                throw new IllegalArgumentException("E007 CSV header가 달라요.");
            }
            for (String line; (line = reader.readLine()) != null;) {
                String[] row = line.split(",", -1);
                if (row.length != 11) { throw new IllegalArgumentException("잘못된 CSV 행이에요."); }
                if (!List.of(SCENES).contains(row[0]) || !(row[1].equals("D") || row[1].equals("G"))) { continue; }
                Star star = Star.of(row);
                if (!Double.isFinite(star.x) || !Double.isFinite(star.y) || !ids.add(star.identity() + "|" + star.model)) {
                    throw new IllegalStateException("중복 또는 유한하지 않은 입력이에요.");
                }
                Star prior = pairs.putIfAbsent(star.identity(), star);
                if (prior != null && (prior.tx != star.tx || prior.ty != star.ty || prior.birth != star.birth)) {
                    throw new IllegalStateException("D/G의 실제 좌표·생성 시간이 달라요.");
                }
                stars.add(star);
            }
        }
        if (stars.size() != 189000 || pairs.size() != 94500) { throw new IllegalStateException("입력 수가 달라요."); }
        return stars;
    }

    private static Map<String, String> run(Path directory, List<Star> stars) throws Exception {
        try (var csv = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(
                Files.newOutputStream(directory.resolve("stars.csv.gz"), StandardOpenOption.CREATE_NEW)), StandardCharsets.UTF_8))) {
            csv.write("scene,model,seed,index,true_x,true_y,x,y,cohort_created_offset_hours,steady_created_offset_hours\n");
            for (Star s : stars) {
                csv.write(s.scene + "," + s.model + "," + s.seed + "," + s.index + "," + s.tx + "," + s.ty
                        + "," + s.x + "," + s.y + ",0.0," + s.birth + "\n");
            }
        }
        StringBuilder metrics = new StringBuilder("scene,schedule,hour,model,style,total,unborn,eligible,expired,alpha_sum,mean_alpha,alpha_ge_half\n");
        byte[] sprite = E001Panel.sprite();
        for (String scene : SCENES) {
            List<Star> subset = stars.stream().filter(s -> s.scene.equals(scene)).toList();
            for (String schedule : List.of("cohort", "steady")) {
                List<BufferedImage> frames = new ArrayList<>();
                String prefix = scene + "-" + schedule;
                for (int hour : HOURS) {
                    BufferedImage frame = frame(scene, schedule, hour, subset, sprite, metrics);
                    writePng(directory.resolve(prefix + "-h" + String.format(Locale.ROOT, "%02d", hour) + ".png"), frame);
                    frames.add(frame);
                }
                timeline(directory.resolve(prefix + "-timeline.png"), frames, prefix);
                gif(directory.resolve(prefix + ".gif"), frames);
            }
        }
        legend(directory.resolve("age-legend.png"));
        write(directory.resolve("metrics.csv"), metrics.toString());
        Map<String, String> hashes = new TreeMap<>();
        try (var paths = Files.list(directory)) {
            for (Path p : paths.toList()) { hashes.put(p.getFileName().toString(), sha(p)); }
        }
        StringBuilder manifest = new StringBuilder();
        hashes.forEach((p, h) -> manifest.append(h).append("  ").append(p).append('\n'));
        write(directory.resolve("checksums.sha256"), manifest.toString());
        return hashes;
    }

    private static BufferedImage frame(String scene, String schedule, int hour, List<Star> stars, byte[] sprite, StringBuilder metrics) {
        BufferedImage image = canvas(WIDTH, HEIGHT);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        Graphics2D g = graphics(image);
        label(g, "E008 | " + scene + " | " + schedule + " | t = " + hour + "h", 70, 44, 30);
        label(g, "24h experimental lifetime | same coordinates, births, alpha and 6px stars in A/B", 70, 82, 23);
        label(g, "Rows: D = grid-based / G = actual-location-based | Columns: A = yellow / B = age colour", 70, 115, 22);
        label(g, "Synthetic local coordinates | x/y +/-1200m | no real map, zoom or server expiry implementation", 70, 145, 21);
        for (int row = 0; row < 2; row++) {
            String model = row == 0 ? "D" : "G";
            List<Star> selected = stars.stream().filter(s -> s.model.equals(model)).toList();
            for (int col = 0; col < 2; col++) {
                int left = 70 + col * 760, top = 220 + row * 840;
                int unborn = 0, eligible = 0, expired = 0, bright = 0;
                double sum = 0;
                for (Star star : selected) {
                    double age = hour - (schedule.equals("cohort") ? 0 : star.birth);
                    if (age < 0) { unborn++; continue; }
                    if (age >= 24) { expired++; continue; }
                    eligible++;
                    double opacity = alpha(age);
                    sum += opacity;
                    if (opacity >= .5) { bright++; }
                    int px = left + (int) StrictMath.round((star.x + 1200) * PANEL / 2400);
                    int py = top + (int) StrictMath.round((1200 - star.y) * PANEL / 2400);
                    if (px - 3 < left || px + 3 >= left + PANEL || py - 3 < top || py + 3 >= top + PANEL) {
                        throw new IllegalStateException("그림에서 별이 잘려요.");
                    }
                    paint(pixels, WIDTH, px, py, color(col == 1, age), opacity, sprite);
                }
                String style = col == 0 ? "A" : "B";
                label(g, model + "-" + style + " | " + (col == 0 ? "Yellow + fade" : "Blue > yellow > red + fade"), left, top - 42, 26);
                label(g, "In lifetime: " + eligible + " | alpha sum: " + String.format(Locale.ROOT, "%.1f", sum), left, top - 12, 22);
                g.setColor(new Color(0x707782)); g.drawRect(left, top, PANEL, PANEL);
                for (int tick = -1; tick <= 1; tick++) {
                    label(g, Integer.toString(tick * 1200), left + (tick + 1) * PANEL / 2 - 22, top + PANEL + 27, 20);
                }
                label(g, "x (m); y: +1200 top / -1200 bottom", left + 140, top + PANEL + 53, 20);
                metrics.append(scene).append(',').append(schedule).append(',').append(hour).append(',').append(model).append(',').append(style)
                        .append(',').append(selected.size()).append(',').append(unborn).append(',').append(eligible).append(',').append(expired)
                        .append(',').append(sum).append(',').append(eligible == 0 ? 0 : sum / eligible).append(',').append(bright).append('\n');
                if (schedule.equals("cohort") && hour == 24 && eligible != 0) { throw new IllegalStateException("24h cohort가 남았어요."); }
            }
        }
        if (schedule.equals("cohort") && hour == 12) {
            for (int row = 0; row < 2; row++) {
                for (int y = 1; y < PANEL; y++) {
                    for (int x = 1; x < PANEL; x++) {
                        if (image.getRGB(70 + x, 220 + row * 840 + y) != image.getRGB(830 + x, 220 + row * 840 + y)) {
                            throw new IllegalStateException("12h 동일 색상인데 A/B pixel이 달라요.");
                        }
                    }
                }
            }
        }
        g.dispose();
        return image;
    }

    private static void paint(int[] pixels, int width, int px, int py, int color, double opacity, byte[] sprite) {
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 6; x++) {
                int a = (int) StrictMath.round(Byte.toUnsignedInt(sprite[((y * 8 / 6) * 8 + x * 8 / 6) * 4 + 3]) * opacity);
                if (a == 0) { continue; }
                int index = (py - 3 + y) * width + px - 3 + x;
                int dest = pixels[index], rgb = 0;
                for (int shift = 16; shift >= 0; shift -= 8) {
                    rgb |= ((((color >> shift) & 255) * a + ((dest >> shift) & 255) * (255 - a) + 127) / 255) << shift;
                }
                pixels[index] = rgb;
            }
        }
    }

    private static BufferedImage canvas(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Arrays.fill(((DataBufferInt) image.getRaster().getDataBuffer()).getData(), E001Panel.BACKGROUND);
        return image;
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    private static void label(Graphics2D g, String text, int x, int y, int size) {
        g.setColor(Color.WHITE); g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size)); g.drawString(text, x, y);
    }

    private static void timeline(Path path, List<BufferedImage> frames, String title) throws Exception {
        BufferedImage image = canvas(2000, 560);
        Graphics2D g = graphics(image);
        label(g, "E008 | " + title + " | fixed scale; 0 / 6 / 12 / 18 / 24 hours", 24, 35, 27);
        for (int i = 0; i < frames.size(); i++) {
            label(g, "t = " + HOURS[i] + "h", i * 400 + 150, 72, 24);
            g.drawImage(frames.get(i), i * 400, 83, 400, 465, null);
        }
        g.dispose(); writePng(path, image);
    }

    private static void gif(Path path, List<BufferedImage> frames) throws Exception {
        var writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (var output = ImageIO.createImageOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW))) {
            writer.setOutput(output); writer.prepareWriteSequence(null);
            for (int i = 0; i < frames.size(); i++) {
                BufferedImage image = frames.get(i);
                var metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), writer.getDefaultWriteParam());
                String format = metadata.getNativeMetadataFormatName();
                var root = (IIOMetadataNode) metadata.getAsTree(format);
                var control = (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);
                control.setAttribute("disposalMethod", "doNotDispose"); control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE"); control.setAttribute("delayTime", "120");
                control.setAttribute("transparentColorIndex", "0");
                if (i == 0) {
                    var extensions = new IIOMetadataNode("ApplicationExtensions");
                    var extension = new IIOMetadataNode("ApplicationExtension");
                    extension.setAttribute("applicationID", "NETSCAPE"); extension.setAttribute("authenticationCode", "2.0");
                    extension.setUserObject(new byte[]{1, 0, 0}); extensions.appendChild(extension); root.appendChild(extensions);
                }
                metadata.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(image, null, metadata), writer.getDefaultWriteParam());
            }
            writer.endWriteSequence();
        } finally { writer.dispose(); }
    }

    private static void legend(Path path) throws Exception {
        BufferedImage image = canvas(1600, 420);
        Graphics2D g = graphics(image);
        label(g, "E008 | Age palette and opacity | 24h is an experiment assumption", 45, 45, 28);
        label(g, "First 2h: full opacity. Then smooth fade. At age >=24h: excluded, not just recoloured.", 45, 85, 24);
        int[] ages = {0, 6, 12, 18, 23, 24};
        byte[] sprite = E001Panel.sprite();
        for (int i = 0; i < ages.length; i++) {
            int x = 145 + i * 250;
            label(g, ages[i] + "h", x - 25, 135, 25);
            for (int row = 0; row < 2; row++) {
                BufferedImage icon = canvas(12, 12);
                paint(((DataBufferInt) icon.getRaster().getDataBuffer()).getData(), 12, 6, 6, color(row == 1, ages[i]), alpha(ages[i]), sprite);
                g.drawImage(icon, x - 40, 155 + row * 95, 80, 80, null);
            }
            label(g, "alpha " + String.format(Locale.ROOT, "%.3f", alpha(ages[i])), x - 72, 381, 21);
        }
        label(g, "A", 35, 202, 26); label(g, "B", 35, 297, 26);
        g.dispose(); writePng(path, image);
    }

    private static void writePng(Path path, BufferedImage image) throws Exception {
        try (var output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
            if (!ImageIO.write(image, "png", output)) { throw new IllegalStateException("PNG writer가 없어요."); }
        }
    }

    private static String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static void write(Path path, String text) throws Exception {
        Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
}
