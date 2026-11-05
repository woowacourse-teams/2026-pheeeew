package com.pheeeew.pressrace.v2.presentation;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.auth.infra.jwt.JwtTokenEncoder;
import com.pheeeew.device.application.token.AccessTokenIssuer;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.support.SharedJwtTestConfiguration;
import com.pheeeew.support.SharedPostgisTestConfiguration;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import({SharedPostgisTestConfiguration.class, SharedJwtTestConfiguration.class})
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "pheeeew.s3.bucket=pheeeew-test",
        "pheeeew.s3.key-prefix=pheeeew/test/", "pheeeew.s3.region=ap-northeast-2"
})
class PressStreamIntegrationTest {

    private static final String STREAM_PATH = "/api/v1/press-race/stream";

    @LocalServerPort
    private int port;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private JwtTokenEncoder jwtTokenEncoder;

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<Long> createdDevices = new ArrayList<>();
    private final List<InputStream> streams = new ArrayList<>();
    private HttpClient client;
    private Device device;
    private String token;

    @BeforeEach
    void setUp() {
        client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        device = deviceRepository.save(기본_기기_빌더().build());
        createdDevices.add(device.getId());
        token = accessTokenIssuer.issue(device.getPublicId()).accessToken();
    }

    @AfterEach
    void tearDown() throws Exception {
        for (InputStream stream : streams) {
            stream.close();
        }
        client.close();
        createdDevices.forEach(deviceRepository::deleteById);
    }

    @Test
    void 구독하면_SSE_헤더와_함께_현재_상태_snapshot_을_먼저_받는다() throws Exception {
        // when
        HttpResponse<InputStream> response = 구독한다(token, "?view=ALL");
        BufferedReader reader = 읽기(response);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("text/event-stream"));
        assertThat(response.headers().firstValue("X-Accel-Buffering")).hasValue("no");
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-cache");
        assertThat(다음_줄(reader)).isEqualTo("event:snapshot");
        String data = 다음_줄(reader);
        assertThat(data).startsWith("data:");
        JsonNode snapshot = json.readTree(data.substring("data:".length()));
        assertThat(snapshot.get("view").asString()).isEqualTo("ALL");
        assertThat(snapshot.get("me").get("participating").asBoolean()).isFalse();
        assertThat(snapshot.has("generatedAt")).isTrue();
        assertThat(snapshot.has("activeCount")).isTrue();
        assertThat(snapshot.get("top100").isArray()).isTrue();
    }

    @Test
    void 인증_없이_구독하면_401이다() throws Exception {
        // when
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri(""))
                .header("Accept", "text/event-stream").GET().build(), HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(json.readTree(response.body()).get("code").asString()).isEqualTo("AUTH-001");
    }

    @Test
    void 알_수_없는_화면은_JSON_본문의_400이다() throws Exception {
        // when
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("?view=RANKING"))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "text/event-stream").GET().build(),
                HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json.readTree(response.body()).get("code").asString()).isEqualTo("COMMON-001");
    }

    @Test
    void 쿼리의_토큰으로는_인증하지_않는다() throws Exception {
        // when
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("?access_token=" + token))
                .header("Accept", "text/event-stream").GET().build(), HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void 같은_기기의_세_번째_구독은_429_PRESS_STREAM_001_이고_앞의_두_구독은_유지된다() throws Exception {
        // given
        BufferedReader first = 읽기(구독한다(token, ""));
        BufferedReader second = 읽기(구독한다(token, "?view=ALL"));
        assertThat(다음_줄(first)).isEqualTo("event:snapshot");
        assertThat(다음_줄(second)).isEqualTo("event:snapshot");

        // when
        HttpResponse<String> rejected = client.send(HttpRequest.newBuilder(uri(""))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "text/event-stream").GET().build(),
                HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(rejected.statusCode()).isEqualTo(429);
        assertThat(json.readTree(rejected.body()).get("code").asString()).isEqualTo("PRESS_STREAM-001");
    }

    @Test
    void 토큰의_남은_수명이_지나면_연결이_닫히고_구독_수를_반납한다() throws Exception {
        // given
        String shortToken = jwtTokenEncoder.encodeAccessToken(device.getPublicId().toString(), Duration.ofSeconds(3));
        BufferedReader shortLived = 읽기(구독한다(shortToken, ""));
        BufferedReader other = 읽기(구독한다(token, ""));

        // when
        CompletableFuture<Boolean> ended = CompletableFuture.supplyAsync(() -> 끝까지_읽는다(shortLived));

        // then
        assertThat(ended.get(10, TimeUnit.SECONDS)).isTrue();
        BufferedReader next = 읽기(구독한다(token, ""));
        assertThat(다음_줄(next)).isEqualTo("event:snapshot");
        assertThat(다음_줄(other)).isEqualTo("event:snapshot");
    }

    private HttpResponse<InputStream> 구독한다(String accessToken, String query) throws Exception {
        HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder(uri(query))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept", "text/event-stream").GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        streams.add(response.body());
        assertThat(response.statusCode()).isEqualTo(200);
        return response;
    }

    private BufferedReader 읽기(HttpResponse<InputStream> response) {
        return new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
    }

    private String 다음_줄(BufferedReader reader) throws Exception {
        String line = reader.readLine();
        while (line != null && (line.isEmpty() || line.startsWith(":"))) {
            line = reader.readLine();
        }
        return line;
    }

    private boolean 끝까지_읽는다(BufferedReader reader) {
        try {
            while (reader.readLine() != null) {
                // 서버가 닫을 때까지 이벤트와 heartbeat 를 버린다.
            }
            return true;
        } catch (Exception exception) {
            return true;
        }
    }

    private URI uri(String query) {
        return URI.create("http://127.0.0.1:" + port + STREAM_PATH + query);
    }
}
