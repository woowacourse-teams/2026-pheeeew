package com.pheeeew.pressrace.presentation;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pheeeew.device.application.token.AccessTokenIssuer;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.pressrace.application.PressRaceService;
import com.pheeeew.pressrace.application.PressRankingReader;
import com.pheeeew.pressrace.domain.PressRanking;
import java.time.LocalDate;
import java.time.Instant;
import static org.mockito.Mockito.doReturn;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.pheeeew.support.SharedJwtTestConfiguration;
import com.pheeeew.support.SharedPostgisTestConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
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
        "pheeeew.s3.key-prefix=pheeeew/test/", "pheeeew.s3.region=ap-northeast-2"
})
class PressRaceWebSocketIntegrationTest {

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private AccessTokenIssuer tokens;
    @Autowired
    private PressRaceService service;

    @Autowired
    private org.springframework.scheduling.TaskScheduler taskScheduler;

    @MockitoSpyBean
    private PressRankingReader rankingReader;

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<WebSocket> sockets = new ArrayList<>();
    private final List<Long> createdDevices = new ArrayList<>();
    private HttpClient client;
    private Device device;
    private String token;

    @BeforeEach
    void setUp() {
        client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        device = devices.save(기본_기기_빌더().build());
        createdDevices.add(device.getId());
        token = tokens.issue(device.getPublicId()).accessToken();
        activateRace();
    }

    @AfterEach
    void tearDown() {
        sockets.forEach(WebSocket::abort);
        client.close();
        for (Long deviceId : createdDevices) {
            jdbc.sql("DELETE FROM device_daily_presses WHERE device_id = :device").param("device", deviceId).update();
            devices.deleteById(deviceId);
        }
        jdbc.sql("UPDATE press_race_control SET active_server_generation = NULL, enabled = FALSE, activation_date = NULL WHERE id = TRUE").update();
    }

    @Test
    void 입력하지_않는_관찰자도_다른_WS와_HTTP_입력의_순위와_활동을_받는다() throws Exception {
        // given
        SocketListener observer = connect(token);
        JsonNode observerReady = initialize(observer);
        JsonNode initial = observer.snapshot(state -> state.path("baseline").booleanValue());
        assertThat(initial.path("me").path("participating").booleanValue()).isFalse();
        Device sender = devices.save(기본_기기_빌더().build());
        createdDevices.add(sender.getId());
        SocketListener writer = connect(tokens.issue(sender.getPublicId()).accessToken());
        JsonNode writerReady = initialize(writer);

        // when
        writer.socket.sendText(submit(UUID.randomUUID(), writerReady, "{\"ANGRY\":3}"), true).join();
        assertThat(writer.receive().path("status").stringValue()).isEqualTo("APPLIED");
        JsonNode updated = observer.snapshot(state -> state.path("nearby").size() == 1);
        assertThat(pressHttp("{\"EXHAUSTED\":2}").statusCode()).isEqualTo(200);
        JsonNode httpUpdated = observer.snapshot(state -> state.path("me").path("participating").booleanValue());

        // then
        assertThat(updated.path("nearby").get(0).path("score").longValue()).isEqualTo(3);
        assertThat(updated.path("activeCount").intValue()).isEqualTo(1);
        assertThat(httpUpdated.path("activeCount").intValue()).isEqualTo(2);
        assertThat(httpUpdated.path("me").path("score").longValue()).isEqualTo(2);
        assertThat(httpUpdated.path("me").path("rank").longValue()).isEqualTo(2);
        assertThat(httpUpdated.path("snapshotVersion").isString()).isTrue();
        assertThat(Long.parseLong(httpUpdated.path("snapshotVersion").stringValue()))
                .isGreaterThan(Long.parseLong(initial.path("snapshotVersion").stringValue()));
        assertThat(httpUpdated.path("connectionGeneration")).isEqualTo(observerReady.path("connectionGeneration"));
        assertThat(httpUpdated.toString()).doesNotContain(device.getPublicId().toString(), sender.getPublicId().toString(),
                "deviceId", "changeNo", "reachOrder");
    }

