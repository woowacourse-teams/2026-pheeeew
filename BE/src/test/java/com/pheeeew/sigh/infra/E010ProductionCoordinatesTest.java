package com.pheeeew.sigh.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.sigh.domain.repository.SighRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@Tag("e010")
@PostgisDataJpaTest
@DataJpaTest(showSql = false)
class E010ProductionCoordinatesTest {

    private static final double ORIGIN_X = 953850;
    private static final double ORIGIN_Y = 1951950;
    private static final double TOLERANCE = 0.0001;
    private static final List<String> SCENES = List.of("stationary-5000", "hotspots-4500", "uniform-45000");
    private static final String INPUT = "docs/experiments/e007-client-location/results/2026-09-07/raw/coordinates.csv.gz";
    private static final String REFERENCE = "docs/experiments/e009-location-pipeline/results/2026-09-07/raw/coordinates.csv.gz";
    private static final String HEADER = "scene,seed,index,mode,true_x,true_y,sent_x,sent_y,shown_x,shown_y,longitude,latitude,error_m,reference_error_m\n";

    @Autowired
    private SighRepository repository;

    @Autowired
    private JdbcClient jdbc;

    private final Map<XY, XY> wgsCache = new HashMap<>();

    @Test
    void 실제_생성기와_PostGIS의_두_실행을_비교하고_그림을_보존한다() throws Exception {
        // given
        Path project = Path.of(System.getProperty("e010.projectDir"));
        Path output = Path.of(System.getProperty("e010.output"));
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.createDirectory(output);
        Map<String, String> sources = sources(project);
        write(output.resolve("source-before.sha256"), manifest(sources));
        write(output.resolve("environment.txt"), "java=" + System.getProperty("java.runtime.version")
                + "\nos=" + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + "\narch=" + System.getProperty("os.arch") + "\npostgres="
                + jdbc.sql("SELECT version()").query(String.class).single() + "\npostgis="
                + jdbc.sql("SELECT postgis_full_version()").query(String.class).single() + "\n");
        List<Input> inputs = inputs(project.resolve(INPUT));
        Map<String, XY> reference = references(project.resolve(REFERENCE));
        assertThat(inputs).hasSize(14000);
        assertThat(reference).hasSize(28000);

        // when
        Map<String, String> first = run(Files.createDirectory(output.resolve("run1")), inputs, reference);
        Map<String, String> second = run(Files.createDirectory(output.resolve("run2")), inputs, reference);

        // then
        assertThat(first).isEqualTo(second);
        assertThat(sources(project)).isEqualTo(sources);
        write(output.resolve("verification.txt"), "two_runs_identical=true\nsource_input_unchanged=true\n"
                + "events_per_run=14000\nrows_per_run=42000\npngs_per_run=3\n"
                + "reference_tolerance_m=0.0001\nclipped_points=0\nproduction_generator_calls_per_run=28000\n");
        System.out.println("E010 complete: 2 identical runs, 42000 rows/run, 3 PNGs/run; " + output);
    }

