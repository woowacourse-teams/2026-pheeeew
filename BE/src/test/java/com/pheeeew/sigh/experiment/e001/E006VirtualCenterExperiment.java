package com.pheeeew.sigh.experiment.e001;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;
import javax.imageio.ImageIO;

/** E001 원본을 수정하지 않는 가상 중심 탐색 도구예요. 제품 코드에서 사용하지 않아요. */
public final class E006VirtualCenterExperiment {

    private static final int PANEL = 1_200;
    private static final int MARGIN = 64;
    private static final int TOP = 150;
    private static final long FIRST_SEED = 2026090701L;
    private static final double[] WEIGHTS = {3, 1, 1, 0.5, 1.5, 0.5, 0.5, 0.5, 0.5};

    enum Model { D, V, V300 }

    record Draw(E001Offset offset, int proposals) {
        static Draw of(E001Offset offset, int proposals) { return new Draw(offset, proposals); }
    }

    record Point(long seed, int center, int index, double cx, double cy, Draw draw) {
        static Point of(long seed, int center, int index, double cx, double cy, Draw draw) {
            return new Point(seed, center, index, cx, cy, draw);
        }
        double x() { return cx + draw.offset().eastingMeters(); }
        double y() { return cy + draw.offset().northingMeters(); }
        double radius() { return draw.offset().radiusMeters(); }
    }

