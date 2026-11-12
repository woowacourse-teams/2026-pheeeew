package com.pheeeew.climb;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.support.SharedJwtTestConfiguration;
import com.pheeeew.support.SharedPostgisTestConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import({SharedPostgisTestConfiguration.class, SharedJwtTestConfiguration.class})
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "pheeeew.s3.bucket=pheeeew-test",
        "pheeeew.s3.key-prefix=pheeeew/test/", "pheeeew.s3.region=ap-northeast-2",
        "pheeeew.climb.enabled=true", "pheeeew.climb.allowed-origin=" + ClimbIntegrationTest.GAME,
        "pheeeew.climb.guest-creates-per-hour=1000"
})
class ClimbIntegrationTest {

    static final String GAME = "http://game.pheeeew.test";

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcClient jdbc;

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<java.net.http.WebSocket> sockets = new ArrayList<>();
    private HttpClient client;

    @BeforeEach
    void setUp() {
        client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    }

    @AfterEach
    void tearDown() {
        sockets.forEach(java.net.http.WebSocket::abort);
        client.close();
        jdbc.sql("DELETE FROM climb_run").update();
        jdbc.sql("DELETE FROM climb_guest").update();
    }

    @Test
    void 닉네임으로_손님을_만들면_쿠키를_내려_주고_그_쿠키로_내_정보를_읽는다() throws Exception {
        // when
        HttpResponse<String> created = post("/api/v1/climb/guests", "{\"nickname\":\"피유\"}", null);

        // then
        assertThat(created.statusCode()).isEqualTo(201);
        String setCookie = created.headers().firstValue("Set-Cookie").orElseThrow();
        assertThat(setCookie).startsWith("climb_guest=").contains("Path=/api/v1/climb", "Max-Age=15552000",
                "Secure", "HttpOnly", "SameSite=Lax");
        JsonNode guest = json.readTree(created.body());
        assertThat(guest.path("nickname").stringValue()).isEqualTo("피유");
        assertThat(guest.path("tag").stringValue()).hasSize(4);

        HttpResponse<String> me = get("/api/v1/climb/me", cookie(created));
        assertThat(me.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(me.body());
        assertThat(body.path("id").stringValue()).isEqualTo(guest.path("id").stringValue());
        assertThat(body.path("tag").stringValue()).isEqualTo(guest.path("tag").stringValue());
        assertThat(body.path("run").isNull()).isTrue();
        assertThat(me.headers().firstValue("Set-Cookie").orElseThrow()).contains("Max-Age=15552000");
    }

    @Test
    void 같은_닉네임도_만들_수_있고_꼬리표로_구분한다() throws Exception {
        // when
        JsonNode first = json.readTree(post("/api/v1/climb/guests", "{\"nickname\":\"피유\"}", null).body());
        JsonNode second = json.readTree(post("/api/v1/climb/guests", "{\"nickname\":\"피유\"}", null).body());

        // then
        assertThat(first.path("id").stringValue()).isNotEqualTo(second.path("id").stringValue());
        assertThat(second.path("nickname").stringValue()).isEqualTo("피유");
    }

    @Test
    void 규칙에_맞지_않는_닉네임은_거절_사유와_함께_400을_돌려준다() throws Exception {
        // when
        HttpResponse<String> response = post("/api/v1/climb/guests", "{\"nickname\":\"피유!\"}", null);

        // then
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json.readTree(response.body()).path("code").stringValue()).isEqualTo("NICKNAME_CHARACTERS");
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @Test
    void 쿠키가_없거나_올바르지_않으면_내_정보는_401이다() throws Exception {
        // when
        HttpResponse<String> missing = get("/api/v1/climb/me", null);
        HttpResponse<String> forged = get("/api/v1/climb/me", "climb_guest=forged-token");

        // then
        assertThat(missing.statusCode()).isEqualTo(401);
        assertThat(forged.statusCode()).isEqualTo(401);
    }

    @Test
    void 닉네임을_바꾸면_내_정보에_반영되고_하루_세_번을_넘으면_429다() throws Exception {
        // given
        String cookie = cookie(post("/api/v1/climb/guests", "{\"nickname\":\"피유\"}", null));

        // when
        List<Integer> statuses = new ArrayList<>();
        for (String nickname : List.of("하나", "둘둘", "셋셋", "넷넷")) {
            statuses.add(patch("/api/v1/climb/me", "{\"nickname\":\"" + nickname + "\"}", cookie).statusCode());
        }

        // then
        assertThat(statuses).containsExactly(200, 200, 200, 429);
        assertThat(json.readTree(get("/api/v1/climb/me", cookie).body()).path("nickname").stringValue())
                .isEqualTo("셋셋");
    }

    @Test
    void 게임_출처만_자격_증명을_실은_교차_출처_요청을_허용한다() throws Exception {
        // when
        HttpResponse<String> allowed = preflight(GAME);
        HttpResponse<String> foreign = preflight("https://foreign.example");

        // then
        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).hasValue(GAME);
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Credentials")).hasValue("true");
        assertThat(foreign.statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> preflight(String origin) throws Exception {
        return client.send(HttpRequest.newBuilder(uri("/api/v1/climb/guests"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String cookie) throws Exception {
        return send(request(path, cookie).POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private HttpResponse<String> patch(String path, String body, String cookie) throws Exception {
        return send(request(path, cookie).method("PATCH", HttpRequest.BodyPublishers.ofString(body)));
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        return send(request(path, cookie).GET());
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path, String cookie) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header("Origin", GAME);
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return builder;
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static String cookie(HttpResponse<String> response) {
        return response.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
    }
}
