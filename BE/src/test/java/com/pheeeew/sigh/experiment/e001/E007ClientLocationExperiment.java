package com.pheeeew.sigh.experiment.e001;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;
import javax.imageio.ImageIO;

/** 합성 위치만 사용하는 탐색 도구예요. 배포용 위치 보호 구현이 아니에요. */
public final class E007ClientLocationExperiment {

    static final double D_M2 = kernelMoment(30_000);
    static final double TARGET_M2 = D_M2 + 15_000.0;
    static final double GAUSSIAN_SIGMA = StrictMath.sqrt(TARGET_M2 / 2);
    static final double LAPLACE_B = StrictMath.sqrt(TARGET_M2 / 6);
    private static final int[] RELEASES = {1, 5, 10, 30, 100, 300, 1000};
    private static final String[] SCENES = {"stationary-5000", "uniform-45000", "hotspots-4500", "hotspots-45000"};
    private static final int[] COUNTS = {5000, 45000, 4500, 45000};
    private static final int PANEL = 1000;
    private static final int MARGIN = 66;
    private static final int TOP = 160;

    enum Model { D, G, L }

    record XY(double x, double y) {
        static XY of(double x, double y) { return new XY(x, y); }
        double radius() { return StrictMath.hypot(x, y); }
    }

    record Point(long seed, int index, XY truth, XY shown) {
        static Point of(long seed, int index, XY truth, XY shown) { return new Point(seed, index, truth, shown); }
        double error() { return StrictMath.hypot(shown.x - truth.x, shown.y - truth.y); }
    }

    private E007ClientLocationExperiment() {
    }

    static double snap(double value) {
        return StrictMath.floor((value + 150) / 300) * 300;
    }

    static double kernelMoment(int steps) {
        if (steps <= 0 || steps % 2 != 0) { throw new IllegalArgumentException("짝수 구간이 필요해요."); }
        double mass = 0, moment = 0;
        for (int i = 0; i <= steps; i++) {
            double r = 300.0 * i / steps;
            double w = r * StrictMath.exp(-r * r / (2 * 120 * 120)) * E001DistanceSampler.taper(r / 300);
            int factor = i == 0 || i == steps ? 1 : (i % 2 == 0 ? 2 : 4);
            mass += factor * w;
            moment += factor * w * r * r;
        }
        return moment / mass;
    }