    private Map<String, String> run(Path output, List<Input> inputs, Map<String, XY> references) throws Exception {
        StringBuilder csv = new StringBuilder(HEADER);
        StringBuilder metrics = new StringBuilder("scene,mode,n,rms_error_m,max_error_m,outside300_fraction,max_reference_error_m\n");
        for (String scene : SCENES) {
            List<Input> selected = inputs.stream().filter(p -> p.scene().equals(scene)).toList();
            List<List<XY>> panels = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
            double[] squared = new double[3], max = new double[3], referenceMax = new double[3];
            int[] outside = new int[3];
            for (Input input : selected) {
                XY truth = input.truth();
                XY grid = input.grid();
                assertThat(Math.floor((ORIGIN_X + truth.x()) / 300) * 300 + 150 - ORIGIN_X).isEqualTo(grid.x());
                assertThat(Math.floor((ORIGIN_Y + truth.y()) / 300) * 300 + 150 - ORIGIN_Y).isEqualTo(grid.y());
                SplittableRandom clientRandom = random("e009|client|" + input.identity());
                double radius = 300 * Math.sqrt(clientRandom.nextDouble());
                double angle = 2 * Math.PI * clientRandom.nextDouble();
                XY approximate = XY.of(truth.x() + radius * Math.cos(angle), truth.y() + radius * Math.sin(angle));
                for (int mode = 0; mode < 3; mode++) {
                    XY sent = mode == 0 ? grid : mode == 1 ? truth : approximate;
                    XY wgs = toWgs(sent);
                    SplittableRandom serverRandom = random("e009|server|" + input.identity());
                    XY result;
                    if (mode == 0) {
                        // 325f656 이전의 사각형 식만 재현해요. 현재 서버 sampler를 복제하지 않아요.
                        var location = repository.findGeneratedLocation(wgs.x(), wgs.y(),
                                serverRandom.nextDouble(-150, 150), serverRandom.nextDouble(-150, 150));
                        result = XY.of(location.getLongitude(), location.getLatitude());
                    } else {
                        var generator = new PostgisSighLocationGenerator(repository, serverRandom);
                        Point location = generator.generate(wgs.x(), wgs.y());
                        assertThat(location.getSRID()).isEqualTo(4326);
                        result = XY.of(location.getX(), location.getY());
                    }
                    XY shown = toLocal(result);
                    double error = distance(shown, truth);
                    double referenceError = mode == 0 ? 0 : distance(shown, references.get(input.identity() + "|" + mode));
                    if (mode == 0) {
                        assertThat(Math.abs(shown.x() - grid.x())).isLessThanOrEqualTo(150 + TOLERANCE);
                        assertThat(Math.abs(shown.y() - grid.y())).isLessThanOrEqualTo(150 + TOLERANCE);
                    } else {
                        assertThat(distance(shown, sent)).isLessThanOrEqualTo(300 + TOLERANCE);
                        assertThat(referenceError).isLessThanOrEqualTo(TOLERANCE);
                        assertThat(error).isLessThanOrEqualTo((mode == 1 ? 300 : 600) + TOLERANCE);
                    }
                    assertThat(distance(approximate, truth)).isLessThanOrEqualTo(300 + TOLERANCE);
                    panels.get(mode).add(shown);
                    squared[mode] += error * error;
                    max[mode] = Math.max(max[mode], error);
                    referenceMax[mode] = Math.max(referenceMax[mode], referenceError);
                    if (error > 300) { outside[mode]++; }
                    csv.append(scene).append(',').append(input.seed()).append(',').append(input.index()).append(',').append(mode)
                            .append(',').append(truth.x()).append(',').append(truth.y()).append(',').append(sent.x()).append(',').append(sent.y())
                            .append(',').append(shown.x()).append(',').append(shown.y()).append(',').append(result.x()).append(',').append(result.y())
                            .append(',').append(error).append(',').append(referenceError).append('\n');
                }
            }
            for (int mode = 0; mode < 3; mode++) {
                metrics.append(scene).append(',').append(mode).append(',').append(selected.size()).append(',')
                        .append(Math.sqrt(squared[mode] / selected.size())).append(',').append(max[mode]).append(',')
                        .append((double) outside[mode] / selected.size()).append(',').append(referenceMax[mode]).append('\n');
            }
            String name = scene.equals("uniform-45000") ? "uniform-4500" : scene;
            render(output.resolve(name + ".png"), name, panels);
            System.out.println("E010 " + output.getFileName() + ": " + name + " / " + selected.size() + " events");
        }
        try (var gzip = new GZIPOutputStream(Files.newOutputStream(output.resolve("coordinates.csv.gz"), StandardOpenOption.CREATE_NEW))) {
            gzip.write(csv.toString().getBytes(StandardCharsets.UTF_8));
        }
        write(output.resolve("metrics.csv"), metrics.toString());
        Map<String, String> hashes = new TreeMap<>();
        try (var files = Files.list(output)) {
            for (Path file : files.toList()) { hashes.put(file.getFileName().toString(), sha(file)); }
        }
        write(output.resolve("checksums.sha256"), manifest(hashes));
        return hashes;
    }

