package com.pheeeew.emotion.infra.metrics;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.pheeeew.device.application.token.AccessTokenIssuer;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.mapcache.EmotionMapCacheCoordinator;
import com.pheeeew.emotion.application.mapcache.PinCandidateCache;
import com.pheeeew.emotion.application.mapcache.RegionSummaryCache;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.support.SharedJwtTestConfiguration;
import com.pheeeew.support.SharedPostgisTestConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
class MapReadHttpIntegrationTest {

    private static final String BOUNDS = "?minLongitude=126&minLatitude=37&maxLongitude=128&maxLatitude=39";

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
    @Autowired
    private MeterRegistry meters;
    @Autowired
    private EmotionMapCacheCoordinator coordinator;
    @Autowired
    private PinCandidateCache pins;
    @Autowired
    private RegionSummaryCache regions;
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
        for (int index = 0; index < 3; index++) {
            emotions.save(기본_한숨_빌더().deviceId(device.getId()).memo("HTTP 계측 검증").state(EmotionState.ANGRY).build());
        }
        coordinator.invalidate();
        pins.rebuild();
        regions.rebuild();
    }

    @AfterEach
    void tearDown() {
        client.close();
        coordinator.invalidate();
        emotions.deleteAllInBatch();
        jdbc.sql("DELETE FROM device_daily_stamps").update();
        devices.deleteAllInBatch();
        jdbc.sql("DELETE FROM regions").update();
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = NULL, backfill_verified_at = NULL").update();
    }

    @ParameterizedTest
    @CsvSource({"v2,pins,", "v4,pins,", "v2,regions,SIDO", "v4,regions,SIDO", "v2,regions,SIGUNGU", "v4,regions,SIGUNGU"})
    void 실제_보안_MVC_JSON_경로를_버전별로_한번씩_계측하고_응답을_유지한다(String version, String route, String level) throws Exception {
        // given
        String path = "/api/" + version + "/emotions/map" + (level == null ? "" : "/regions")
                + BOUNDS + (level == null ? "" : "&level=" + level);
        List<String> stages = List.of("map_filter_total", "map_jwt_decode", "map_pre_mvc", "map_mvc_pre_controller",
                "map_" + route + "_controller", "map_" + route + "_service", "map_json_write",
                "map_response_processing", "map_filter_return");
        double[] before = stages.stream().mapToDouble(stage -> stageCount(version, stage)).toArray();
        double requests = requestCount(version, route, "all_default", "cache_hit", "success");
        double writes = writeCount(version, route);

        // when
        var response = send(path, token);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-cache, private");
        assertThat(response.headers().firstValue("X-Correlation-ID")).isPresent();
        var body = mapper.readTree(response.body());
        if (level == null) {
            assertThat(body.get("items").size()).isEqualTo(3);
            assertThat(body.get("hasNext").asBoolean()).isFalse();
            assertThat(body.get("items").get(0).get("properties").has("groupId")).isEqualTo(version.equals("v2"));
        } else {
            assertThat(body.size()).isEqualTo(1);
        }
        for (int index = 0; index < stages.size(); index++) {
            assertThat(stageCount(version, stages.get(index)) - before[index]).as(stages.get(index)).isEqualTo(1);
        }
        assertThat(writeCount(version, route) - writes).isEqualTo(1);
        assertThat(requestCount(version, route, "all_default", "cache_hit", "success") - requests).isEqualTo(1);
    }

    @Test
    void 한달_첫페이지와_커서_후속페이지는_SQL_우회를_집계하고_v4_계약을_유지한다() throws Exception {
        // given
        LocalDate end = LocalDate.now(ZoneId.of("Asia/Seoul"));
        String period = "&startDate=" + end.minusMonths(1) + "&endDate=" + end;
        double before = requestCount("v4", "pins", "month_current_default", "bypass_period", "success");
        double dbBefore = stageCount("v4", "map_pins_db_path");

        // when
        var first = send("/api/v4/emotions/map" + BOUNDS + period + "&limit=1", token);
        String cursor = mapper.readTree(first.body()).get("nextCursor").asString();
        var next = send("/api/v4/emotions/map?limit=1&cursor="
                + java.net.URLEncoder.encode(cursor, java.nio.charset.StandardCharsets.UTF_8), token);

        // then
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(next.statusCode()).isEqualTo(200);
        var firstId = mapper.readTree(first.body()).get("items").get(0).get("id").asLong();
        var nextId = mapper.readTree(next.body()).get("items").get(0).get("id").asLong();
        assertThat(firstId).isNotEqualTo(nextId);
        assertThat(requestCount("v4", "pins", "month_current_default", "bypass_period", "success") - before).isEqualTo(2);
        assertThat(stageCount("v4", "map_pins_db_path") - dbBefore).isEqualTo(2);
    }

    @Test
    void 인증_커서_준비상태_실패를_분리하고_다음_요청의_문맥을_오염시키지_않는다() throws Exception {
        // given
        double invalid = requestCount("v4", "pins", "unclassified", "unclassified", "client_error");
        double jwtErrors = stageErrorCount("v4", "map_jwt_decode");

        // when
        var unauthorized = send("/api/v4/emotions/map" + BOUNDS, "invalid-token");
        var invalidCursor = send("/api/v4/emotions/map?cursor=invalid-cursor", token);
        double failures = requestCount("v4", "regions", "all_default", "eligible", "server_error");
        jdbc.sql("UPDATE region_datasets SET backfill_verified_at = NULL").update();
        var unavailable = send("/api/v4/emotions/map/regions" + BOUNDS + "&level=SIDO", token);
        var valid = send("/api/v4/emotions/map" + BOUNDS, token);

        // then
        assertThat(unauthorized.statusCode()).isEqualTo(401);
        assertThat(invalidCursor.statusCode()).isEqualTo(400);
        assertThat(unavailable.statusCode()).isEqualTo(503);
        assertThat(valid.statusCode()).isEqualTo(200);
        assertThat(requestCount("v4", "pins", "unclassified", "unclassified", "client_error") - invalid).isEqualTo(2);
        assertThat(stageErrorCount("v4", "map_jwt_decode") - jwtErrors).isEqualTo(1);
        assertThat(requestCount("v4", "regions", "all_default", "eligible", "server_error") - failures).isEqualTo(1);
    }

    private HttpResponse<byte[]> send(String path, String bearer) throws Exception {
        double before = meters.find("pheeeew.map.read.requests").counters().stream().mapToDouble(counter -> counter.count()).sum();
        var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + bearer)
                .header("Accept-Encoding", "identity").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        // 본문 수신과 서버 필터 finally의 완료는 같은 시점이 아니다.
        await().atMost(Duration.ofSeconds(5)).until(() -> meters.find("pheeeew.map.read.requests").counters().stream()
                .mapToDouble(counter -> counter.count()).sum() > before);
        return response;
    }

    private double stageCount(String version, String stage) {
        return meters.find("pheeeew.map.read.stage").tags("api_version", version, "stage", stage, "outcome", "success")
                .timers().stream().mapToLong(timer -> timer.count()).sum();
    }

    private double stageErrorCount(String version, String stage) {
        return meters.find("pheeeew.map.read.stage").tags("api_version", version, "stage", stage, "outcome", "error")
                .timers().stream().mapToLong(timer -> timer.count()).sum();
    }

    private double writeCount(String version, String route) {
        return meters.find("pheeeew.map.response.write").tags("api_version", version, "route", route, "outcome", "success")
                .timers().stream().mapToLong(timer -> timer.count()).sum();
    }

    private double requestCount(String version, String route, String query, String decision, String outcome) {
        return meters.find("pheeeew.map.read.requests").tags("api_version", version, "route", route, "query", query,
                "decision", decision, "outcome", outcome).counters().stream().mapToDouble(counter -> counter.count()).sum();
    }
}