    static long seed(String identity) {
        try {
            return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8))).getLong();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    static XY gaussian(SplittableRandom random, double sigma) {
        double r = sigma * StrictMath.sqrt(-2 * StrictMath.log1p(-random.nextDouble()));
        double theta = 2 * StrictMath.PI * random.nextDouble();
        return XY.of(r * StrictMath.cos(theta), r * StrictMath.sin(theta));
    }

    static XY display(Model model, XY truth, SplittableRandom random) {
        XY noise;
        if (model == Model.D) {
            var result = E001DistanceSampler.sampleTaperedGaussian(random::nextDouble, 120);
            if (!(result instanceof E001SamplingResult.Success success)) {
                throw new IllegalStateException("D 실패 표본을 숨기지 않아요.");
            }
            return XY.of(snap(truth.x) + success.offset().eastingMeters(),
                    snap(truth.y) + success.offset().northingMeters());
        } else if (model == Model.G) {
            noise = gaussian(random, GAUSSIAN_SIGMA);
        } else {
            double r = -LAPLACE_B * (StrictMath.log1p(-random.nextDouble()) + StrictMath.log1p(-random.nextDouble()));
            double theta = 2 * StrictMath.PI * random.nextDouble();
            noise = XY.of(r * StrictMath.cos(theta), r * StrictMath.sin(theta));
        }
        return XY.of(truth.x + noise.x, truth.y + noise.y);
    }

    private static XY input(int scene, SplittableRandom random) {
        if (scene == 0) { return XY.of(120, -90); }
        if (scene == 1) { return XY.of(900 * random.nextDouble() - 450, 900 * random.nextDouble() - 450); }
        double[][] centers = {{-330, -210}, {-180, 280}, {80, -80}, {340, 190}, {260, -320}};
        double u = random.nextDouble();
        int index = u < .30 ? 0 : u < .50 ? 1 : u < .75 ? 2 : u < .90 ? 3 : 4;
        XY noise = gaussian(random, 55);
        return XY.of(centers[index][0] + noise.x, centers[index][1] + noise.y);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) { throw new IllegalArgumentException("새 출력 디렉터리가 필요해요."); }
        Path output = Path.of(args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.createDirectory(output);
        Map<String, String> first = run(Files.createDirectory(output.resolve("run1")));
        Map<String, String> second = run(Files.createDirectory(output.resolve("run2")));
        if (!first.equals(second)) { throw new IllegalStateException("두 실행 checksum이 달라요."); }
        write(output.resolve("verification.txt"), "two_runs_identical=true\nvisual_points_per_run=298500\n"
                + "attack_releases_per_run=3000000\nattack_prefix_rows_per_run=21000\n"
                + "sampler_failures=0\nclipped_points=0\nchecksum_files_per_run=" + first.size() + "\n");
        System.out.println("E007 complete: two identical runs, 298500 visual points + 3000000 attack releases/run; " + output);
    }

    private static Map<String, String> run(Path output) throws Exception {
        List<List<List<Point>>> all = new ArrayList<>();
        double max = 0;
        StringBuilder metrics = new StringBuilder("scene,model,seed,n,rms_m,p50_m,p95_m,p99_m,max_m,outside300_fraction,period300_amplitude\n");
        try (var csv = gzip(output.resolve("coordinates.csv.gz"))) {
            csv.write("scene,model,seed,index,true_x,true_y,grid_x,grid_y,shown_x,shown_y,error_m\n");
            for (int scene = 0; scene < SCENES.length; scene++) {
                List<List<Point>> panels = new ArrayList<>();
                for (Model model : Model.values()) {
                    List<Point> pool = new ArrayList<>();
                    for (long s = 2026090701L; s <= 2026090705L; s++) {
                        SplittableRandom inputs = new SplittableRandom(seed("e007|input|" + scene + "|" + s));
                        SplittableRandom noise = new SplittableRandom(seed("e007|noise|" + scene + "|" + s + "|" + model));
                        List<Point> points = new ArrayList<>();
                        for (int i = 0; i < COUNTS[scene] / 5; i++) {
                            XY truth = input(scene, inputs), shown = display(model, truth, noise);
                            if (!Double.isFinite(shown.x) || !Double.isFinite(shown.y)) { throw new IllegalStateException("유한 좌표가 아니에요."); }
                            max = StrictMath.max(max, StrictMath.max(StrictMath.abs(shown.x), StrictMath.abs(shown.y)));
                            Point p = Point.of(s, i, truth, shown);
                            points.add(p);
                            csv.write(SCENES[scene] + "," + model + "," + s + "," + i + "," + truth.x + "," + truth.y
                                    + "," + snap(truth.x) + "," + snap(truth.y) + "," + shown.x + "," + shown.y + "," + p.error() + "\n");
                        }
                        metrics.append(measure(SCENES[scene], model, Long.toString(s), points));
                        pool.addAll(points);
                    }
                    metrics.append(measure(SCENES[scene], model, "pooled", pool));
                    panels.add(pool);
                }
                all.add(panels);
            }
        }
        double bound = StrictMath.max(900, StrictMath.ceil((max + 20) / 300) * 300);
        write(output.resolve("parameters.txt"), "d_kernel_m2=" + D_M2 + "\ntarget_m2=" + TARGET_M2
                + "\ntarget_rms_m=" + StrictMath.sqrt(TARGET_M2) + "\ngaussian_sigma_m=" + GAUSSIAN_SIGMA
                + "\nlaplace_b_m=" + LAPLACE_B + "\nideal_laplace_epsilon_per_m=" + 1 / LAPLACE_B
                + "\ncommon_plot_bound_m=" + bound + "\nmax_abs_shown_coordinate_m=" + max + "\n");
        write(output.resolve("metrics.csv"), metrics.toString());
        for (int scene = 0; scene < SCENES.length; scene++) {
            scatter(output.resolve(SCENES[scene] + ".png"), SCENES[scene], all.get(scene), bound);
        }
        attack(output);
        Map<String, String> hashes = new TreeMap<>();
        try (var paths = Files.list(output)) {
            for (Path p : paths.toList()) {
                hashes.put(p.getFileName().toString(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))));
            }
        }
        StringBuilder manifest = new StringBuilder();
        hashes.forEach((p, h) -> manifest.append(h).append("  ").append(p).append('\n'));
        write(output.resolve("checksums.sha256"), manifest.toString());
        return hashes;
    }

    private static String measure(String scene, Model model, String seed, List<Point> points) {
        double[] errors = points.stream().mapToDouble(Point::error).toArray();
        double m2 = 0, cx = 0, sx = 0, cy = 0, sy = 0;
        long outside = 0;
        for (Point p : points) {
            m2 += p.error() * p.error();
            if (p.error() > 300) { outside++; }
            double ax = 2 * StrictMath.PI * p.shown.x / 300, ay = 2 * StrictMath.PI * p.shown.y / 300;
            cx += StrictMath.cos(ax); sx += StrictMath.sin(ax);
            cy += StrictMath.cos(ay); sy += StrictMath.sin(ay);
        }
        double n = points.size();
        return scene + "," + model + "," + seed + "," + points.size() + "," + StrictMath.sqrt(m2 / n)
                + "," + E001Statistics.quantile(errors, .5) + "," + E001Statistics.quantile(errors, .95)
                + "," + E001Statistics.quantile(errors, .99) + "," + E001Statistics.quantile(errors, 1)
                + "," + outside / n + "," + (StrictMath.hypot(cx, sx) + StrictMath.hypot(cy, sy)) / (2 * n) + "\n";
    }

    private static void attack(Path output) throws Exception {
        double[][][] curves = new double[2][3][RELEASES.length];
        StringBuilder metrics = new StringBuilder("anchor,model,n,trajectories,rms_m,p50_m,p95_m,within25_fraction,theoretical_rms_m\n");
        try (var csv = gzip(output.resolve("attack-prefixes.csv.gz"))) {
            csv.write("anchor,model,seed,trajectory,n,true_x,true_y,mean_x,mean_y,error_m\n");
            for (int anchor = 0; anchor < 2; anchor++) {
                XY truth = anchor == 0 ? XY.of(120, -90) : XY.of(0, 0);
                for (Model model : Model.values()) {
                    List<List<Double>> errors = new ArrayList<>();
                    for (int n : RELEASES) { errors.add(new ArrayList<>()); }
                    for (long s = 2026090701L; s <= 2026090705L; s++) {
                        for (int trajectory = 0; trajectory < 100; trajectory++) {
                            SplittableRandom rng = new SplittableRandom(seed("e007|attack|" + anchor + "|" + model + "|" + s + "|" + trajectory));
                            double x = 0, y = 0;
                            int checkpoint = 0;
                            for (int n = 1; n <= 1000; n++) {
                                XY shown = display(model, truth, rng);
                                x += shown.x; y += shown.y;
                                if (n == RELEASES[checkpoint]) {
                                    double error = StrictMath.hypot(x / n - truth.x, y / n - truth.y);
                                    errors.get(checkpoint).add(error);
                                    csv.write(anchor + "," + model + "," + s + "," + trajectory + "," + n + ","
                                            + truth.x + "," + truth.y + "," + x / n + "," + y / n + "," + error + "\n");
                                    checkpoint++;
                                }
                            }
                        }
                    }
                    for (int k = 0; k < RELEASES.length; k++) {
                        double[] values = errors.get(k).stream().mapToDouble(Double::doubleValue).toArray();
                        double squared = 0; int within = 0;
                        for (double e : values) { squared += e * e; if (e <= 25) { within++; } }
                        double rms = StrictMath.sqrt(squared / values.length);
                        curves[anchor][model.ordinal()][k] = rms;
                        double theory = StrictMath.sqrt(model == Model.D ? (anchor == 0 ? 22500 : 0) + D_M2 / RELEASES[k] : TARGET_M2 / RELEASES[k]);
                        if (StrictMath.abs(rms / theory - 1) > .15) { throw new IllegalStateException("평균 공격 RMS가 이론과 15% 넘게 달라요."); }
                        metrics.append(anchor).append(',').append(model).append(',').append(RELEASES[k]).append(',').append(values.length)
                                .append(',').append(rms).append(',').append(E001Statistics.quantile(values, .5))
                                .append(',').append(E001Statistics.quantile(values, .95)).append(',').append(within / (double) values.length)
                                .append(',').append(theory).append('\n');
                    }
                }
            }
        }
        write(output.resolve("attack-metrics.csv"), metrics.toString());
        attackPlot(output.resolve("repeated-observation.png"), curves);
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(E001Panel.BACKGROUND)); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    private static void label(Graphics2D g, String text, int x, int y, int size) {
        g.setColor(Color.WHITE); g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size)); g.drawString(text, x, y);
    }

    private static void scatter(Path output, String scene, List<List<Point>> panels, double bound) throws Exception {
        BufferedImage image = new BufferedImage(3264, 1240, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(image);
        label(g, "E007 | " + scene + " | " + panels.getFirst().size() + " stars per panel", MARGIN, 42, 30);
        label(g, "Same synthetic inputs | five seeds pooled | x/y metres | common scale | 6px stars | no tail clipping", MARGIN, 84, 23);
        byte[] rgba = E001Panel.sprite();
        BufferedImage sprite = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 64; i++) {
            int p = i * 4;
            sprite.setRGB(i % 8, i / 8, Byte.toUnsignedInt(rgba[p + 3]) << 24 | Byte.toUnsignedInt(rgba[p]) << 16
                    | Byte.toUnsignedInt(rgba[p + 1]) << 8 | Byte.toUnsignedInt(rgba[p + 2]));
        }
        String[] titles = {"D | grid centre + tapered Gaussian", "G | no grid + Gaussian", "L | no grid + planar Laplace"};
        for (int col = 0; col < 3; col++) {
            int left = MARGIN + col * (PANEL + MARGIN);
            label(g, titles[col], left, TOP - 25, 25);
            for (Point p : panels.get(col)) {
                int x = left + (int) StrictMath.round((p.shown.x + bound) * PANEL / (2 * bound));
                int y = TOP + (int) StrictMath.round((bound - p.shown.y) * PANEL / (2 * bound));
                if (x - 3 < left || x + 3 >= left + PANEL || y - 3 < TOP || y + 3 >= TOP + PANEL) {
                    throw new IllegalStateException("표시 점이 잘려요.");
                }
                g.drawImage(sprite, x - 3, y - 3, 6, 6, null);
            }
            g.setColor(new Color(0x707782)); g.drawRect(left, TOP, PANEL, PANEL);
            for (int tick = -1; tick <= 1; tick++) {
                label(g, Integer.toString((int) (tick * bound)), left + (tick + 1) * PANEL / 2 - 20, TOP + PANEL + 30, 22);
            }
            label(g, "x (m); y: +" + (int) bound + " top / -" + (int) bound + " bottom", left + 240, TOP + PANEL + 62, 22);
        }
        g.dispose(); ImageIO.write(image, "png", output.toFile());
    }

    private static void attackPlot(Path output, double[][][] curves) throws Exception {
        BufferedImage image = new BufferedImage(2100, 860, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(image);
        label(g, "E007 | Repeated-location estimation by averaging released coordinates", 70, 45, 30);
        label(g, "500 trajectories per model/anchor | lower error = more accurate estimate | not a privacy guarantee", 70, 85, 23);
        Color[] colors = {Color.WHITE, new Color(0xEAC76B), new Color(0x82B4DC)};
        String[] legends = {"D: grid", "G: Gaussian", "L: Laplace"};
        for (int m = 0; m < 3; m++) {
            g.setColor(colors[m]); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 23)); g.drawString(legends[m], 70 + 330 * m, 125);
        }
        double upper = 0;
        for (double[][] panel : curves) { for (double[] line : panel) { for (double value : line) { upper = Math.max(upper, value); } } }
        upper = StrictMath.ceil(upper / 50) * 50;
        for (int panel = 0; panel < 2; panel++) {
            int left = 100 + panel * 1030, top = 220, width = 820, height = 510;
            label(g, panel == 0 ? "Truth (120,-90): 150m from grid centre" : "Truth (0,0): exactly at grid centre", left, 181, 25);
            for (int tick = 0; tick <= (int) upper; tick += 50) {
                int y = top + height - (int) (tick / upper * height);
                g.setColor(new Color(0x414650)); g.setStroke(new BasicStroke(1)); g.drawLine(left, y, left + width, y);
                label(g, Integer.toString(tick), left - 55, y + 8, 21);
            }
            label(g, "RMS estimation error (m)", left, top - 13, 20);
            for (int n : RELEASES) {
                int x = left + (int) (StrictMath.log10(n) / 3 * width);
                label(g, Integer.toString(n), x - 15, top + height + 30, 20);
            }
            label(g, "Linked releases from one fixed location (log scale)", left + 90, top + height + 75, 23);
            for (int m = 0; m < 3; m++) {
                g.setColor(colors[m]);
                g.setStroke(m == 0 ? new BasicStroke(3) : new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, m == 1 ? new float[]{12, 7} : new float[]{3, 6}, 0));
                int px = 0, py = 0;
                for (int k = 0; k < RELEASES.length; k++) {
                    int x = left + (int) (StrictMath.log10(RELEASES[k]) / 3 * width);
                    int y = top + height - (int) (curves[panel][m][k] / upper * height);
                    if (k > 0) { g.drawLine(px, py, x, y); }
                    if (m == 0) { g.fillOval(x - 5, y - 5, 10, 10); }
                    else if (m == 1) { g.drawRect(x - 5, y - 5, 10, 10); }
                    else { g.drawPolygon(new int[]{x, x - 6, x + 6}, new int[]{y - 6, y + 5, y + 5}, 3); }
                    px = x; py = y;
                }
            }
        }
        g.dispose(); ImageIO.write(image, "png", output.toFile());
    }

    private static BufferedWriter gzip(Path path) throws Exception {
        return new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)), StandardCharsets.UTF_8));
    }

    private static void write(Path path, String text) throws Exception {
        Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
}