    private XY toWgs(XY local) {
        return wgsCache.computeIfAbsent(local, point -> jdbc.sql("""
                        SELECT ST_X(p) AS x, ST_Y(p) AS y FROM
                        (SELECT ST_Transform(ST_SetSRID(ST_MakePoint(:x, :y), 5179), 4326) AS p) converted
                        """)
                .param("x", ORIGIN_X + point.x()).param("y", ORIGIN_Y + point.y())
                .query((rs, n) -> XY.of(rs.getDouble("x"), rs.getDouble("y"))).single());
    }

    private XY toLocal(XY wgs) {
        return jdbc.sql("""
                        SELECT ST_X(p) AS x, ST_Y(p) AS y FROM
                        (SELECT ST_Transform(ST_SetSRID(ST_MakePoint(:x, :y), 4326), 5179) AS p) converted
                        """)
                .param("x", wgs.x()).param("y", wgs.y())
                .query((rs, n) -> XY.of(rs.getDouble("x") - ORIGIN_X, rs.getDouble("y") - ORIGIN_Y)).single();
    }

    private List<Input> inputs(Path file) throws Exception {
        List<Input> values = new ArrayList<>();
        try (var reader = reader(file)) {
            assertThat(reader.readLine()).isEqualTo("scene,model,seed,index,true_x,true_y,grid_x,grid_y,shown_x,shown_y,error_m");
            for (String line; (line = reader.readLine()) != null;) {
                String[] r = line.split(",");
                if (r[1].equals("D") && included(r[0], Integer.parseInt(r[3]))) {
                    values.add(Input.of(r[0], Long.parseLong(r[2]), Integer.parseInt(r[3]),
                            XY.of(Double.parseDouble(r[4]), Double.parseDouble(r[5])),
                            XY.of(Double.parseDouble(r[6]), Double.parseDouble(r[7]))));
                }
            }
        }
        return values;
    }

    private Map<String, XY> references(Path file) throws Exception {
        Map<String, XY> values = new HashMap<>();
        try (var reader = reader(file)) {
            assertThat(reader.readLine()).isEqualTo("scene,mode,seed,index,true_x,true_y,sent_x,sent_y,shown_x,shown_y,age_hours,alpha,error_m");
            for (String line; (line = reader.readLine()) != null;) {
                String[] r = line.split(",");
                if (!r[1].equals("3") && included(r[0], Integer.parseInt(r[3]))) {
                    values.put(r[0] + "|" + r[2] + "|" + r[3] + "|" + r[1], XY.of(Double.parseDouble(r[8]), Double.parseDouble(r[9])));
                }
            }
        }
        return values;
    }

    private boolean included(String scene, int index) {
        return SCENES.contains(scene) && (!scene.equals("uniform-45000") || index < 900);
    }

