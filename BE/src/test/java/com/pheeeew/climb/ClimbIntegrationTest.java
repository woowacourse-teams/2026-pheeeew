package com.pheeeew.climb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;

import com.pheeeew.PheeeewApplication;
import com.pheeeew.climb.application.ClimbGuestService;
import com.pheeeew.device.application.token.AccessTokenIssuer;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.support.SharedJwtTestConfiguration;
import com.pheeeew.support.SharedPostgisTestConfiguration;
import com.pheeeew.support.SharedTestContainers;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
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
        "pheeeew.climb.guest-creates-per-hour=1000", "pheeeew.climb.admin-key=" + ClimbIntegrationTest.ADMIN_KEY
})
class ClimbIntegrationTest {

    static final String GAME = "http://game.pheeeew.test";
    static final String ADMIN_KEY = "integration-test-admin-key";
    private static final String ADMIN_SESSION = "/api/v1/climb/admin/session";

    @LocalServerPort
    private int port;
    @Autowired
    private Environment environment;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private AccessTokenIssuer tokens;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private ClimbGuestService guestService;

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<WebSocket> sockets = new ArrayList<>();
    private HttpClient client;

    @BeforeEach
    void setUp() {
        client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    }

    @AfterEach
    void tearDown() throws Exception {
        sockets.forEach(WebSocket::abort);
        // 다음 테스트의 접속자 순위에 남지 않도록 서버가 연결 종료를 처리할 때까지 기다린다.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (json.readTree(get("/api/v1/climb/ranking?board=online&map=v1", null).body()).path("online").intValue(0) > 0
                && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        client.close();
    }

    @Test
    void 닉네임으로_손님을_만들면_쿠키를_내려_주고_그_쿠키로_내_정보를_읽는다() throws Exception {
        // when
        HttpResponse<String> created = post("/api/v1/climb/guests", "{\"nickname\":\"히유\"}", null);

        // then
        assertThat(created.statusCode()).isEqualTo(201);
        String setCookie = created.headers().firstValue("Set-Cookie").orElseThrow();
        assertThat(setCookie).startsWith("climb_guest=").contains("Path=/api/v1/climb", "Max-Age=15552000",
                "Secure", "HttpOnly", "SameSite=Lax");
        JsonNode guest = json.readTree(created.body());
        assertThat(guest.path("nickname").stringValue()).isEqualTo("히유");
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
        JsonNode first = json.readTree(post("/api/v1/climb/guests", "{\"nickname\":\"히유\"}", null).body());
        JsonNode second = json.readTree(post("/api/v1/climb/guests", "{\"nickname\":\"히유\"}", null).body());

        // then
        assertThat(first.path("id").stringValue()).isNotEqualTo(second.path("id").stringValue());
        assertThat(second.path("nickname").stringValue()).isEqualTo("히유");
    }