    @Test
    void 화면을_바꾸면_같은_연결에서_더_큰_버전의_완전한_기준을_받고_동일_내용은_반복하지_않는다() throws Exception {
        // given
        SocketListener observer = connect(token);
        JsonNode ready = initialize(observer);
        JsonNode press = observer.snapshot(state -> state.path("view").stringValue().equals("PRESS"));

        // when
        observer.socket.sendText("{\"v\":1,\"type\":\"view.change\",\"view\":\"ALL\"}", true).join();
        JsonNode all = observer.snapshot(state -> state.path("view").stringValue().equals("ALL"));
        observer.socket.sendText("{\"v\":1,\"type\":\"view.change\",\"view\":\"PRESS\"}", true).join();
        JsonNode back = observer.snapshot(state -> state.path("view").stringValue().equals("PRESS"));

        // then
        assertThat(all.path("baseline").booleanValue()).isTrue();
        assertThat(back.path("baseline").booleanValue()).isTrue();
        assertThat(Long.parseLong(all.path("snapshotVersion").stringValue()))
                .isGreaterThan(Long.parseLong(press.path("snapshotVersion").stringValue()));
        assertThat(Long.parseLong(back.path("snapshotVersion").stringValue()))
                .isGreaterThan(Long.parseLong(all.path("snapshotVersion").stringValue()));
        assertThat(back.path("connectionGeneration")).isEqualTo(ready.path("connectionGeneration"));
        assertThat(observer.snapshots.poll(800, TimeUnit.MILLISECONDS)).as("번호만 바뀐 동일 화면은 생략").isNull();
    }

    @Test
    void 중복은_활동을_연장하지_않고_닉네임과_마지막_활동_만료는_입력_없이_반영된다() throws Exception {
        // given
        SocketListener observer = connect(token);
        JsonNode ready = initialize(observer);
        String message = submit(UUID.randomUUID(), ready, "{\"ANGRY\":1}");
        observer.socket.sendText(message, true).join();
        assertThat(observer.receive().path("status").stringValue()).isEqualTo("APPLIED");
        observer.snapshot(state -> state.path("activeCount").intValue() == 1);
        Instant appliedAt = jdbc.sql("SELECT last_applied_at FROM press_daily_scores WHERE device_id = :id")
                .param("id", device.getId()).query(java.time.OffsetDateTime.class).single().toInstant();

        // when
        observer.socket.sendText(message, true).join();
        assertThat(observer.receive().path("status").stringValue()).isEqualTo("APPLIED");
        jdbc.sql("UPDATE devices SET nickname = '새닉네임' WHERE id = :id").param("id", device.getId()).update();
        JsonNode renamed = observer.snapshot(state -> state.path("me").path("nickname").stringValue().equals("새닉네임"));
        JsonNode expired = observer.snapshot(state -> state.path("activeCount").intValue() == 0);

        // then
        assertThat(renamed.path("me").path("score").longValue()).isEqualTo(1);
        assertThat(expired.path("baseline").booleanValue()).isFalse();
        assertThat(expired.path("me").path("participating").booleanValue()).isTrue();
        assertThat(jdbc.sql("SELECT last_applied_at FROM press_daily_scores WHERE device_id = :id")
                .param("id", device.getId()).query(java.time.OffsetDateTime.class).single().toInstant()).isEqualTo(appliedAt);
        assertThat(Instant.parse(expired.path("generatedAt").stringValue())).isAfterOrEqualTo(appliedAt.plusSeconds(5));
    }

