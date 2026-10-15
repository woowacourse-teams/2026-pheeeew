package com.pheeeew.emotion.presentation;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.device.application.token.AccessTokenIssuer;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.support.SharedJwtTestConfiguration;
import com.pheeeew.support.SharedPostgisTestConfiguration;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

@Import({SharedPostgisTestConfiguration.class, SharedJwtTestConfiguration.class})
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "pheeeew.s3.bucket=pheeeew-test",
        "pheeeew.s3.key-prefix=pheeeew/test/", "pheeeew.s3.region=ap-northeast-2"
})
class RegionMapCompressionIntegrationTest {

    private static final String BOUNDS = "?minLongitude=126&minLatitude=37&maxLongitude=128&maxLatitude=39";
    private static final String REGIONS = "/api/v2/emotions/map/regions" + BOUNDS + "&level=SIGUNGU";

    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private EmotionRepository emotions;
    @Autowired
    private AccessTokenIssuer tokens;
    @Autowired
    private ObjectMapper mapper;
    @LocalServerPort
    private int port;

    private HttpClient client;
    private String token;

    @BeforeEach
    void setUp() {
        client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        검증용_지역_계층을_저장한다(jdbc);
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP, backfill_verified_at = CURRENT_TIMESTAMP")
                .update();
        var device = devices.save(기본_기기_빌더().build());
        token = tokens.issue(device.getPublicId()).accessToken();
        for (int index = 20; index < 40; index++) {
            String code = "11%03d".formatted(index);
            addRegion(code, "SIGUNGU", "11", "11010");
            addRegion(code + "001", "EMD", code, "11010530");
            emotions.save(기본_한숨_빌더().regionCode(code + "001").deviceId(device.getId())
                    .memo("압축 검증용 메모").state(EmotionState.ANGRY).build());
        }
    }

    @AfterEach
    void tearDown() {
        client.close();
        emotions.deleteAll();
        devices.deleteAll();
        jdbc.sql("DELETE FROM regions").update();
        jdbc.sql("UPDATE region_datasets SET backfill_verified_at = NULL, boundaries_verified_at = NULL").update();
    }