    @Test
    void 규칙에_맞지_않는_닉네임은_거절_사유와_함께_400을_돌려준다() throws Exception {
        // when
        HttpResponse<String> response = post("/api/v1/climb/guests", "{\"nickname\":\"히유!\"}", null);

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
        String cookie = cookie(post("/api/v1/climb/guests", "{\"nickname\":\"히유\"}", null));

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

    @Test
    void 두_손님이_높이를_보고하면_순위를_받고_더_높이_오른_손님이_위로_간다() throws Exception {
        // given
        String first = cookie(post("/api/v1/climb/guests", "{\"nickname\":\"먼저\"}", null));
        String second = cookie(post("/api/v1/climb/guests", "{\"nickname\":\"나중\"}", null));
        Socket me = connect(first);
        JsonNode welcome = hello(me);
        assertThat(welcome.path("me").path("nickname").stringValue()).isEqualTo("먼저");
        assertThat(welcome.path("run").isNull()).isTrue();
        assertThat(welcome.path("serverTime").isNumber()).isTrue();

        // when
        me.send("{\"t\":\"pos\",\"h\":10,\"best\":12,\"falls\":0,\"time\":2,\"x\":5}");
        JsonNode leading = me.await("rank", rank -> rank.path("me").path("best").intValue() == 12);
        Socket other = connect(second);
        hello(other);
        other.send("{\"t\":\"pos\",\"h\":18,\"best\":18,\"falls\":1,\"time\":2,\"x\":9}");
        JsonNode overtaken = me.await("rank", rank -> rank.path("me").path("rank").intValue() == 2);

        // then
        assertThat(leading.path("me").path("rank").intValue()).isEqualTo(1);
        assertThat(leading.path("top").get(0).path("nickname").stringValue()).isEqualTo("먼저");
        assertThat(leading.path("top").get(0).path("character").stringValue()).isEqualTo("angry");
        assertThat(overtaken.path("top").get(0).path("nickname").stringValue()).isEqualTo("나중");
        assertThat(overtaken.path("top").get(0).path("best").intValue()).isEqualTo(18);
        assertThat(overtaken.path("near")).extracting(item -> item.path("rank").intValue()).containsExactly(1, 2);
        assertThat(overtaken.path("online").intValue()).isEqualTo(2);
        JsonNode online = json.readTree(get("/api/v1/climb/ranking?board=online&map=v1", first).body());
        assertThat(online.path("me").path("rank").intValue()).isEqualTo(2);
    }

    @Test
    void 쿠키가_없는_웹소켓_연결은_거절한다() {
        // when
        Throwable failure = catchThrowable(() -> connect(null));

        // then
        assertThat(handshakeStatus(failure)).isEqualTo(401);
    }

    @Test
    void 허용되지_않은_출처의_웹소켓_연결은_거절한다() throws Exception {
        // given
        String cookie = cookie(post("/api/v1/climb/guests", "{\"nickname\":\"히유\"}", null));

        // when
        Throwable failure = catchThrowable(() -> connect(cookie, "https://foreign.example"));

        // then
        assertThat(handshakeStatus(failure)).isEqualTo(403);
    }

    @Test
    void 같은_손님이_다시_연결하면_이전_연결을_닫는다() throws Exception {
        // given
        String cookie = cookie(post("/api/v1/climb/guests", "{\"nickname\":\"히유\"}", null));
        Socket previous = connect(cookie);
        hello(previous);

        // when
        Socket next = connect(cookie);
        hello(next);

        // then
        assertThat(previous.await("error", error -> true).path("code").stringValue()).isEqualTo("CONNECTION_REPLACED");
        assertThat(previous.closedReason()).isEqualTo("1008 CONNECTION_REPLACED");
        next.send("{\"t\":\"pos\",\"h\":3,\"best\":3,\"falls\":0,\"time\":1,\"x\":0}");
        assertThat(next.await("rank", rank -> rank.path("me").path("best").intValue() == 3).path("online").intValue())
                .isEqualTo(1);
    }

    @Test
    void 빌드가_없거나_낮은_hello는_CONNECTION_REPLACED로_닫고_흔적을_남기지_않는다() throws Exception {
        // given
        HttpResponse<String> legacyGuest = post("/api/v1/climb/guests", "{\"nickname\":\"옛탭\"}", null);
        Socket legacy = connect(cookie(legacyGuest));
        Socket outdated = connect(cookie(post("/api/v1/climb/guests", "{\"nickname\":\"낮은탭\"}", null)));

        // when
        legacy.send("{\"t\":\"hello\",\"v\":1,\"map\":\"v1\",\"character\":\"angry\"}");
        outdated.send("{\"t\":\"hello\",\"v\":1,\"build\":0,\"map\":\"v1\",\"character\":\"angry\"}");

        // then
        assertThat(legacy.await("error", error -> true).path("code").stringValue()).isEqualTo("CONNECTION_REPLACED");
        assertThat(legacy.closedReason()).isEqualTo("1008 CONNECTION_REPLACED");
        assertThat(outdated.await("error", error -> true).path("code").stringValue()).isEqualTo("CONNECTION_REPLACED");
        assertThat(outdated.closedReason()).isEqualTo("1008 CONNECTION_REPLACED");
        JsonNode online = json.readTree(get("/api/v1/climb/ranking?board=online&map=v1", null).body());
        assertThat(online.path("online").intValue()).isZero();
        assertThat(online.path("top")).isEmpty();
        String legacyId = json.readTree(legacyGuest.body()).path("id").stringValue();
        assertThat(jdbc.sql("SELECT count(*) FROM climb_run WHERE guest_id = :id")
                .param("id", java.util.UUID.fromString(legacyId)).query(Long.class).single()).isZero();
    }

    @Test
    void 불가능한_높이_보고는_순위에_반영되지_않는다() throws Exception {
        // given
        String cookie = cookie(post("/api/v1/climb/guests", "{\"nickname\":\"히유\"}", null));
        Socket me = connect(cookie);
        hello(me);

        // when
        me.send("{\"t\":\"pos\",\"h\":500,\"best\":500,\"falls\":0,\"time\":1,\"x\":0}");
        me.send("{\"t\":\"pos\",\"h\":5,\"best\":5,\"falls\":0,\"time\":1,\"x\":0}");
        JsonNode rank = me.await("rank", message -> message.path("me").path("best").intValue() == 5);

        // then
        assertThat(rank.path("top").get(0).path("best").intValue()).isEqualTo(5);
        assertThat(me.received()).noneMatch(message -> message.toString().contains("500"));
    }

    @Test
    void 구간을_넘으면_접속자에게_알리고_연결을_끊었다_다시_오면_이어서_한다() throws Exception {
        // given
        HttpResponse<String> created = post("/api/v1/climb/guests", "{\"nickname\":\"히유\"}", null);
        String cookie = cookie(created);
        String watcherCookie = cookie(post("/api/v1/climb/guests", "{\"nickname\":\"구경\"}", null));
        // 1구간 경계(135칸) 바로 아래까지 오른 등반. 2초에 20칸 상한 때문에 처음부터 오르면 오래 걸린다.
        jdbc.sql("""
                INSERT INTO climb_run (id, guest_id, map_version, character, best_height, best_at, height, x, falls,
                    play_seconds, started_at, updated_at)
                VALUES (:id, :guest, 'v1', 'angry', 130, now(), 128, 0, 2, 29, now(), now())
                """)
                .param("id", java.util.UUID.randomUUID())
                .param("guest", java.util.UUID.fromString(json.readTree(created.body()).path("id").stringValue()))
                .update();
        Socket watcher = connect(watcherCookie);
        hello(watcher);
        Socket me = connect(cookie);
        hello(me);
        me.send("{\"t\":\"zone\",\"zone\":1,\"time\":30}");
        me.send("{\"t\":\"pos\",\"h\":148,\"best\":150,\"falls\":2,\"time\":30,\"x\":7}");

        // when
        me.send("{\"t\":\"zone\",\"zone\":1,\"time\":31}");
        JsonNode event = watcher.await("event", message -> true);
        me.close();
        JsonNode resumed = json.readTree(get("/api/v1/climb/me", cookie).body()).path("run");
        Socket again = connect(cookie);
        JsonNode welcome = hello(again);

        // then
        assertThat(event.path("kind").stringValue()).isEqualTo("zone");
        assertThat(event.path("nickname").stringValue()).isEqualTo("히유");
        assertThat(event.path("zone").intValue()).isEqualTo(1);
        assertThat(event.path("tag").stringValue()).isEqualTo(welcome.path("me").path("tag").stringValue());
        assertThat(resumed.path("height").intValue()).isEqualTo(148);
        assertThat(resumed.path("best").intValue()).isEqualTo(150);
        assertThat(resumed.path("x").intValue()).isEqualTo(7);
        assertThat(resumed.path("falls").intValue()).isEqualTo(2);
        assertThat(resumed.path("time").intValue()).isEqualTo(31);
        assertThat(resumed.path("map").stringValue()).isEqualTo("v1");
        assertThat(welcome.path("run")).isEqualTo(resumed);
    }

    @Test
    void 처음부터_다시_하면_새_등반이_되고_이전_최고_기록은_역대_순위에_남는다() throws Exception {
        // given
        String cookie = cookie(post("/api/v1/climb/guests", "{\"nickname\":\"히유\"}", null));
        Socket me = connect(cookie);
        hello(me);
        me.send("{\"t\":\"pos\",\"h\":20,\"best\":20,\"falls\":0,\"time\":3,\"x\":0}");
        me.await("rank", rank -> rank.path("me").path("best").intValue() == 20);

        // when
        me.send("{\"t\":\"restart\"}");
        JsonNode online = me.await("rank", rank -> rank.path("me").path("best").intValue() == 0);
        JsonNode all = json.readTree(get("/api/v1/climb/ranking?board=all&map=v1", cookie).body());
        JsonNode today = json.readTree(get("/api/v1/climb/ranking?board=today&map=v1", cookie).body());

        // then
        assertThat(online.path("me").path("rank").intValue()).isEqualTo(1);
        assertThat(all.path("me").path("best").intValue()).isEqualTo(20);
        assertThat(today.path("me").path("best").intValue()).isEqualTo(20);
        assertThat(json.readTree(get("/api/v1/climb/me", cookie).body()).path("run").path("best").intValue()).isZero();
    }

    @Test
    void 완주하면_명예의_전당에_먼저_도착한_순서로_한_줄씩_남고_같은_손님의_다음_완주는_더하지_않는다() throws Exception {
        // given
        HttpResponse<String> first = post("/api/v1/climb/guests", "{\"nickname\":\"일등\"}", null);
        HttpResponse<String> second = post("/api/v1/climb/guests", "{\"nickname\":\"이등\"}", null);
        String firstTag = json.readTree(first.body()).path("tag").stringValue();
        String secondTag = json.readTree(second.body()).path("tag").stringValue();

        // when
        finish(first, 400, 3);
        finish(second, 350, 1);
        finish(first, 320, 0);
        JsonNode hall = json.readTree(get("/api/v1/climb/hall?map=v1", null).body()).path("hall");

        // then
        List<JsonNode> mine = new ArrayList<>();
        hall.forEach(item -> {
            if (firstTag.equals(item.path("tag").stringValue()) || secondTag.equals(item.path("tag").stringValue())) {
                mine.add(item);
            }
        });
        assertThat(mine).hasSize(2);
        assertThat(mine.get(0).path("nickname").stringValue()).isEqualTo("일등");
        assertThat(mine.get(0).path("time").intValue()).isEqualTo(400);
        assertThat(mine.get(0).path("falls").intValue()).isEqualTo(3);
        assertThat(mine.get(0).path("character").stringValue()).isEqualTo("angry");
        assertThat(mine.get(1).path("nickname").stringValue()).isEqualTo("이등");
        assertThat(mine.get(0).path("order").intValue()).isLessThan(mine.get(1).path("order").intValue());
        assertThat(java.time.Instant.parse(mine.get(0).path("finishedAt").stringValue()))
                .isBefore(java.time.Instant.parse(mine.get(1).path("finishedAt").stringValue()));
        assertThat(jdbc.sql("SELECT count(*) FROM climb_hall WHERE tag = :tag").param("tag", firstTag)
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void 같은_손님이_더_빠르게_다시_완주하면_가장_빠른_기록만_바뀌고_순서와_처음_완주는_그대로다() throws Exception {
        // given
        HttpResponse<String> first = post("/api/v1/climb/guests", "{\"nickname\":\"어셔\"}", null);
        HttpResponse<String> second = post("/api/v1/climb/guests", "{\"nickname\":\"둘째\"}", null);
        String firstTag = json.readTree(first.body()).path("tag").stringValue();
        String secondTag = json.readTree(second.body()).path("tag").stringValue();

        // when: 처음 완주, 다른 손님, 더 빠른 재완주, 의심 완주(이벤트가 없어 DB 에 넣는다), 느린 재완주
        finish(first, 2705, 4);
        finish(second, 900, 1);
        finish(first, 716, 2);
        jdbc.sql("""
                INSERT INTO climb_run (id, guest_id, map_version, character, best_height, best_at, height, x, falls,
                    play_seconds, finished_at, finish_seconds, suspect, started_at, updated_at)
                VALUES (:id, :guest, 'v1', 'angry', 663, now(), 663, 0, 0, 0, now(), 200, true, now(), now())
                """)
                .param("id", java.util.UUID.randomUUID())
                .param("guest", java.util.UUID.fromString(json.readTree(first.body()).path("id").stringValue()))
                .update();
        finish(first, 1400, 0);
        JsonNode hall = json.readTree(get("/api/v1/climb/hall?map=v1", null).body()).path("hall");

        // then
        JsonNode mine = null;
        JsonNode other = null;
        for (JsonNode item : hall) {
            if (firstTag.equals(item.path("tag").stringValue())) {
                mine = item;
            }
            if (secondTag.equals(item.path("tag").stringValue())) {
                other = item;
            }
        }
        assertThat(mine).isNotNull();
        assertThat(other).isNotNull();
        assertThat(mine.path("time").intValue()).isEqualTo(2705);
        assertThat(mine.path("falls").intValue()).isEqualTo(4);
        assertThat(mine.path("best").intValue()).isEqualTo(716);
        assertThat(mine.path("bestFalls").intValue()).isEqualTo(2);
        assertThat(java.time.Instant.parse(mine.path("bestAt").stringValue()))
                .isAfter(java.time.Instant.parse(other.path("finishedAt").stringValue()));
        assertThat(mine.path("order").intValue()).isLessThan(other.path("order").intValue());
        assertThat(other.path("best").intValue()).isEqualTo(900);
        assertThat(other.path("bestFalls").intValue()).isEqualTo(1);
        assertThat(other.path("bestAt").stringValue()).isEqualTo(other.path("finishedAt").stringValue());
    }

    /** 꼭대기에 오른 등반을 넣고 이어 해서 완주한다. 2초에 20칸 상한 때문에 처음부터 오르면 오래 걸린다. */
    private void finish(HttpResponse<String> created, int time, int falls) throws Exception {
        jdbc.sql("""
                INSERT INTO climb_run (id, guest_id, map_version, character, best_height, best_at, height, x, falls,
                    play_seconds, started_at, updated_at)
                VALUES (:id, :guest, 'v1', 'angry', 663, now(), 663, 0, 0, 0, now(), now())
                """)
                .param("id", java.util.UUID.randomUUID())
                .param("guest", java.util.UUID.fromString(json.readTree(created.body()).path("id").stringValue()))
                .update();
        Socket socket = connect(cookie(created));
        hello(socket);
        socket.send("{\"t\":\"finish\",\"time\":" + time + ",\"falls\":" + falls + "}");
        socket.await("event", event -> "finish".equals(event.path("kind").stringValue()));
        socket.close();
    }

    @Test
    void 잘못된_메시지는_오류를_보내고_연결을_닫는다() throws Exception {
        // given
        String cookie = cookie(post("/api/v1/climb/guests", "{\"nickname\":\"히유\"}", null));
        Socket me = connect(cookie);

        // when
        me.send("{\"t\":\"pos\",\"h\":1,\"best\":1,\"falls\":0,\"time\":1,\"x\":0}");

        // then
        assertThat(me.await("error", error -> true).path("code").stringValue()).isEqualTo("HELLO_REQUIRED");
        assertThat(me.closedReason()).isEqualTo("1008 HELLO_REQUIRED");
    }

    @Test
    void 배포로_서버가_내려가면_진행을_저장하고_닫으며_다시_뜬_서버는_순위표를_DB에서_복원한다() throws Exception {
        // given
        ConfigurableApplicationContext before = startServer();
        int beforePort = serverPort(before);
        String cookie = cookie(client.send(request("http://127.0.0.1:" + beforePort + "/api/v1/climb/guests", null)
                .POST(HttpRequest.BodyPublishers.ofString("{\"nickname\":\"재시작\"}")).build(),
                HttpResponse.BodyHandlers.ofString()));
        Socket me = connect(cookie, GAME, beforePort);
        hello(me);
        me.send("{\"t\":\"pos\",\"h\":10,\"best\":10,\"falls\":0,\"time\":1,\"x\":0}");
        me.send("{\"t\":\"pos\",\"h\":17,\"best\":18,\"falls\":0,\"time\":2,\"x\":3}");
        me.await("rank", rank -> rank.path("me").path("best").intValue() == 18);

        // when
        before.close();
        ConfigurableApplicationContext after = startServer();

        // then
        try {
            assertThat(me.await("error", error -> true).path("code").stringValue()).isEqualTo("SERVER_DRAINING");
            assertThat(me.closedReason()).isEqualTo("1012 SERVER_DRAINING");
            HttpResponse<String> restored = client.send(request("http://127.0.0.1:" + serverPort(after)
                    + "/api/v1/climb/ranking?board=all&map=v1", cookie).GET().build(), HttpResponse.BodyHandlers.ofString());
            JsonNode ranking = json.readTree(restored.body());
            assertThat(ranking.path("me").path("best").intValue()).isEqualTo(18);
            assertThat(ranking.path("top")).anyMatch(item -> "재시작".equals(item.path("nickname").stringValue()));
        } finally {
            after.close();
        }
    }

    @Test
    void 등반을_켜도_기기_JWT_경로와_프레스_경쟁_웹소켓은_그대로_동작한다() throws Exception {
        // given
        Device device = devices.save(기본_기기_빌더().build());
        String token = tokens.issue(device.getPublicId()).accessToken();
        try {
            // when
            HttpResponse<String> withJwt = client.send(HttpRequest.newBuilder(uri("/api/v3/devices/me/notification-settings"))
                    .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> withoutJwt = client.send(HttpRequest.newBuilder(uri("/api/v3/devices/me/notification-settings"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> climbCookieOnDeviceApi = client.send(HttpRequest.newBuilder(uri("/api/v3/devices/me/notification-settings"))
                    .header("Cookie", cookie(post("/api/v1/climb/guests", "{\"nickname\":\"히유\"}", null)))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            Socket pressRace = new Socket();
            WebSocket socket = client.newWebSocketBuilder().header("Authorization", "Bearer " + token)
                    .buildAsync(URI.create("ws://127.0.0.1:" + port + "/api/v1/press-race"), pressRace)
                    .get(10, TimeUnit.SECONDS);
            sockets.add(socket);
            Throwable pressRaceWithoutJwt = catchThrowable(() -> client.newWebSocketBuilder()
                    .buildAsync(URI.create("ws://127.0.0.1:" + port + "/api/v1/press-race"), new Socket()).join());

            // then
            assertThat(withJwt.statusCode()).isEqualTo(200);
            assertThat(withoutJwt.statusCode()).isEqualTo(401);
            assertThat(climbCookieOnDeviceApi.statusCode()).isEqualTo(401);
            assertThat(pressRace.next().path("v").intValue(0)).isEqualTo(1);
            assertThat(handshakeStatus(pressRaceWithoutJwt)).isEqualTo(401);
        } finally {
            sockets.forEach(WebSocket::abort);
            jdbc.sql("DELETE FROM device_daily_presses WHERE device_id = :device").param("device", device.getId()).update();
            devices.deleteById(device.getId());
        }
    }

    @Test
    void 백팔십일_동안_접속하지_않은_손님과_기록을_지운다() throws Exception {
        // given
        HttpResponse<String> created = post("/api/v1/climb/guests", "{\"nickname\":\"오래전\"}", null);
        String stale = cookie(created);
        String active = cookie(post("/api/v1/climb/guests", "{\"nickname\":\"요즘\"}", null));
        Socket socket = connect(stale);
        hello(socket);
        socket.send("{\"t\":\"pos\",\"h\":5,\"best\":5,\"falls\":0,\"time\":1,\"x\":0}");
        socket.await("rank", rank -> rank.path("me").path("best").intValue() == 5);
        socket.close();
        jdbc.sql("UPDATE climb_guest SET last_seen_at = now() - interval '181 days' WHERE id = :id")
                .param("id", java.util.UUID.fromString(json.readTree(created.body()).path("id").stringValue()))
                .update();

        // when
        guestService.deleteInactive();

        // then
        assertThat(get("/api/v1/climb/me", stale).statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/climb/me", active).statusCode()).isEqualTo(200);
        JsonNode all = json.readTree(get("/api/v1/climb/ranking?board=all&map=v1", null).body());
        assertThat(all.path("top")).noneMatch(item -> "오래전".equals(item.path("nickname").stringValue()));
    }

    @Test
    void 한쪽이_자세를_보내면_가까이_있는_다른_쪽이_들어옴과_상태를_받는다() throws Exception {
        // given
        Socket me = climber("나나");
        Socket other = climber("너너");
        state(me, 640, 10);

        // when
        other.send("{\"t\":\"st\",\"s\":[300,12.25,-120,40,3,55]}");
        JsonNode states = me.await("sts", message -> message.path("s").size() > 0);

        // then
        JsonNode entered = in(me, "너너");
        assertThat(entered.path("character").stringValue()).isEqualTo("angry");
        assertThat(entered.path("tag").stringValue()).hasSize(4);
        assertThat(states.path("s").get(0).toString())
                .isEqualTo("[" + entered.path("n").intValue() + ",300,12.25,-120,40,3,55]");
    }

    @Test
    void 높이가_삼십_칸_넘게_벌어지면_나감을_받고_더_받지_않는다() throws Exception {
        // given
        Socket me = climber("나나");
        Socket other = climber("너너");
        state(me, 640, 10);
        state(other, 640, 20);
        me.await("sts", message -> message.path("s").size() > 0);
        int n = in(me, "너너").path("n").intValue();

        // when
        state(other, 640, 40.5);
        JsonNode out = me.await("out", message -> true);
        int before = me.received().size();
        state(other, 600, 41);
        Thread.sleep(400);

        // then
        assertThat(out.path("n").get(0).intValue()).isEqualTo(n);
        assertThat(me.received().subList(before, me.received().size()))
                .noneMatch(message -> "sts".equals(message.path("t").stringValue()));
    }

    @Test
    void 스물한_번째로_가까운_사람은_받지_않는다() throws Exception {
        // given
        Socket me = climber("나나");
        state(me, 640, 100);
        for (int k = 1; k <= 21; k++) {
            state(climber("남" + k), 640, 100 + k);
        }

        // when
        me.await("sts", message -> true);
        Thread.sleep(500);

        // then: 받은 들어옴·나감을 차례로 따라가 지금 보이는 사람을 만든다(연결 순서가 엇갈려 잠깐 보였다 나갈 수 있다)
        Map<Integer, String> seen = new HashMap<>();
        for (JsonNode message : me.received()) {
            if ("in".equals(message.path("t").stringValue())) {
                message.path("p").forEach(person -> seen.put(person.path("n").intValue(), person.path("nickname").stringValue()));
            } else if ("out".equals(message.path("t").stringValue())) {
                message.path("n").forEach(n -> seen.remove(n.intValue()));
            }
        }
        assertThat(seen.values()).hasSize(20).contains("남1", "남20").doesNotContain("남21");
    }

    @Test
    void 다른_쪽이_연결을_끊으면_나감을_받는다() throws Exception {
        // given
        Socket me = climber("나나");
        Socket other = climber("너너");
        state(me, 640, 10);
        state(other, 640, 11);
        me.await("sts", message -> message.path("s").size() > 0);
        int n = in(me, "너너").path("n").intValue();

        // when
        other.close();

        // then
        assertThat(me.await("out", message -> true).path("n").get(0).intValue()).isEqualTo(n);
    }

    @Test
    void 초당_열두_개를_넘긴_자세는_전달하지_않고_연결은_유지한다() throws Exception {
        // given
        Socket me = climber("나나");
        Socket other = climber("너너");
        state(me, 640, 10);

        // when
        for (int k = 1; k <= 19; k++) {
            state(other, k, 10);
        }
        state(other, 999, 10);
        Thread.sleep(1500);
        state(other, 777, 10);
        me.await("sts", message -> message.toString().contains(",777,"));

        // then
        assertThat(me.received()).noneMatch(message -> message.toString().contains(",999,"));
    }

    @Test
    void 자세를_계속_지나치게_보내면_연결을_닫는다() throws Exception {
        // given
        Socket other = climber("너너");

        // when
        for (int k = 0; k < 40; k++) {
            try {
                state(other, k, 10);
            } catch (java.util.concurrent.CompletionException closed) {
                break; // 서버가 이미 닫았다
            }
        }

        // then
        assertThat(other.await("error", error -> true).path("code").stringValue()).isEqualTo("MESSAGE_RATE_LIMITED");
    }

    @Test
    void 자세_메시지로는_순위가_바뀌지_않는다() throws Exception {
        // given
        Socket me = climber("나나");
        me.send("{\"t\":\"pos\",\"h\":5,\"best\":5,\"falls\":0,\"time\":1,\"x\":0}");
        me.await("rank", rank -> rank.path("me").path("best").intValue() == 5);

        // when
        state(me, 640, 15);
        Thread.sleep(1500);

        // then
        assertThat(me.received()).filteredOn(message -> "rank".equals(message.path("t").stringValue()))
                .allMatch(rank -> rank.path("me").path("best").intValue() <= 5);
        JsonNode online = json.readTree(get("/api/v1/climb/ranking?board=online&map=v1", null).body());
        assertThat(online.path("top").get(0).path("best").intValue()).isEqualTo(5);
    }

    @Test
    void 위치_공유를_끄면_자세는_오지_않고_순위는_계속_온다() throws Exception {
        // given
        ConfigurableApplicationContext off = startServer(Map.of("pheeeew.climb.presence.enabled", "false"));
        int offPort = serverPort(off);
        try {
            Socket me = connect(cookie(client.send(request("http://127.0.0.1:" + offPort + "/api/v1/climb/guests", null)
                    .POST(HttpRequest.BodyPublishers.ofString("{\"nickname\":\"나나\"}")).build(),
                    HttpResponse.BodyHandlers.ofString())), GAME, offPort);
            hello(me);
            Socket other = connect(cookie(client.send(request("http://127.0.0.1:" + offPort + "/api/v1/climb/guests", null)
                    .POST(HttpRequest.BodyPublishers.ofString("{\"nickname\":\"너너\"}")).build(),
                    HttpResponse.BodyHandlers.ofString())), GAME, offPort);
            hello(other);

            // when
            state(me, 640, 1);
            state(other, 640, 2);
            other.send("{\"t\":\"pos\",\"h\":3,\"best\":3,\"falls\":0,\"time\":1,\"x\":0}");
            me.await("rank", rank -> rank.path("top").get(0).path("best").intValue() == 3);
            Thread.sleep(300);

            // then
            assertThat(me.received()).noneMatch(message -> List.of("sts", "in", "out")
                    .contains(message.path("t").stringValue()));
        } finally {
            sockets.forEach(WebSocket::abort);
            off.close();
        }
    }

    @Test
    void 관리자_키가_설정되지_않으면_관리자_끝점은_모두_404다() throws Exception {
        // given
        ConfigurableApplicationContext noKey = startServer();
        String base = "http://127.0.0.1:" + serverPort(noKey);
        try {
            // when
            HttpResponse<String> open = post(base + ADMIN_SESSION, "{\"key\":\"\"}", null);
            HttpResponse<String> check = get(base + ADMIN_SESSION, null);
            HttpResponse<String> close = delete(base + ADMIN_SESSION, null);

            // then
            assertThat(List.of(open.statusCode(), check.statusCode(), close.statusCode())).containsOnly(404);
            assertThat(open.headers().firstValue("Set-Cookie")).isEmpty();
        } finally {
            noKey.close();
        }
    }

    @Test
    void 틀린_관리자_키는_401이고_쿠키를_주지_않는다() throws Exception {
        // when
        HttpResponse<String> response = post(ADMIN_SESSION, "{\"key\":\"wrong-key\"}", null);

        // then
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(json.readTree(response.body()).path("code").stringValue()).isEqualTo("ADMIN_KEY_MISMATCH");
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @Test
    void 맞는_관리자_키면_204와_관리자_쿠키를_주고_그_쿠키로만_세션이_확인된다() throws Exception {
        // when
        HttpResponse<String> opened = post(ADMIN_SESSION, "{\"key\":\"" + ADMIN_KEY + "\"}", null);

        // then
        assertThat(opened.statusCode()).isEqualTo(204);
        String setCookie = opened.headers().firstValue("Set-Cookie").orElseThrow();
        assertThat(setCookie).startsWith("climb_admin=").doesNotContain(ADMIN_KEY)
                .contains("Path=/api/v1/climb", "Max-Age=28800", "Secure", "HttpOnly", "SameSite=Lax");
        assertThat(get(ADMIN_SESSION, cookie(opened)).statusCode()).isEqualTo(204);
        assertThat(get(ADMIN_SESSION, null).statusCode()).isEqualTo(401);
        assertThat(get(ADMIN_SESSION, "climb_admin=forged").statusCode()).isEqualTo(401);
    }

    @Test
    void 관리자_키를_분당_다섯_번_넘게_틀리면_여섯_번째는_429다() throws Exception {
        // given
        ConfigurableApplicationContext limited = startServer(Map.of("pheeeew.climb.admin-key", ADMIN_KEY));
        String url = "http://127.0.0.1:" + serverPort(limited) + ADMIN_SESSION;
        try {
            // when
            List<Integer> statuses = new ArrayList<>();
            for (int attempt = 0; attempt < 6; attempt++) {
                statuses.add(post(url, "{\"key\":\"wrong-" + attempt + "\"}", null).statusCode());
            }
            HttpResponse<String> right = post(url, "{\"key\":\"" + ADMIN_KEY + "\"}", null);

            // then
            assertThat(statuses).containsExactly(401, 401, 401, 401, 401, 429);
            assertThat(right.statusCode()).isEqualTo(429);
        } finally {
            limited.close();
        }
    }

    @Test
    void 관리자_세션을_끝내면_쿠키를_지우고_그_쿠키는_더_쓸_수_없다() throws Exception {
        // given
        String admin = cookie(post(ADMIN_SESSION, "{\"key\":\"" + ADMIN_KEY + "\"}", null));

        // when
        HttpResponse<String> closed = delete(ADMIN_SESSION, admin);

        // then
        assertThat(closed.statusCode()).isEqualTo(204);
        assertThat(closed.headers().firstValue("Set-Cookie").orElseThrow())
                .startsWith("climb_admin=;").contains("Max-Age=0", "Path=/api/v1/climb");
        assertThat(get(ADMIN_SESSION, admin).statusCode()).isEqualTo(401);
    }

    @Test
    void 허용되지_않은_출처의_관리자_키_요청은_거절한다() throws Exception {
        // when
        HttpResponse<String> foreign = send(HttpRequest.newBuilder(uri(ADMIN_SESSION))
                .header("Content-Type", "application/json")
                .header("Origin", "https://foreign.example")
                .POST(HttpRequest.BodyPublishers.ofString("{\"key\":\"" + ADMIN_KEY + "\"}")));
        HttpResponse<String> preflight = client.send(HttpRequest.newBuilder(uri(ADMIN_SESSION))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "https://foreign.example")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .build(), HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(foreign.statusCode()).isEqualTo(403);
        assertThat(foreign.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(preflight.statusCode()).isEqualTo(403);
    }

    @Test
    void 관리자_쿠키가_있는_연결의_보고는_순위와_기록과_위치_공유에_넣지_않는다() throws Exception {
        // given
        HttpResponse<String> adminGuest = post("/api/v1/climb/guests", "{\"nickname\":\"관리\"}", null);
        String admin = cookie(adminGuest) + "; "
                + cookie(post(ADMIN_SESSION, "{\"key\":\"" + ADMIN_KEY + "\"}", null));
        Socket me = connect(admin);
        hello(me);
        Socket other = climber("손님");

        // when
        me.send("{\"t\":\"pos\",\"h\":15,\"best\":15,\"falls\":0,\"time\":2,\"x\":5}");
        me.send("{\"t\":\"restart\"}");
        state(me, 640, 3);
        other.send("{\"t\":\"pos\",\"h\":3,\"best\":3,\"falls\":0,\"time\":2,\"x\":5}");
        state(other, 640, 2);
        JsonNode rank = me.await("rank", message -> message.path("top").size() == 1);
        in(me, "손님");
        other.await("rank", message -> message.path("me").path("best").intValue() == 3);
        Thread.sleep(1500);

        // then
        assertThat(rank.path("top").get(0).path("nickname").stringValue()).isEqualTo("손님");
        assertThat(rank.path("me").isNull()).isTrue();
        assertThat(other.received()).filteredOn(message -> "rank".equals(message.path("t").stringValue()))
                .allMatch(message -> message.path("top").size() == 1);
        assertThat(other.received()).noneMatch(message -> "in".equals(message.path("t").stringValue()));
        JsonNode online = json.readTree(get("/api/v1/climb/ranking?board=online&map=v1", null).body());
        assertThat(online.path("top").size()).isEqualTo(1);
        String adminId = json.readTree(adminGuest.body()).path("id").stringValue();
        assertThat(jdbc.sql("SELECT count(*) FROM climb_run WHERE guest_id = :id")
                .param("id", java.util.UUID.fromString(adminId)).query(Long.class).single()).isZero();
    }

    private Socket climber(String nickname) throws Exception {
        Socket socket = connect(cookie(post("/api/v1/climb/guests", "{\"nickname\":\"" + nickname + "\"}", null)));
        hello(socket);
        return socket;
    }

    private static void state(Socket socket, int x, double h) {
        socket.send("{\"t\":\"st\",\"s\":[" + x + "," + h + ",0,0,1,0]}");
    }

    private static JsonNode in(Socket socket, String nickname) {
        return socket.received().stream()
                .filter(message -> "in".equals(message.path("t").stringValue()))
                .flatMap(message -> java.util.stream.StreamSupport.stream(message.path("p").spliterator(), false))
                .filter(person -> nickname.equals(person.path("nickname").stringValue()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(nickname + " 의 in 을 받지 못했다. 받은 것: " + socket.received()));
    }

    private ConfigurableApplicationContext startServer() {
        return startServer(Map.of());
    }

    private ConfigurableApplicationContext startServer(Map<String, String> extra) {
        Map<String, String> properties = new HashMap<>();
        properties.put("server.port", "0");
        properties.put("server.address", "127.0.0.1");
        properties.put("spring.datasource.url", SharedTestContainers.postgis().getJdbcUrl());
        properties.put("spring.datasource.username", SharedTestContainers.postgis().getUsername());
        properties.put("spring.datasource.password", SharedTestContainers.postgis().getPassword());
        properties.put("pheeeew.jwt.private-key-base64", environment.getRequiredProperty("pheeeew.jwt.private-key-base64"));
        properties.put("pheeeew.jwt.public-key-base64", environment.getRequiredProperty("pheeeew.jwt.public-key-base64"));
        properties.put("pheeeew.s3.bucket", "pheeeew-test");
        properties.put("pheeeew.s3.key-prefix", "pheeeew/test/");
        properties.put("pheeeew.s3.region", "ap-northeast-2");
        properties.put("pheeeew.climb.enabled", "true");
        properties.put("pheeeew.climb.allowed-origin", GAME);
        properties.putAll(extra);
        // 기본 속성은 application.yml 보다 우선순위가 낮아서 명령행 인자로 넘긴다.
        return new SpringApplicationBuilder(PheeeewApplication.class).profiles("test")
                .run(properties.entrySet().stream().map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                        .toArray(String[]::new));
    }

    private static int serverPort(ConfigurableApplicationContext context) {
        return ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private JsonNode hello(Socket socket) throws Exception {
        socket.send("{\"t\":\"hello\",\"v\":1,\"build\":2,\"map\":\"v1\",\"character\":\"angry\"}");
        return socket.await("welcome", message -> true);
    }

    private Socket connect(String cookie) throws Exception {
        return connect(cookie, GAME);
    }

    private Socket connect(String cookie, String origin) throws Exception {
        return connect(cookie, origin, port);
    }

    private Socket connect(String cookie, String origin, int serverPort) throws Exception {
        Socket listener = new Socket();
        WebSocket.Builder builder = client.newWebSocketBuilder().header("Origin", origin);
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        listener.socket = builder.buildAsync(URI.create("ws://127.0.0.1:" + serverPort + "/api/v1/climb/ws"), listener)
                .get(10, TimeUnit.SECONDS);
        sockets.add(listener.socket);
        return listener;
    }

    private static int handshakeStatus(Throwable failure) {
        Throwable cause = failure;
        while (cause != null && !(cause instanceof WebSocketHandshakeException)) {
            cause = cause.getCause();
        }
        assertThat(cause).isInstanceOf(WebSocketHandshakeException.class);
        return ((WebSocketHandshakeException) cause).getResponse().statusCode();
    }

    private class Socket implements WebSocket.Listener {

        private final BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        private final List<JsonNode> received = new ArrayList<>();
        private final CompletableFuture<String> closed = new CompletableFuture<>();
        private final StringBuilder partial = new StringBuilder();
        private WebSocket socket;

        void send(String text) {
            socket.sendText(text, true).join();
        }

        void close() throws Exception {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "").join();
            closed.get(10, TimeUnit.SECONDS);
        }

        JsonNode await(String type, Predicate<JsonNode> condition) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                JsonNode message = messages.poll(100, TimeUnit.MILLISECONDS);
                if (message != null && type.equals(message.path("t").stringValue()) && condition.test(message)) {
                    return message;
                }
            }
            throw new AssertionError(type + " 메시지를 받지 못했다. 받은 것: " + received);
        }

        JsonNode next() throws Exception {
            JsonNode message = messages.poll(10, TimeUnit.SECONDS);
            assertThat(message).isNotNull();
            return message;
        }

        synchronized List<JsonNode> received() {
            return List.copyOf(received);
        }

        String closedReason() throws Exception {
            return closed.get(10, TimeUnit.SECONDS);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                JsonNode message = json.readTree(partial.toString());
                partial.setLength(0);
                synchronized (this) {
                    received.add(message);
                }
                messages.add(message);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed.complete(statusCode + " " + reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closed.complete("error " + error);
        }
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

    private HttpResponse<String> delete(String path, String cookie) throws Exception {
        return send(request(path, cookie).DELETE());
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        return send(request(path, cookie).GET());
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path, String cookie) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(path.startsWith("http") ? URI.create(path) : uri(path))
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