    private void render(Path path, String scene, List<List<XY>> panels) throws Exception {
        BufferedImage image = new BufferedImage(3264, 1240, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(new Color(0x11131A)); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28));
        g.drawString("E010 | " + scene + " | " + panels.getFirst().size() + " events per panel | PostgreSQL 17 / PostGIS 3.5", 66, 42);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
        g.drawString("Same synthetic locations | x/y metres, +/-1200 | age 0h / alpha 1 / 6px stars | seeded production generator", 66, 80);
        g.drawString("Old square is reconstructed; new modes call current server code. Not an app screenshot or privacy guarantee.", 66, 112);
        byte[] rgba;
        try (var stream = getClass().getResourceAsStream("/experiments/e001/star-8x8.rgba.hex")) {
            rgba = HexFormat.of().parseHex(new String(stream.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s", ""));
        }
        assertThat(rgba).hasSize(256);
        BufferedImage sprite = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 64; i++) {
            int p = 4 * i;
            sprite.setRGB(i % 8, i / 8, Byte.toUnsignedInt(rgba[p + 3]) << 24 | Byte.toUnsignedInt(rgba[p]) << 16
                    | Byte.toUnsignedInt(rgba[p + 1]) << 8 | Byte.toUnsignedInt(rgba[p + 2]));
        }
        String[] titles = {"OLD | Grid centre + square", "SERVER ONLY | Actual + 300m disk", "CLIENT + SERVER | Two 300m disks"};
        for (int mode = 0; mode < 3; mode++) {
            int left = 66 + mode * 1066, top = 160;
            g.setColor(Color.WHITE); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
            g.drawString(titles[mode], left, top - 15);
            for (XY point : panels.get(mode)) {
                int x = (int) Math.round((point.x() + 1200) * 1000 / 2400);
                int y = (int) Math.round((1200 - point.y()) * 1000 / 2400);
                assertThat(x).isBetween(3, 996); assertThat(y).isBetween(3, 996);
                g.drawImage(sprite, left + x - 3, top + y - 3, 6, 6, null);
            }
            g.setColor(new Color(0x707782)); g.drawRect(left, top, 1000, 1000);
            g.setColor(Color.WHITE); g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 22));
            for (int tick = -1; tick <= 1; tick++) { g.drawString(Integer.toString(tick * 1200), left + (tick + 1) * 500 - 20, 1190); }
            g.drawString("x (m); y: +1200 top / -1200 bottom", left + 230, 1220);
        }
        g.dispose();
        try (var stream = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
            assertThat(ImageIO.write(image, "png", stream)).isTrue();
        }
    }

    private Map<String, String> sources(Path project) throws Exception {
        Map<String, String> result = new TreeMap<>();
        for (String path : List.of(INPUT, REFERENCE, "build.gradle", "docs/experiments/e010-production-coordinates/README.md",
                "docs/experiments/e010-production-coordinates/verify-results.rb",
                "src/test/java/com/pheeeew/sigh/infra/E010ProductionCoordinatesTest.java",
                "src/main/java/com/pheeeew/sigh/infra/PostgisSighLocationGenerator.java",
                "src/main/java/com/pheeeew/sigh/infra/UniformDiskSampler.java",
                "src/main/java/com/pheeeew/sigh/domain/repository/SighRepository.java",
                "src/test/resources/experiments/e001/star-8x8.rgba.hex")) {
            result.put(path, sha(project.resolve(path)));
        }
        return result;
    }

    private static BufferedReader reader(Path path) throws Exception {
        return new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(path)), StandardCharsets.UTF_8));
    }

    private static double distance(XY a, XY b) { return Math.hypot(a.x() - b.x(), a.y() - b.y()); }

    private static SplittableRandom random(String identity) throws Exception {
        return new SplittableRandom(ByteBuffer.wrap(MessageDigest.getInstance("SHA-256")
                .digest(identity.getBytes(StandardCharsets.UTF_8))).getLong());
    }

    private static String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static String manifest(Map<String, String> hashes) {
        StringBuilder result = new StringBuilder();
        hashes.forEach((p, h) -> result.append(h).append("  ").append(p).append('\n'));
        return result.toString();
    }

    private static void write(Path path, String value) throws Exception {
        Files.writeString(path, value, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    record XY(double x, double y) {
        static XY of(double x, double y) {
            if (!Double.isFinite(x) || !Double.isFinite(y)) { throw new IllegalArgumentException("유한 좌표가 필요해요."); }
            return new XY(x, y);
        }
    }

    record Input(String scene, long seed, int index, XY truth, XY grid) {
        static Input of(String scene, long seed, int index, XY truth, XY grid) { return new Input(scene, seed, index, truth, grid); }
        String identity() { return scene + "|" + seed + "|" + index; }
    }
}