    @Test
    void 공동_100위_목록을_확장하지_않고_100명_밖의_me와_주변을_전달한다() throws Exception {
        // given
        for (int index = 0; index < 104; index++) {
            Device participant = devices.save(기본_기기_빌더().build());
            createdDevices.add(participant.getId());
            seedScore(participant, 10, new UUID(0, index + 1));
        }
        seedScore(device, 5, UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
        SocketListener observer = connect(token);

        // when
        observer.socket.sendText("{\"v\":1,\"type\":\"session.init\",\"view\":\"ALL\",\"pending\":[]}", true).join();
        assertThat(observer.receive().path("type").stringValue()).isEqualTo("session.ready");
        JsonNode state = observer.snapshot(snapshot -> snapshot.path("top100").size() == 100);

        // then
        assertThat(state.path("me").path("rank").longValue()).isEqualTo(105);
        assertThat(state.path("nearby").size()).isEqualTo(3);
        assertThat(state.path("nearby").get(2)).isEqualTo(state.path("me"));
        assertThat(state.path("top100").get(99).path("rank").longValue()).isEqualTo(1);
        assertThat(state.path("top100").toString()).doesNotContain(state.path("me").path("publicId").stringValue());
    }

    @Test
    void 날짜_전환을_모의하면_실제_WS가_입력_없이_새날_미참여_기준을_받는다() throws Exception {
        // given: DB의 실제 KST 날짜 조회와 분리해 WS의 자정 전환 경계를 결정적으로 검증한다.
        seedScore(device, 3, UUID.randomUUID());
        SocketListener observer = connect(token);
        initialize(observer);
        JsonNode today = observer.snapshot(state -> state.path("me").path("participating").booleanValue());
        LocalDate tomorrow = LocalDate.parse(today.path("date").stringValue()).plusDays(1);
        PressRanking midnight = PressRanking.of(tomorrow, tomorrow.atStartOfDay(java.time.ZoneId.of("Asia/Seoul")).toInstant(), List.of());

        // when
        doReturn(midnight).when(rankingReader).read();
        JsonNode nextDay = observer.snapshot(state -> state.path("date").stringValue().equals(tomorrow.toString()));

        // then
        assertThat(nextDay.path("baseline").booleanValue()).isTrue();
        assertThat(nextDay.path("me").path("participating").booleanValue()).isFalse();
        assertThat(nextDay.path("me").path("rank").isNull()).isTrue();
        assertThat(nextDay.path("me").path("score").longValue()).isZero();
        assertThat(nextDay.path("activeCount").intValue()).isZero();
        assertThat(Long.parseLong(nextDay.path("snapshotVersion").stringValue()))
                .isGreaterThan(Long.parseLong(today.path("snapshotVersion").stringValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RACE_DISABLED", "SERVER_REPLACED"})
    void 입력_없는_READY_관찰자도_기능_중단이나_서버_교체시_종료된다(String code) throws Exception {
        // given
        SocketListener observer = connect(token);
        initialize(observer);
        observer.snapshot(state -> state.path("baseline").booleanValue());

        // when
        if (code.equals("RACE_DISABLED")) {
            jdbc.sql("UPDATE press_race_control SET enabled = FALSE").update();
        } else {
            jdbc.sql("UPDATE press_race_control SET active_server_generation = :generation")
                    .param("generation", UUID.randomUUID()).update();
        }

        // then
        assertThat(observer.closed.poll(10, TimeUnit.SECONDS)).isEqualTo(1008);
        assertThat(observer.closeReasons.poll(10, TimeUnit.SECONDS)).isEqualTo(code);
    }

    @Test
    void 같은_연결에서_다시_초기화해도_ready_뒤_새로운_기준만_보낸다() throws Exception {
        // given
        SocketListener observer = connect(token);
        initialize(observer);
        JsonNode first = observer.snapshot(state -> state.path("baseline").booleanValue());

        // when
        initialize(observer);
        JsonNode second = observer.snapshot(state -> state.path("baseline").booleanValue());

        // then
        assertThat(Long.parseLong(second.path("snapshotVersion").stringValue()))
                .isGreaterThan(Long.parseLong(first.path("snapshotVersion").stringValue()));
        assertThat(second.path("connectionGeneration")).isEqualTo(first.path("connectionGeneration"));
    }

    @Test
    void 기본_스케줄러가_정리_작업으로_막혀도_순위_전체_조회는_계속된다() throws Exception {
        // given
        SocketListener observer = connect(token);
        JsonNode ready = initialize(observer);
        observer.snapshot(state -> state.path("baseline").booleanValue());
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var cleanup = taskScheduler.schedule(() -> {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }, Instant.now());

        // when
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            observer.socket.sendText(submit(UUID.randomUUID(), ready, "{\"ANGRY\":1}"), true).join();
            assertThat(observer.receive().path("status").stringValue()).isEqualTo("APPLIED");
            JsonNode state = observer.snapshot(snapshot -> snapshot.path("me").path("score").longValue() == 1);

            // then
            assertThat(state.path("activeCount").intValue()).isEqualTo(1);
            assertThat(release.getCount()).isEqualTo(1);
            assertThat(cleanup.isDone()).as("정리 작업이 여전히 차단된 동안 받은 순위").isFalse();
        } finally {
            release.countDown();
            cleanup.cancel(true);
        }
    }

    private void seedScore(Device participant, long score, UUID publicId) {
        jdbc.sql("""
                INSERT INTO press_daily_scores(device_id, press_date, total, last_applied_at, public_id)
                VALUES (:id, (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date, :score, clock_timestamp(), :publicId)
                """).param("id", participant.getId()).param("score", score).param("publicId", publicId).update();
    }

    @Test
    void 인증_없는_실제_업그레이드와_쿼리_토큰을_거절한다() throws Exception {
        // given
        URI endpoint = URI.create("http://127.0.0.1:" + port + "/api/v1/press-race");

        // when
        HttpResponse<String> unauthenticated = client.send(HttpRequest.newBuilder(endpoint).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> queryToken = client.send(HttpRequest.newBuilder(
                URI.create(endpoint + "?access_token=" + token)).GET().build(), HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(unauthenticated.statusCode()).isEqualTo(401);
        assertThat(queryToken.statusCode()).isEqualTo(401);
    }

    @Test
    void 인증된_입력과_재전송과_조회는_같은_커밋_결과이고_내용_충돌은_가산하지_않는다() throws Exception {
        // given
        SocketListener listener = connect(token);
        JsonNode ready = initialize(listener);
        UUID requestId = UUID.randomUUID();
        String submission = submit(requestId, ready, "{\"ANGRY\":3,\"EXHAUSTED\":2}");

        // when
        listener.socket.sendText(submission, true).join();
        JsonNode applied = listener.receive();
        listener.socket.sendText(submission, true).join();
        JsonNode duplicate = listener.receive();
        listener.socket.sendText("{\"v\":1,\"type\":\"press.status\",\"requestIds\":[\"" + requestId + "\"]}", true).join();
        JsonNode status = listener.receive();
        listener.socket.sendText(submit(requestId, ready, "{\"ANGRY\":4}"), true).join();
        JsonNode conflict = listener.receive();

        // then
        assertThat(applied.path("status").stringValue()).isEqualTo("APPLIED");
        assertThat(applied.has("reachOrder")).isFalse();
        assertThat(applied.has("changeNo")).isFalse();
        assertThat(ready.has("reachOrder")).isFalse();
        assertThat(ready.has("changeNo")).isFalse();
        assertThat(duplicate).isEqualTo(applied);
        assertThat(status).isEqualTo(applied);
        assertThat(conflict.path("type").stringValue()).isEqualTo("protocol.error");
        assertThat(conflict.path("code").stringValue()).isEqualTo("INPUT_CONFLICT");
        assertThat(conflict.path("requestId").stringValue()).isEqualTo(requestId.toString());
        assertThat(conflict.has("status")).isFalse();
        listener.socket.sendText("{\"v\":1,\"type\":\"press.status\",\"requestIds\":[\"" + requestId + "\"]}", true).join();
        assertThat(listener.receive()).isEqualTo(applied);
        assertThat(totalPresses()).isEqualTo(5);
        assertThat(applied.toString()).doesNotContain(device.getPublicId().toString(), "deviceId");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"ANGRY\":1.5}", "{\"ANGRY\":\"1\"}", "{\"UNKNOWN\":1}", "{\"ANGRY\":2147483648}"
    })
    void 잘못된_입력은_프로토콜_오류이며_점수를_저장하지_않는다(String counts) throws Exception {
        // given
        SocketListener listener = connect(token);
        JsonNode ready = initialize(listener);

        // when
        listener.socket.sendText(submit(UUID.randomUUID(), ready, counts), true).join();
        JsonNode result = listener.receive();

        // then
        assertThat(result.path("type").stringValue()).isEqualTo("protocol.error");
        assertThat(result.path("code").stringValue()).isEqualTo("INVALID_INPUT");
        assertThat(totalPresses()).isZero();
    }

    @ParameterizedTest
    @CsvSource(value = {"{}|INVALID_INPUT", "{\"ANGRY\":-1}|INVALID_INPUT", "{\"ANGRY\":101}|INPUT_TOO_LARGE"}, delimiter = '|')
    void 해석_가능한_횟수_거절은_영속_최종_결과로_반환한다(String counts, String code) throws Exception {
        // given
        SocketListener listener = connect(token);
        JsonNode ready = initialize(listener);
        UUID requestId = UUID.randomUUID();
        String message = submit(requestId, ready, counts);

        // when
        listener.socket.sendText(message, true).join();
        JsonNode result = listener.receive();
        listener.socket.sendText(message, true).join();
        JsonNode duplicate = listener.receive();

        // then
        assertThat(result.path("type").stringValue()).isEqualTo("input.result");
        assertThat(result.path("status").stringValue()).isEqualTo("REJECTED");
        assertThat(result.path("code").stringValue()).isEqualTo(code);
        assertThat(duplicate).isEqualTo(result);
        assertThat(result.has("appliedDate")).isFalse();
        assertThat(result.has("appliedCounts")).isFalse();
        assertThat(totalPresses()).isZero();
    }

    @Test
    void 본문_기기_사칭과_연결_세대_위조를_거절한다() throws Exception {
        // given
        SocketListener listener = connect(token);
        JsonNode ready = initialize(listener);
        String submission = submit(UUID.randomUUID(), ready, "{\"ANGRY\":1}");
        String spoof = submission.substring(0, submission.length() - 1) + ",\"deviceId\":\"" + UUID.randomUUID() + "\"}";

        // when
        listener.socket.sendText(spoof, true).join();
        JsonNode spoofResult = listener.receive();
        listener.socket.sendText(submission.replace(ready.path("connectionGeneration").stringValue(), UUID.randomUUID().toString()), true).join();
        JsonNode generationResult = listener.receive();

        // then
        assertThat(spoofResult.path("code").stringValue()).isEqualTo("INVALID_INPUT");
        assertThat(generationResult.path("code").stringValue()).isEqualTo("CONNECTION_REPLACED");
        assertThat(totalPresses()).isZero();
    }

    @Test
    void 다른_인증_기기는_입력_결과를_조회할_수_없다() throws Exception {
        // given
        SocketListener owner = connect(token);
        JsonNode ready = initialize(owner);
        UUID requestId = UUID.randomUUID();
        owner.socket.sendText(submit(requestId, ready, "{\"ANGRY\":1}"), true).join();
        assertThat(owner.receive().path("status").stringValue()).isEqualTo("APPLIED");
        Device other = devices.save(기본_기기_빌더().build());
        createdDevices.add(other.getId());
        SocketListener stranger = connect(tokens.issue(other.getPublicId()).accessToken());
        initialize(stranger);

        // when
        stranger.socket.sendText("{\"v\":1,\"type\":\"press.status\",\"requestIds\":[\"" + requestId + "\"]}", true).join();

        // then
        assertThat(stranger.receive().path("status").stringValue()).isEqualTo("NOT_FOUND");
    }

    @Test
    void 중복_JSON_키는_손상된_입력으로_종료한다() throws Exception {
        // given
        SocketListener listener = connect(token);
        initialize(listener);

        // when
        listener.socket.sendText("{\"v\":1,\"v\":1,\"type\":\"press.status\",\"requestIds\":[]}", true).join();

        // then
        assertThat(listener.receive().path("code").stringValue()).isEqualTo("INVALID_JSON");
        assertThat(listener.closed.poll(5, TimeUnit.SECONDS)).isEqualTo(1007);
    }

    @Test
    void 새_연결이_기존_소켓을_교체하고_새_고정_세대만_사용한다() throws Exception {
        // given
        SocketListener previous = connect(token);
        JsonNode previousReady = initialize(previous);

        // when
        SocketListener current = connect(token);
        JsonNode currentReady = initialize(current);

        // then
        assertThat(previous.closed.poll(5, TimeUnit.SECONDS)).isEqualTo(1008);
        assertThat(currentReady.path("connectionGeneration").stringValue())
                .isNotEqualTo(previousReady.path("connectionGeneration").stringValue());
        current.socket.sendText(submit(UUID.randomUUID(), currentReady, "{\"ANGRY\":1}"), true).join();
        assertThat(current.receive().path("status").stringValue()).isEqualTo("APPLIED");
    }

    @Test
    void 분할_프레임도_완성_메시지_16KiB_제한을_우회하지_못한다() throws Exception {
        // given
        SocketListener listener = connect(token);
        initialize(listener);
        String message = "{\"v\":1,\"type\":\"press.status\",\"requestIds\":[]}" + " ".repeat(17 * 1024);

        // when
        listener.socket.sendText(message.substring(0, 9 * 1024), false).join();
        listener.socket.sendText(message.substring(9 * 1024), true).join();

        // then
        assertThat(listener.closed.poll(5, TimeUnit.SECONDS)).isEqualTo(1009);
        assertThat(totalPresses()).isZero();
    }

    @Test
    void 인증된_HTTP와_WS는_같은_프레스_허용량을_소비하고_빈_HTTP는_조회만_한다() throws Exception {
        // given: WS가 공통 버스트 100회를 먼저 사용한다.
        SocketListener listener = connect(token);
        JsonNode ready = initialize(listener);
        listener.socket.sendText(submit(UUID.randomUUID(), ready, "{\"ANGRY\":100}"), true).join();
        JsonNode wsApplied = listener.receive();
        assertThat(wsApplied.path("appliedCounts").path("ANGRY").intValue()).isEqualTo(100);
        assertThat(totalPresses()).isEqualTo(100);
        pauseRefill();

        // when: HTTP의 기존 절삭 결과 100회도 같은 잔여량 검사를 받는다.
        HttpResponse<String> limitedHttp = pressHttp("{\"ANGRY\":999,\"DISCOURAGED\":999,\"EXHAUSTED\":999,\"FRUSTRATED\":999,\"IRRITATED\":999}");
        HttpResponse<String> emptyHttp = pressHttp("{}");

        // then
        assertThat(limitedHttp.statusCode()).isEqualTo(429);
        assertThat(limitedHttp.headers().firstValue("Retry-After")).isPresent();
        assertThat(Integer.parseInt(limitedHttp.headers().firstValue("Retry-After").orElseThrow())).isPositive();
        assertThat(emptyHttp.statusCode()).isEqualTo(200);
        assertThat(json.readTree(emptyHttp.body()).path("counts").path("ANGRY").longValue()).isEqualTo(100);
        assertThat(json.readTree(emptyHttp.body()).path("total").longValue()).isEqualTo(100);
        assertThat(totalPresses()).isEqualTo(100);

        // given: 새 기기는 HTTP 절삭 결과 30회를 먼저 사용한다.
        device = devices.save(기본_기기_빌더().build());
        createdDevices.add(device.getId());
        token = tokens.issue(device.getPublicId()).accessToken();
        HttpResponse<String> appliedHttp = pressHttp("{\"ANGRY\":999}");
        assertThat(appliedHttp.statusCode()).isEqualTo(200);
        assertThat(json.readTree(appliedHttp.body()).path("counts").path("ANGRY").longValue()).isEqualTo(30);
        assertThat(json.readTree(appliedHttp.body()).path("total").longValue()).isEqualTo(30);
        SocketListener afterHttp = connect(token);
        JsonNode afterHttpReady = initialize(afterHttp);
        pauseRefill();
        UUID requestId = UUID.randomUUID();
        String submission = submit(requestId, afterHttpReady, "{\"ANGRY\":100}");

        // when
        afterHttp.socket.sendText(submission, true).join();
        JsonNode rejected = afterHttp.receive();
        afterHttp.socket.sendText(submission, true).join();
        JsonNode duplicate = afterHttp.receive();

        // then
        assertThat(rejected.path("status").stringValue()).isEqualTo("REJECTED");
        assertThat(rejected.path("code").stringValue()).isEqualTo("RATE_LIMITED");
        assertThat(rejected.has("appliedCounts")).isFalse();
        assertThat(duplicate).isEqualTo(rejected);
        assertThat(totalPresses()).isEqualTo(30);
    }

    @Test
    void 유효한_인증도_외부_브라우저_Origin으로는_업그레이드할_수_없다() {
        // given
        URI endpoint = URI.create("ws://127.0.0.1:" + port + "/api/v1/press-race");

        // when
        Throwable failure = catchThrowable(() -> client.newWebSocketBuilder()
                .header("Authorization", "Bearer " + token)
                .header("Origin", "https://foreign.example")
                .buildAsync(endpoint, new SocketListener()).join());

        // then
        assertThat(failure).isNotNull();
        assertThat(failure.getCause()).isInstanceOfSatisfying(WebSocketHandshakeException.class,
                exception -> assertThat(exception.getResponse().statusCode()).isEqualTo(403));
        assertThat(totalPresses()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"RACE_DISABLED", "SERVER_REPLACED"})
    void 새_연결이_거절되면_실제_사유를_반환하고_기존_연결을_유지한다(String code) throws Exception {
        // given
        SocketListener previous = connect(token);
        // 아직 관찰을 시작하지 않은 연결로, 새 연결 거절 자체가 기존 연결을 교체하지 않는지 분리한다.
        // READY 관찰자의 전역 중단·서버 교체 종료는 별도 테스트에서 확인한다.
        previous.socket.sendText("{\"v\":1,\"type\":\"view.change\",\"view\":\"PRESS\"}", true).join();
        assertThat(previous.receive().path("code").stringValue()).isEqualTo("SESSION_NOT_READY");
        UUID originalGeneration = jdbc.sql("SELECT connection_generation FROM press_device_controls WHERE device_id = :device")
                .param("device", device.getId()).query(UUID.class).single();
        if (code.equals("RACE_DISABLED")) {
            jdbc.sql("UPDATE press_race_control SET enabled = FALSE WHERE id = TRUE").update();
        } else {
            jdbc.sql("UPDATE press_race_control SET active_server_generation = :generation WHERE id = TRUE")
                    .param("generation", UUID.randomUUID()).update();
        }

        // when
        SocketListener rejected = connect(token);
        JsonNode error = rejected.receive();

        // then
        assertThat(error.path("type").stringValue()).isEqualTo("protocol.error");
        assertThat(error.path("code").stringValue()).isEqualTo(code);
        assertThat(rejected.closed.poll(10, TimeUnit.SECONDS)).isEqualTo(1008);
        assertThat(rejected.closeReasons.poll(10, TimeUnit.SECONDS)).isEqualTo(code);
        assertThat(jdbc.sql("SELECT connection_generation FROM press_device_controls WHERE device_id = :device")
                .param("device", device.getId()).query(UUID.class).single())
                .isEqualTo(originalGeneration);
        assertThat(previous.closed).isEmpty();

        // when: 연결 허용 조건을 복구하면 기존 연결로 계속 입력할 수 있다.
        activateRace();
        JsonNode ready = initialize(previous);
        previous.socket.sendText(submit(UUID.randomUUID(), ready, "{\"ANGRY\":1}"), true).join();

        // then
        assertThat(previous.receive().path("status").stringValue()).isEqualTo("APPLIED");
        assertThat(totalPresses()).isEqualTo(1);
    }

    private HttpResponse<String> pressHttp(String counts) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v2/emotions/presses"))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"counts\":" + counts + "}"))
                        .build(), HttpResponse.BodyHandlers.ofString());
    }