    private E006VirtualCenterExperiment() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("새 결과 디렉터리 한 개를 지정해요.");
        }
        Path output = Path.of(args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.createDirectory(output);
        Map<String, String> first = run(Files.createDirectory(output.resolve("run1")));
        Map<String, String> second = run(Files.createDirectory(output.resolve("run2")));
        if (!first.equals(second)) {
            throw new IllegalStateException("두 실행의 좌표·지표·그림 checksum이 달라요.");
        }
        write(output.resolve("verification.txt"), "two_runs_identical=true\npoints_per_run=313500\n"
                + "sampler_failures=0\nclipped_points=0\nchecksum_files_per_run=" + first.size() + "\n");
        System.out.println("E006: 313500 points/run; two identical runs; failures=0; clipped=0; " + output);
    }

    static Draw sample(Model model, long seed) {
        SplittableRandom gaussian = new SplittableRandom(seed);
        SplittableRandom jitter = new SplittableRandom(seed ^ 0x63d83595b5c48e13L);
        SplittableRandom accept = new SplittableRandom(seed ^ 0x91e10da5c79e7b1dL);
        Supplier<E001Offset> proposal = () -> {
            E001SamplingResult sampled = E001DistanceSampler.sampleTaperedGaussian(gaussian::nextDouble, 120.0);
            if (!(sampled instanceof E001SamplingResult.Success success)) {
                throw new IllegalStateException("D sampler 실패를 숨기지 않아요.");
            }
            if (model == Model.D) {
                return success.offset();
            }
            return combine(success.offset(), 300.0 * jitter.nextDouble() - 150.0,
                    300.0 * jitter.nextDouble() - 150.0);
        };
        return model == Model.V300 ? constrain(proposal, accept::nextDouble, 4096) : Draw.of(proposal.get(), 1);
    }

    static E001Offset combine(E001Offset offset, double ux, double uy) {
        double x = offset.eastingMeters() + ux;
        double y = offset.northingMeters() + uy;
        return E001Offset.of(x, y, StrictMath.hypot(x, y));
    }

    static Draw constrain(Supplier<E001Offset> proposals, E001UniformRandom random, int limit) {
        for (int attempt = 1; attempt <= limit; attempt++) {
            E001Offset point = proposals.get();
            double radius = StrictMath.hypot(point.eastingMeters(), point.northingMeters());
            if (!Double.isFinite(radius)) {
                throw new IllegalArgumentException("유한한 좌표만 평가해요.");
            }
            double probability = E001DistanceSampler.taper(radius / 300.0);
            if (random.nextDouble() < probability) {
                return Draw.of(point, attempt);
            }
        }
        throw new IllegalStateException("V300 재추첨 한도를 초과했어요.");
    }

    static long pointSeed(int layout, int count, long seed, int center, int index) {
        String identity = "e006|" + layout + "|" + count + "|" + seed + "|" + center + "|" + index;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(hash).getLong();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없어요.", exception);
        }
    }

    private static Map<String, String> run(Path directory) throws Exception {
        StringBuilder metrics = new StringBuilder("scenario,model,seed,count,p50_m,p95_m,p99_m,max_m,outside300_fraction,period300_amplitude,a4_raw,mean_proposals\n");
        long total = 0;
        try (BufferedWriter csv = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(
                Files.newOutputStream(directory.resolve("coordinates.csv.gz"), StandardOpenOption.CREATE_NEW)),
                StandardCharsets.UTF_8))) {
            csv.write("scenario,model,seed,center,index,center_x_m,center_y_m,x_m,y_m,radius_m,proposals\n");
            for (int layout = 0; layout < 3; layout++) {
                for (int count : new int[]{500, 5000}) {
                    String scenario = new String[]{"single", "grid-equal", "grid-imbalanced"}[layout] + "-" + count;
                    List<List<Point>> panels = new ArrayList<>();
                    for (Model model : Model.values()) {
                        List<Point> pool = new ArrayList<>();
                        for (long seed = FIRST_SEED; seed < FIRST_SEED + 5; seed++) {
                            List<Point> points = generate(layout, count, model, seed);
                            for (Point point : points) {
                                csv.write(scenario + "," + model + "," + seed + "," + point.center() + ","
                                        + point.index() + "," + point.cx() + "," + point.cy() + "," + point.x()
                                        + "," + point.y() + "," + point.radius() + "," + point.draw().proposals() + "\n");
                            }
                            metrics.append(measure(scenario, model, Long.toString(seed), points));
                            pool.addAll(points);
                        }
                        metrics.append(measure(scenario, model, "pooled", pool));
                        panels.add(pool);
                        total += pool.size();
                    }
                    render(directory.resolve(scenario + ".png"), scenario, panels);
                }
            }
        }
        if (total != 313_500) {
            throw new IllegalStateException("요청 표본 개수가 일치하지 않아요.");
        }
        write(directory.resolve("metrics.csv"), metrics.toString());
        Map<String, String> hashes = new TreeMap<>();
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                hashes.put(file.getFileName().toString(), sha(file));
            }
        }
        StringBuilder list = new StringBuilder();
        hashes.forEach((path, checksum) -> list.append(checksum).append("  ").append(path).append('\n'));
        write(directory.resolve("checksums.sha256"), list.toString());
        return hashes;
    }

    private static List<Point> generate(int layout, int count, Model model, long seed) {
        List<Point> points = new ArrayList<>();
        int centers = layout == 0 ? 1 : 9;
        for (int center = 0; center < centers; center++) {
            double x = layout == 0 ? 0 : (center % 3 - 1) * 300.0;
            double y = layout == 0 ? 0 : (center / 3 - 1) * 300.0;
            int perSeed = (int) (count / 5.0 * (layout == 2 ? WEIGHTS[center] : 1.0));
            for (int index = 0; index < perSeed; index++) {
                long pointSeed = pointSeed(layout, count, seed, center, index);
                Draw draw = sample(model, pointSeed);
                double limit = model == Model.V ? 300.0 + 150.0 * StrictMath.sqrt(2.0) : 300.0;
                if (!Double.isFinite(draw.offset().radiusMeters()) || draw.offset().radiusMeters() >= limit) {
                    throw new IllegalStateException("모델의 지지영역을 벗어났어요.");
                }
                points.add(Point.of(seed, center, index, x, y, draw));
            }
        }
        return points;
    }

    private static String measure(String scenario, Model model, String seed, List<Point> points) {
        double[] radii = points.stream().mapToDouble(Point::radius).toArray();
        double cx = 0, sx = 0, cy = 0, sy = 0, c4 = 0, s4 = 0, proposals = 0;
        long outside = 0;
        for (Point p : points) {
            double ax = 2.0 * StrictMath.PI * p.x() / 300.0;
            double ay = 2.0 * StrictMath.PI * p.y() / 300.0;
            cx += StrictMath.cos(ax); sx += StrictMath.sin(ax);
            cy += StrictMath.cos(ay); sy += StrictMath.sin(ay);
            double a = 4.0 * StrictMath.atan2(p.draw().offset().northingMeters(), p.draw().offset().eastingMeters());
            c4 += StrictMath.cos(a); s4 += StrictMath.sin(a);
            proposals += p.draw().proposals();
            if (p.radius() >= 300.0) { outside++; }
        }
        double n = points.size();
        double period = (StrictMath.hypot(cx, sx) + StrictMath.hypot(cy, sy)) / (2.0 * n);
        return scenario + "," + model + "," + seed + "," + points.size() + ","
                + E001Statistics.quantile(radii, .50) + "," + E001Statistics.quantile(radii, .95) + ","
                + E001Statistics.quantile(radii, .99) + "," + E001Statistics.quantile(radii, 1.0) + ","
                + outside / n + "," + period + "," + StrictMath.hypot(c4, s4) / n + "," + proposals / n + "\n";
    }

    private static void render(Path path, String scenario, List<List<Point>> panels) throws IOException {
        int step = PANEL + MARGIN;
        BufferedImage image = new BufferedImage(step * 3 + MARGIN, PANEL + TOP + 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(E001Panel.BACKGROUND)); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
        g.drawString("E006 | " + scenario + " | " + panels.getFirst().size() + " points per panel", MARGIN, 42);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));
        g.drawString("Five seeds pooled | x/y: metres | same scale [-900, +900] | 6px stars | synthetic locations", MARGIN, 80);
        BufferedImage sprite = sprite();
        String[] titles = {"D  |  tapered Gaussian, R < 300m", "V  |  virtual centre + D, R < 512.13m", "V300  |  V + boundary taper, R < 300m"};
        for (int col = 0; col < 3; col++) {
            int left = MARGIN + col * step;
            g.setColor(Color.WHITE); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
            g.drawString(titles[col], left, TOP - 22);
            for (Point p : panels.get(col)) {
                int px = left + (int) StrictMath.round((p.x() + 900.0) * PANEL / 1800.0);
                int py = TOP + (int) StrictMath.round((900.0 - p.y()) * PANEL / 1800.0);
                if (px - 3 < left || px + 3 >= left + PANEL || py - 3 < TOP || py + 3 >= TOP + PANEL) {
                    throw new IllegalStateException("그림에 잘리는 점이 있어요.");
                }
                g.drawImage(sprite, px - 3, py - 3, 6, 6, null);
            }
            g.setColor(new Color(0x707782)); g.drawRect(left, TOP, PANEL, PANEL);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 22));
            for (int tick = -900; tick <= 900; tick += 300) {
                int px = left + (int) ((tick + 900.0) * PANEL / 1800.0);
                g.drawLine(px, TOP + PANEL, px, TOP + PANEL + 8);
                g.drawString(Integer.toString(tick), px - 22, TOP + PANEL + 34);
            }
            g.drawString("x (m); y: +900 top / -900 bottom", left + 300, TOP + PANEL + 65);
        }
        g.dispose();
        if (!ImageIO.write(image, "png", path.toFile())) {
            throw new IOException("PNG writer가 없어요.");
        }
    }

    private static BufferedImage sprite() throws IOException {
        byte[] rgba = E001Panel.sprite();
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 64; i++) {
            int p = i * 4;
            image.setRGB(i % 8, i / 8, Byte.toUnsignedInt(rgba[p + 3]) << 24
                    | Byte.toUnsignedInt(rgba[p]) << 16 | Byte.toUnsignedInt(rgba[p + 1]) << 8
                    | Byte.toUnsignedInt(rgba[p + 2]));
        }
        return image;
    }

    private static void write(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private static String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
