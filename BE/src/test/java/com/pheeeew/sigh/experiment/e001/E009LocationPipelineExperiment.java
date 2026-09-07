package com.pheeeew.sigh.experiment.e001;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.imageio.ImageIO;

/** 전송·계산 위치를 합성 데이터로 모의해요. 제품의 위치 처리 계약은 바꾸지 않아요. */
public final class E009LocationPipelineExperiment {

    private static final List<String> SCENES = List.of("stationary-5000", "hotspots-4500", "hotspots-45000", "uniform-45000");
    private static final int PANEL = 1000;
    private static final int TOP = 200;
    private static final int MARGIN = 66;
    private static final double BOUND = 1200;

    record XY(double x, double y) {
        static XY of(double x, double y) {
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                throw new IllegalArgumentException("유한한 합성 좌표가 필요해요.");
            }
            return new XY(x, y);
        }
    }

    record Input(String scene, String seed, String index, XY truth, XY grid, XY existing) {
        static Input from(String[] row) {
            return new Input(row[0], row[2], row[3], XY.of(Double.parseDouble(row[4]), Double.parseDouble(row[5])),
                    XY.of(Double.parseDouble(row[6]), Double.parseDouble(row[7])),
                    XY.of(Double.parseDouble(row[8]), Double.parseDouble(row[9])));
        }

        String identity() { return scene + "|" + seed + "|" + index; }
    }

    record Route(XY sent, XY shown) {
        static Route of(XY sent, XY shown) { return new Route(sent, shown); }
    }

    private E009LocationPipelineExperiment() {
    }

    static XY disk(XY center, double u, double v) {
        if (!Double.isFinite(u) || !Double.isFinite(v) || u < 0 || u >= 1 || v < 0 || v >= 1) {
            throw new IllegalArgumentException("두 난수는 [0,1) 범위여야 해요.");
        }
        double radius = 300 * StrictMath.sqrt(u);
        double angle = 2 * StrictMath.PI * v;
        return XY.of(center.x + radius * StrictMath.cos(angle), center.y + radius * StrictMath.sin(angle));
    }

    static Route route(int mode, XY truth, XY grid, XY existing, double u, double v, double clientU, double clientV) {
        return switch (mode) {
            case 1 -> Route.of(truth, disk(truth, u, v));
            case 2 -> {
                XY clientResult = disk(truth, clientU, clientV);
                yield Route.of(clientResult, disk(clientResult, u, v));
            }
            case 3 -> Route.of(grid, existing);
            default -> throw new IllegalArgumentException("경로는 1/2/3이에요.");
        };
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) { throw new IllegalArgumentException("E007 입력과 새 결과 폴더를 지정해요."); }
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        String before = sha(input);
        List<Input> points = read(input);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.createDirectory(output);
        Map<String, String> first = run(Files.createDirectory(output.resolve("run1")), points);
        Map<String, String> second = run(Files.createDirectory(output.resolve("run2")), points);
        if (!first.equals(second) || !before.equals(sha(input))) { throw new IllegalStateException("입력 또는 재현 결과가 달라요."); }
        write(output.resolve("verification.txt"), "two_runs_identical=true\ninput_unchanged=true\nevents=99500\n"
                + "coordinate_rows=298500\nmetrics_rows=12\npngs=4\nfiles_per_run=6\n"
                + "mode1_mode2_server_offsets_equal=true\nmode2_client_and_server_radius300=true\nclipped_points=0\n");
        System.out.println("E009 complete: 298500 rows/run; two identical runs; 4 PNGs; mode 2 double randomisation.");
    }

    private static List<Input> read(Path path) throws Exception {
        List<Input> points = new ArrayList<>();
        var ids = new HashSet<String>();
        try (var reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(path)), StandardCharsets.UTF_8))) {
            if (!"scene,model,seed,index,true_x,true_y,grid_x,grid_y,shown_x,shown_y,error_m".equals(reader.readLine())) {
                throw new IllegalArgumentException("E007 header가 달라요.");
            }
            for (String line; (line = reader.readLine()) != null;) {
                String[] row = line.split(",", -1);
                if (row.length != 11) { throw new IllegalArgumentException("잘못된 CSV 행이에요."); }
                if (!SCENES.contains(row[0]) || !row[1].equals("D")) { continue; }
                Input point = Input.from(row);
                if (!ids.add(point.identity())) { throw new IllegalArgumentException("중복 이벤트예요."); }
                points.add(point);
            }
        }
        if (points.size() != 99500) { throw new IllegalStateException("입력 개수가 달라요."); }
        for (String scene : SCENES) {
            int expected = Integer.parseInt(scene.substring(scene.lastIndexOf('-') + 1));
            if (points.stream().filter(p -> p.scene.equals(scene)).count() != expected) {
                throw new IllegalStateException("장면별 개수가 달라요.");
            }
        }
        return points;
    }

    private static Map<String, String> run(Path output, List<Input> points) throws Exception {
        StringBuilder metrics = new StringBuilder("scene,mode,n,mean_error_m,rms_error_m,max_error_m,outside300_fraction\n");
        try (var csv = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(
                output.resolve("coordinates.csv.gz"), StandardOpenOption.CREATE_NEW)), StandardCharsets.UTF_8))) {
            csv.write("scene,mode,seed,index,true_x,true_y,sent_x,sent_y,shown_x,shown_y,age_hours,alpha,error_m\n");
            for (String scene : SCENES) {
                List<Input> subset = points.stream().filter(p -> p.scene.equals(scene)).toList();
                List<List<XY>> panels = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
                double[] sum = new double[3], squared = new double[3], max = new double[3];
                int[] outside = new int[3];
                for (Input point : subset) {
                    SplittableRandom random = random("e009|server|" + point.identity());
                    SplittableRandom clientRandom = random("e009|client|" + point.identity());
                    double u = random.nextDouble(), v = random.nextDouble();
                    double clientU = clientRandom.nextDouble(), clientV = clientRandom.nextDouble();
                    Route previous = null;
                    for (int mode = 1; mode <= 3; mode++) {
                        Route route = route(mode, point.truth, point.grid, point.existing, u, v, clientU, clientV);
                        if (mode == 2) {
                            double dx = (route.shown.x - route.sent.x) - (previous.shown.x - previous.sent.x);
                            double dy = (route.shown.y - route.sent.y) - (previous.shown.y - previous.sent.y);
                            if (StrictMath.hypot(dx, dy) > 1e-9) { throw new IllegalStateException("1/2 서버 오프셋이 달라요."); }
                            if (StrictMath.hypot(route.sent.x - point.truth.x, route.sent.y - point.truth.y) > 300 + 1e-9
                                    || StrictMath.hypot(route.shown.x - route.sent.x, route.shown.y - route.sent.y) > 300 + 1e-9) {
                                throw new IllegalStateException("클라이언트 또는 서버의 300m 범위를 벗어났어요.");
                            }
                        }
                        previous = route;
                        panels.get(mode - 1).add(route.shown);
                        double error = StrictMath.hypot(route.shown.x - point.truth.x, route.shown.y - point.truth.y);
                        if ((mode == 1 && error > 300 + 1e-9) || (mode == 2 && error > 600 + 1e-9)) {
                            throw new IllegalStateException("최종 원 범위를 벗어났어요.");
                        }
                        sum[mode - 1] += error; squared[mode - 1] += error * error;
                        max[mode - 1] = Math.max(max[mode - 1], error);
                        if (error > 300) { outside[mode - 1]++; }
                        csv.write(point.scene + "," + mode + "," + point.seed + "," + point.index + "," + point.truth.x + "," + point.truth.y
                                + "," + route.sent.x + "," + route.sent.y + "," + route.shown.x + "," + route.shown.y + ",0,1," + error + "\n");
                    }
                }
                for (int i = 0; i < 3; i++) {
                    metrics.append(scene).append(',').append(i + 1).append(',').append(subset.size()).append(',')
                            .append(sum[i] / subset.size()).append(',').append(StrictMath.sqrt(squared[i] / subset.size()))
                            .append(',').append(max[i]).append(',').append(outside[i] / (double) subset.size()).append('\n');
                }
                scatter(output.resolve(scene + ".png"), scene, panels);
            }
        }
        write(output.resolve("metrics.csv"), metrics.toString());
        Map<String, String> hashes = new TreeMap<>();
        try (var files = Files.list(output)) {
            for (Path path : files.toList()) { hashes.put(path.getFileName().toString(), sha(path)); }
        }
        StringBuilder manifest = new StringBuilder();
        hashes.forEach((name, hash) -> manifest.append(hash).append("  ").append(name).append('\n'));
        write(output.resolve("checksums.sha256"), manifest.toString());
        return hashes;
    }

    private static void scatter(Path path, String scene, List<List<XY>> panels) throws Exception {
        BufferedImage image = new BufferedImage(3264, 1280, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(E001Panel.BACKGROUND)); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        label(g, "E009 | " + scene + " | " + panels.getFirst().size() + " stars per panel", MARGIN, 42, 30);
        label(g, "Same synthetic positions | shared server draws in 1/2 | age 0h, alpha 1 | 6px stars | x/y +/-1200m", MARGIN, 80, 23);
        label(g, "1: one 300m disk | 2: client 300m + server 300m, up to 600m total | 3: D, grid + tapered Gaussian", MARGIN, 115, 23);
        label(g, "Simulation only: no real location transmission or product change. Spread distances are NOT matched.", MARGIN, 150, 23);
        byte[] rgba = E001Panel.sprite();
        BufferedImage sprite = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 64; i++) {
            int p = i * 4;
            sprite.setRGB(i % 8, i / 8, Byte.toUnsignedInt(rgba[p + 3]) << 24 | Byte.toUnsignedInt(rgba[p]) << 16
                    | Byte.toUnsignedInt(rgba[p + 1]) << 8 | Byte.toUnsignedInt(rgba[p + 2]));
        }
        String[] titles = {"1 | Actual location + server 300m disk", "2 | Client 300m disk + server 300m disk", "3 | Existing D: grid + tapered Gaussian"};
        for (int col = 0; col < 3; col++) {
            int left = MARGIN + col * (PANEL + MARGIN);
            label(g, titles[col], left, TOP - 15, 25);
            for (XY p : panels.get(col)) {
                int x = left + (int) StrictMath.round((p.x + BOUND) * PANEL / (2 * BOUND));
                int y = TOP + (int) StrictMath.round((BOUND - p.y) * PANEL / (2 * BOUND));
                if (x - 3 < left || x + 3 >= left + PANEL || y - 3 < TOP || y + 3 >= TOP + PANEL) {
                    throw new IllegalStateException("표시 점이 잘려요.");
                }
                g.drawImage(sprite, x - 3, y - 3, 6, 6, null);
            }
            g.setColor(new Color(0x707782)); g.drawRect(left, TOP, PANEL, PANEL);
            for (int tick = -1; tick <= 1; tick++) {
                label(g, Integer.toString((int) (tick * BOUND)), left + (tick + 1) * PANEL / 2 - 20, TOP + PANEL + 30, 22);
            }
            label(g, "x (m); y: +1200 top / -1200 bottom", left + 240, TOP + PANEL + 62, 22);
        }
        g.dispose();
        try (var stream = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
            if (!ImageIO.write(image, "png", stream)) { throw new IllegalStateException("PNG writer가 없어요."); }
        }
    }

    private static void label(Graphics2D g, String text, int x, int y, int size) {
        g.setColor(Color.WHITE); g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size)); g.drawString(text, x, y);
    }

    private static String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static SplittableRandom random(String identity) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
        return new SplittableRandom(ByteBuffer.wrap(hash).getLong());
    }

    private static void write(Path path, String text) throws Exception {
        Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
}