    private void pauseRefill() {
        // 실제 소비량을 보존하고 충전 시각만 미래로 고정해 테스트 실행 지연에 영향받지 않는다.
        jdbc.sql("UPDATE press_device_controls SET refilled_at = clock_timestamp() + INTERVAL '1 day' WHERE device_id = :device")
                .param("device", device.getId()).update();
    }

    private void activateRace() {
        // 공통 제어 행은 서버가 자동으로 선점하지 않는다. 테스트만 명시적으로 활성화한다.
        jdbc.sql("UPDATE press_race_control SET active_server_generation = :generation, enabled = true, activation_date = CURRENT_DATE WHERE id = TRUE")
                .param("generation", service.serverGeneration()).update();
    }

    private long totalPresses() {
        return jdbc.sql("SELECT COALESCE(SUM(press_count), 0) FROM device_daily_presses WHERE device_id = :device")
                .param("device", device.getId()).query(Long.class).single();
    }

    private SocketListener connect(String accessToken) throws Exception {
        SocketListener listener = new SocketListener();
        listener.socket = client.newWebSocketBuilder().header("Authorization", "Bearer " + accessToken)
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/api/v1/press-race"), listener)
                .get(10, TimeUnit.SECONDS);
        sockets.add(listener.socket);
        return listener;
    }