    @ParameterizedTest
    @ValueSource(strings = {"gzip", "GZip", "deflate, gzip;q=0.8"})
    void 지역_요약만_gzip으로_보내고_해제한_JSON과_캐시_정책은_유지한다(String encoding) throws Exception {
        // given
        var original = send(REGIONS, "GET", true, "identity");
        assertThat(original.body().length).isGreaterThan(2048);

        // when
        var response = send(REGIONS, "GET", true, encoding);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Encoding")).contains("gzip");
        assertThat(String.join(",", response.headers().allValues("Vary")).toLowerCase(Locale.ROOT))
                .contains("accept-encoding");
        assertThat(response.headers().firstValue("Content-Type")).isEqualTo(original.headers().firstValue("Content-Type"));
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-cache, private");
        assertThat(response.body().length).isLessThan(original.body().length);
        assertThat(decompress(response.body())).isEqualTo(original.body());
        assertThat(mapper.readTree(decompress(response.body())).size()).isEqualTo(20);
        assertThat(response.headers().allValues("Content-Length"))
                .allSatisfy(value -> assertThat(Long.parseLong(value)).isEqualTo(response.body().length));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "identity", "br", "gzip;q=0", "br, gzip;q=0", "*"})
    void gzip을_명시적으로_허용하지_않는_요청은_기존_JSON을_반환한다(String encoding) throws Exception {
        // given
        var original = send(REGIONS, "GET", true, "identity");

        // when
        var response = send(REGIONS, "GET", true, encoding);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(response.body()).isEqualTo(original.body());
    }

    @Test
    void 큰_개별_지도_JSON은_gzip_지원_요청이어도_압축하지_않는다() throws Exception {
        // given / when
        var response = send("/api/v2/emotions/map" + BOUNDS, "GET", true, "gzip");

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().length).isGreaterThan(2048);
        assertThat(response.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(mapper.readTree(response.body()).get("items").size()).isEqualTo(20);
    }

    @Test
    void 인증_검증_오류와_GET이_아닌_응답은_압축하지_않는다() throws Exception {
        // given / when
        var unauthenticated = send(REGIONS, "GET", false, "gzip");
        var invalid = send("/api/v2/emotions/map/regions?level=SIGUNGU", "GET", true, "gzip");
        var head = send(REGIONS, "HEAD", true, "gzip");
        var post = send(REGIONS, "POST", true, "gzip");

        // then
        assertThat(unauthenticated.statusCode()).isEqualTo(401);
        assertThat(mapper.readTree(unauthenticated.body()).get("code").asString()).isEqualTo("AUTH-001");
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(mapper.readTree(invalid.body()).get("code").asString()).isEqualTo("COMMON-001");
        assertThat(post.statusCode()).isEqualTo(403);
        for (var response : java.util.List.of(unauthenticated, invalid, head, post)) {
            assertThat(response.headers().firstValue("Content-Encoding")).isEmpty();
        }
        assertThat(head.body()).isEmpty();
    }

    @Test
    void 작은_전국_요약은_gzip_지원_요청이어도_압축하지_않는다() throws Exception {
        // given
        String path = REGIONS.replace("level=SIGUNGU", "level=SIDO");
        var original = send(path, "GET", true, "identity");
        assertThat(original.body().length).isLessThan(2048);

        // when
        var response = send(path, "GET", true, "br", "gzip");

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(response.body()).isEqualTo(original.body());
    }

    @ParameterizedTest
    @ValueSource(ints = {2047, 2048, 2049})
    void 지역_요약의_실제_UTF8_바이트_크기로_압축_경계를_판단한다(int size) throws Exception {
        // given: 한글을 포함하는 실제 응답을 경계 크기로 맞춘다.
        jdbc.sql("DELETE FROM emotions WHERE region_code >= '11026001'").update();
        jdbc.sql("UPDATE regions SET name = '가' WHERE level = 'SIGUNGU'").update();
        int remaining = size - send(REGIONS, "GET", true, "identity").body().length;
        assertThat(remaining).isPositive();
        for (int index = 20; index < 26; index++) {
            int korean = Math.min(99, remaining / 3);
            int ascii = Math.min(99 - korean, remaining - korean * 3);
            String name = "가" + "나".repeat(korean) + "x".repeat(ascii);
            jdbc.sql("UPDATE regions SET name = :name WHERE code = :code")
                    .param("name", name).param("code", "11%03d".formatted(index)).update();
            remaining -= korean * 3 + ascii;
        }
        assertThat(remaining).isZero();
        var original = send(REGIONS, "GET", true, "identity");
        assertThat(original.body()).hasSize(size);

        // when: 복수 Accept-Encoding 헤더에서도 gzip 허용 여부를 판단한다.
        var response = send(REGIONS, "GET", true, "br", "gzip");

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        if (size < 2048) {
            assertThat(response.headers().firstValue("Content-Encoding")).isEmpty();
            assertThat(response.body()).isEqualTo(original.body());
            assertThat(response.headers().firstValue("Content-Length")).contains(Integer.toString(size));
        } else {
            assertThat(response.headers().firstValue("Content-Encoding")).contains("gzip");
            assertThat(decompress(response.body())).isEqualTo(original.body());
        }
    }

    @Test
    void 지역_자료가_준비되지_않으면_오류_JSON을_온전히_비압축으로_반환한다() throws Exception {
        // given
        jdbc.sql("UPDATE region_datasets SET backfill_verified_at = NULL").update();

        // when
        var response = send(REGIONS, "GET", true, "gzip");

        // then
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(mapper.readTree(response.body()).get("code").asString()).isEqualTo("EMOTION-013");
    }

    private void addRegion(String code, String level, String parent, String source) {
        jdbc.sql("""
                INSERT INTO regions (code, level, name, parent_code, boundary, display_point)
                SELECT :code, :level, name, :parent, boundary, display_point FROM regions WHERE code = :source
                """).param("code", code).param("level", level).param("parent", parent).param("source", source).update();
    }

    private HttpResponse<byte[]> send(String path, String method, boolean authenticated, String... encodings) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).method(method, HttpRequest.BodyPublishers.noBody());
        if (authenticated) {
            request.header("Authorization", "Bearer " + token);
        }
        for (String encoding : encodings) {
            if (encoding != null) {
                request.header("Accept-Encoding", encoding);
            }
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private byte[] decompress(byte[] body) throws Exception {
        try (var stream = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return stream.readAllBytes();
        }
    }
}