    private JsonNode initialize(SocketListener listener) throws Exception {
        listener.socket.sendText("{\"v\":1,\"type\":\"session.init\",\"view\":\"PRESS\",\"pending\":[]}", true).join();
        JsonNode result = listener.receive();
        assertThat(result.path("type").stringValue()).isEqualTo("session.ready");
        return result;
    }

    private String submit(UUID requestId, JsonNode ready, String counts) {
        return "{\"v\":1,\"type\":\"press.submit\",\"requestId\":\"" + requestId
                + "\",\"connectionGeneration\":\"" + ready.path("connectionGeneration").stringValue() + "\",\"counts\":" + counts + "}";
    }

    private class SocketListener implements WebSocket.Listener {

        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final BlockingQueue<String> snapshots = new LinkedBlockingQueue<>();
        private final BlockingQueue<Integer> closed = new LinkedBlockingQueue<>();
        private final BlockingQueue<String> closeReasons = new LinkedBlockingQueue<>();
        private final StringBuilder fragments = new StringBuilder();
        private WebSocket socket;

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            fragments.append(data);
            if (last) {
                String message = fragments.toString();
                if (json.readTree(message).path("type").stringValue().equals("state.snapshot")) {
                    snapshots.add(message);
                } else {
                    messages.add(message);
                }
                fragments.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closeReasons.add(reason);
            closed.add(statusCode);
            return null;
        }

        private JsonNode snapshot(java.util.function.Predicate<JsonNode> matches) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                String message = snapshots.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                assertThat(message).as("수신 전용 관찰자의 자동 스냅샷").isNotNull();
                JsonNode state = json.readTree(message);
                if (matches.test(state)) {
                    return state;
                }
            }
            throw new AssertionError("기대한 순위 상태를 받지 못함");
        }

        private JsonNode receive() throws Exception {
            String message = messages.poll(10, TimeUnit.SECONDS);
            assertThat(message).as("실제 WebSocket 응답").isNotNull();
            return json.readTree(message);
        }
    }
}
