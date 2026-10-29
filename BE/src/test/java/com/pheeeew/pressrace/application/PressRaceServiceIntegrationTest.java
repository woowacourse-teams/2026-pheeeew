package com.pheeeew.pressrace.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.command.EmotionPressService;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.pressrace.domain.PendingPress;
import com.pheeeew.pressrace.domain.PressConnection;
import com.pheeeew.pressrace.domain.PressInputResult;
import com.pheeeew.pressrace.exception.PressRaceException;
import com.pheeeew.pressrace.infra.PressRaceRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Import({PressRaceService.class, PressRaceRepository.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PressRaceServiceIntegrationTest {

    @Autowired
    private PressRaceService service;

    @Autowired
    private EmotionPressService httpService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ApplicationContext applicationContext;

    private final List<Device> devices = new ArrayList<>();

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                UPDATE press_race_control SET active_server_generation = :generation,
                    enabled = true, activation_date = (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date
                """).param("generation", service.serverGeneration()).update();
    }

    @AfterEach
    void tearDown() {
        for (Device device : devices) {
            jdbc.sql("DELETE FROM device_daily_presses WHERE device_id = :device")
                    .param("device", device.getId()).update();
            deviceRepository.deleteById(device.getId());
        }
        jdbc.sql("""
                UPDATE press_race_control SET active_server_generation = NULL,
                    enabled = false, activation_date = NULL
                """).update();
    }

    @Test
    void 같은_입력을_동시에_보내도_집계와_투영에_한_번만_반영한다() throws Exception {
        // given
        Device device = device();
        PressConnection connection = ready(device);
        UUID requestId = UUID.randomUUID();
        var executor = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<PressInputResult>> futures = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                futures.add(executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return service.submit(connection, requestId, Map.of(EmotionState.ANGRY, 7));
                }));
            }

            // when
            start.countDown();
            List<PressInputResult> results = new ArrayList<>();
            for (Future<PressInputResult> future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }

            // then
            assertThat(results).allSatisfy(result -> assertThat(result.status()).isEqualTo("APPLIED"));
            assertThat(results).containsOnly(results.getFirst());
            assertThat(total(device)).isEqualTo(7);
            assertThat(projectionTotal(device)).isEqualTo(7);
            assertThat(inputCount(device)).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 누락과_영은_같은_입력이며_중복과_내용_충돌은_기존_결과와_활동을_바꾸지_않는다() {
        // given
        Device device = device();
        PressConnection connection = ready(device);
        UUID requestId = UUID.randomUUID();
        PressInputResult applied = service.submit(connection, requestId, Map.of(EmotionState.ANGRY, 40));
        Map<String, Object> before = activity(device);

        // when
        PressInputResult duplicate = service.submit(connection, requestId,
                Map.of(EmotionState.ANGRY, 40, EmotionState.EXHAUSTED, 0));
        assertThatThrownBy(() -> service.submit(connection, requestId, Map.of(EmotionState.ANGRY, 41)))
                .isInstanceOfSatisfying(PressRaceException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("INPUT_CONFLICT");
                    assertThat(exception.requestId()).isEqualTo(requestId);
                });

        // then
        assertThat(duplicate).isEqualTo(applied);
        assertThat(service.status(connection, List.of(requestId))).containsExactly(applied);
        assertThat(activity(device)).isEqualTo(before);
        assertThat(total(device)).isEqualTo(40);
        assertThat(projectionTotal(device)).isEqualTo(40);
    }

    @Test
    void 속도_제한_최종_거절은_잔여량이_회복돼도_같은_입력에서_성공으로_바뀌지_않는다() {
        // given
        Device device = device();
        PressConnection connection = ready(device);
        service.submit(connection, UUID.randomUUID(), Map.of(EmotionState.ANGRY, 100));
        UUID rejectedId = UUID.randomUUID();

        // when
        PressInputResult rejected = service.submit(connection, rejectedId, Map.of(EmotionState.ANGRY, 100));
        jdbc.sql("UPDATE press_device_controls SET tokens = 100 WHERE device_id = :device")
                .param("device", device.getId()).update();
        PressInputResult retry = service.submit(connection, rejectedId, Map.of(EmotionState.ANGRY, 100));

        // then
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(rejected.code()).isEqualTo("RATE_LIMITED");
        assertThat(rejected.appliedDate()).isNull();
        assertThat(retry).isEqualTo(rejected);
        assertThat(total(device)).isEqualTo(100);
    }

    @Test
    void HTTP는_WS와_제한을_공유하고_빈_입력은_제한과_활동을_소비하지_않는다() {
        // given
        Device device = device();
        PressConnection connection = ready(device);
        service.submit(connection, UUID.randomUUID(), Map.of(EmotionState.ANGRY, 100));
        Map<String, Object> before = activity(device);

        // when / then
        assertThat(httpService.press(device.getPublicId(), Map.of()).total()).isEqualTo(100);
        assertThat(activity(device)).isEqualTo(before);
        assertThatThrownBy(() -> httpService.press(device.getPublicId(), Map.of(
                EmotionState.ANGRY, 30, EmotionState.EXHAUSTED, 30,
                EmotionState.DISCOURAGED, 30, EmotionState.FRUSTRATED, 30)))
                .isInstanceOfSatisfying(PressRaceException.class,
                        exception -> assertThat(exception.code()).isEqualTo("RATE_LIMITED"));
        assertThat(activity(device)).isEqualTo(before);
        assertThat(total(device)).isEqualTo(100);
    }

    @Test
    void HTTP와_WS가_동시에_백회씩_요청하면_공통_잔여량에서_하나만_전체_반영한다() throws Exception {
        // given
        Device device = device();
        PressConnection connection = ready(device);
        UUID requestId = UUID.randomUUID();
        jdbc.sql("""
                UPDATE press_device_controls SET tokens = 100, refilled_at = clock_timestamp() + interval '1 day'
                WHERE device_id = :device
                """).param("device", device.getId()).update();
        jdbc.sql("""
                CREATE FUNCTION freeze_press_refill_test() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN NEW.refilled_at := OLD.refilled_at; RETURN NEW; END $$
                """).update();
        jdbc.sql("""
                CREATE TRIGGER freeze_press_refill_test BEFORE UPDATE ON press_device_controls
                FOR EACH ROW EXECUTE FUNCTION freeze_press_refill_test()
                """).update();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<PressInputResult> websocket = executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return service.submit(connection, requestId, Map.of(EmotionState.ANGRY, 100));
            });
            Future<String> http = executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                try {
                    httpService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, 30,
                            EmotionState.EXHAUSTED, 30, EmotionState.DISCOURAGED, 30, EmotionState.FRUSTRATED, 30));
                    return "APPLIED";
                } catch (PressRaceException exception) {
                    assertThat(exception.getErrorCode().getStatus().value()).isEqualTo(429);
                    return exception.code();
                }
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            // when
            start.countDown();
            PressInputResult wsResult = websocket.get(15, TimeUnit.SECONDS);
            String httpResult = http.get(15, TimeUnit.SECONDS);

            // then
            String websocketResult = "APPLIED".equals(wsResult.status()) ? "APPLIED" : wsResult.code();
            assertThat(List.of(websocketResult, httpResult)).containsExactlyInAnyOrder("APPLIED", "RATE_LIMITED");
            assertThat(service.status(connection, List.of(requestId))).containsExactly(wsResult);
            assertThat(total(device)).isEqualTo(100);
            assertThat(projectionTotal(device)).isEqualTo(100);
            assertThat(jdbc.sql("SELECT tokens FROM press_device_controls WHERE device_id = :device")
                    .param("device", device.getId()).query(Double.class).single()).isZero();
        } finally {
            start.countDown();
            executor.shutdownNow();
            jdbc.sql("DROP TRIGGER freeze_press_refill_test ON press_device_controls").update();
            jdbc.sql("DROP FUNCTION freeze_press_refill_test()").update();
        }
    }

    @Test
    void 적용_트랜잭션이_실패해도_접수_기록과_최초_시각은_남고_점수와_제한은_롤백된다() {
        // given
        Device device = device();
        PressConnection connection = ready(device);
        UUID requestId = UUID.randomUUID();
        Map<EmotionState, Integer> counts = Map.of(EmotionState.ANGRY, 3);
        assertThat(service.receive(connection, requestId, counts).status()).isEqualTo("PENDING");
        Map<String, Object> receipt = receipt(device, requestId);
        jdbc.sql("""
                CREATE FUNCTION fail_press_race_test() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'forced press storage failure'; END $$
                """).update();
        jdbc.sql("""
                CREATE TRIGGER fail_press_race_test BEFORE INSERT ON device_daily_presses
                FOR EACH ROW EXECUTE FUNCTION fail_press_race_test()
                """).update();
        try {
            // when / then
            assertThatThrownBy(() -> service.apply(connection, requestId, counts))
                    .isInstanceOf(DataAccessException.class);
            assertThat(receipt(device, requestId)).isEqualTo(receipt);
            assertThat(total(device)).isZero();
            assertThat(projectionTotal(device)).isZero();
            assertThat(jdbc.sql("SELECT tokens FROM press_device_controls WHERE device_id = :device")
                    .param("device", device.getId()).query(Double.class).single()).isEqualTo(100);
        } finally {
            jdbc.sql("DROP TRIGGER fail_press_race_test ON device_daily_presses").update();
            jdbc.sql("DROP FUNCTION fail_press_race_test()").update();
        }

        assertThat(service.submit(connection, requestId, counts).status()).isEqualTo("APPLIED");
        assertThat(receipt(device, requestId).get("first_received_at"))
                .isEqualTo(receipt.get("first_received_at"));
        assertThat(total(device)).isEqualTo(3);
    }

    @Test
    void 성공_결과_확정이_실패하면_이미_쓴_점수와_투영과_제한을_모두_롤백한다() {
        // given
        Device device = device();
        PressConnection connection = ready(device);
        UUID requestId = UUID.randomUUID();
        Map<EmotionState, Integer> counts = Map.of(EmotionState.ANGRY, 3);
        service.receive(connection, requestId, counts);
        Map<String, Object> before = receipt(device, requestId);
        jdbc.sql("""
                CREATE FUNCTION fail_press_finalization_test() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF (SELECT SUM(press_count) FROM device_daily_presses WHERE device_id = NEW.device_id)
                            IS DISTINCT FROM 3::bigint
                        OR (SELECT SUM(total) FROM press_daily_scores WHERE device_id = NEW.device_id)
                            IS DISTINCT FROM 3::bigint
                        OR (SELECT tokens FROM press_device_controls WHERE device_id = NEW.device_id)
                            IS DISTINCT FROM 97::numeric THEN
                        RAISE EXCEPTION 'intermediate writes missing';
                    END IF;
                    RAISE EXCEPTION 'forced finalization failure';
                END $$
                """).update();
        jdbc.sql("""
                CREATE TRIGGER fail_press_finalization_test BEFORE UPDATE ON press_inputs
                FOR EACH ROW WHEN (NEW.status = 'APPLIED') EXECUTE FUNCTION fail_press_finalization_test()
                """).update();
        try {
            // when / then: 최종 성공 직전 같은 트랜잭션 안에는 점수·투영·제한 변경이 모두 존재한다.
            assertThatThrownBy(() -> service.apply(connection, requestId, counts))
                    .isInstanceOfSatisfying(DataAccessException.class, exception ->
                            assertThat(exception.getMostSpecificCause().getMessage()).contains("forced finalization failure"));
            assertThat(receipt(device, requestId)).isEqualTo(before);
            assertThat(total(device)).isZero();
            assertThat(projectionTotal(device)).isZero();
            assertThat(jdbc.sql("SELECT tokens FROM press_device_controls WHERE device_id = :device")
                    .param("device", device.getId()).query(Double.class).single()).isEqualTo(100);
        } finally {
            jdbc.sql("DROP TRIGGER fail_press_finalization_test ON press_inputs").update();
            jdbc.sql("DROP FUNCTION fail_press_finalization_test()").update();
        }

        PressInputResult retried = service.submit(connection, requestId, counts);
        assertThat(retried.status()).isEqualTo("APPLIED");
        assertThat(receipt(device, requestId).get("first_received_at")).isEqualTo(before.get("first_received_at"));
        assertThat(total(device)).isEqualTo(3);
        assertThat(projectionTotal(device)).isEqualTo(3);
    }

    @Test
    void 재연결은_목록에_없는_이전_PENDING도_정산하고_없는_입력은_실제_접수시각_없이_종결한다() {
        // given
        Device device = device();
        PressConnection previous = ready(device);
        UUID pendingId = UUID.randomUUID();
        UUID absentId = UUID.randomUUID();
        service.receive(previous, pendingId, Map.of(EmotionState.ANGRY, 5));
        Map<String, Object> original = receipt(device, pendingId);
        PressConnection current = connection(device);
        service.connect(current);

        // when
        var session = service.initialize(current, List.of(PendingPress.of(
                absentId, previous.connectionGeneration(), Map.of(EmotionState.ANGRY, 2))));

        // then
        assertThat(session.counts().values()).allMatch(count -> count == 0);
        assertThat(session.results()).singleElement()
                .satisfies(result -> assertThat(result.code()).isEqualTo("NOT_APPLIED_ON_RECOVERY"));
        assertThat(service.status(current, List.of(pendingId, absentId))).allSatisfy(result -> {
            assertThat(result.status()).isEqualTo("REJECTED");
            assertThat(result.code()).isEqualTo("NOT_APPLIED_ON_RECOVERY");
            assertThat(result.appliedDate()).isNull();
        });
        Map<String, Object> settled = receipt(device, pendingId);
        assertThat(settled.get("first_received_at")).isEqualTo(original.get("first_received_at"));
        assertThat(settled.get("original_generation")).isEqualTo(previous.connectionGeneration());
        Map<String, Object> absent = receipt(device, absentId);
        assertThat(absent.get("first_received_at")).isNull();
        assertThat(absent.get("original_generation")).isNull();
        assertThat(absent.get("recovered_by_generation")).isEqualTo(current.connectionGeneration());
        assertThat(service.submit(current, absentId, Map.of(EmotionState.ANGRY, 2)).code())
                .isEqualTo("NOT_APPLIED_ON_RECOVERY");
        assertThatThrownBy(() -> service.submit(previous, UUID.randomUUID(), Map.of(EmotionState.ANGRY, 1)))
                .isInstanceOfSatisfying(PressRaceException.class,
                        exception -> assertThat(exception.code()).isEqualTo("CONNECTION_REPLACED"));
        assertThat(total(device)).isZero();
        assertThat(service.submit(current, UUID.randomUUID(), Map.of(EmotionState.ANGRY, 1)).status())
                .isEqualTo("APPLIED");
    }

    @Test
    void 진행_중인_이전_연결의_저장을_기다린_교체는_커밋된_성공을_복구_거절로_바꾸지_않는다() throws Exception {
        // given
        Device device = device();
        PressConnection previous = ready(device);
        PressConnection current = connection(device);
        UUID requestId = UUID.randomUUID();
        Map<EmotionState, Integer> counts = Map.of(EmotionState.ANGRY, 4);
        service.receive(previous, requestId, counts);
        long lockKey = UUID.randomUUID().getMostSignificantBits();
        jdbc.sql("""
                CREATE FUNCTION pause_press_race_test() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    PERFORM pg_advisory_xact_lock(%d);
                    RETURN NEW;
                END $$
                """.formatted(lockKey)).update();
        jdbc.sql("""
                CREATE TRIGGER pause_press_race_test AFTER INSERT ON device_daily_presses
                FOR EACH ROW EXECUTE FUNCTION pause_press_race_test()
                """).update();
        var executor = Executors.newFixedThreadPool(2);
        try (Connection gate = dataSource.getConnection()) {
            gate.setAutoCommit(false);
            try (var statement = gate.createStatement()) {
                statement.execute("SELECT pg_advisory_xact_lock(" + lockKey + ")");
                Future<PressInputResult> applying = executor.submit(() -> service.apply(previous, requestId, counts));
                awaitDatabaseLock("device_daily_presses", "advisory");

                // when: 기존 저장이 기기 잠금을 가진 상태에서 같은 기기의 연결 교체가 기다린다.
                Future<?> replacing = executor.submit(() -> service.connect(current));
                awaitDatabaseLock("press_device_controls", null);
                assertThat(applying.isDone()).isFalse();
                assertThat(replacing.isDone()).isFalse();
                gate.commit();
                PressInputResult applied = applying.get(10, TimeUnit.SECONDS);
                replacing.get(10, TimeUnit.SECONDS);
                var recovered = service.initialize(current, List.of(
                        PendingPress.of(requestId, previous.connectionGeneration(), counts)));

                // then
                assertThat(applied.status()).isEqualTo("APPLIED");
                assertThat(recovered.results()).containsExactly(applied);
                assertThat(recovered.counts().get(EmotionState.ANGRY)).isEqualTo(4);
                assertThat(receipt(device, requestId).get("status")).isEqualTo("APPLIED");
                assertThat(total(device)).isEqualTo(4);
                assertThat(projectionTotal(device)).isEqualTo(4);
            }
        } finally {
            executor.shutdownNow();
            jdbc.sql("DROP TRIGGER pause_press_race_test ON device_daily_presses").update();
            jdbc.sql("DROP FUNCTION pause_press_race_test()").update();
        }
    }

    @Test
    void 접수_뒤_연결이_교체되면_늦게_시작한_이전_적용은_점수를_반영하지_못한다() {
        // given
        Device device = device();
        PressConnection previous = ready(device);
        UUID requestId = UUID.randomUUID();
        Map<EmotionState, Integer> counts = Map.of(EmotionState.ANGRY, 4);
        service.receive(previous, requestId, counts);
        PressConnection current = connection(device);
        service.connect(current);

        // when / then
        assertThatThrownBy(() -> service.apply(previous, requestId, counts))
                .isInstanceOfSatisfying(PressRaceException.class,
                        exception -> assertThat(exception.code()).isEqualTo("CONNECTION_REPLACED"));
        assertThat(receipt(device, requestId).get("status")).isEqualTo("PENDING");
        assertThat(total(device)).isZero();
        assertThat(projectionTotal(device)).isZero();

        service.initialize(current, List.of());
        assertThat(service.status(current, List.of(requestId))).singleElement()
                .satisfies(result -> assertThat(result.code()).isEqualTo("NOT_APPLIED_ON_RECOVERY"));
        assertThat(total(device)).isZero();
    }

    @Test
    void 한_기기의_적용이_대기해도_다른_기기의_WS와_HTTP는_커밋하고_일일_공개ID를_유지한다() throws Exception {
        // given
        Device first = device();
        Device second = device();
        PressConnection firstConnection = ready(first);
        PressConnection secondConnection = ready(second);
        UUID firstRequest = UUID.randomUUID();
        UUID secondRequest = UUID.randomUUID();
        Map<EmotionState, Integer> firstCounts = Map.of(EmotionState.ANGRY, 4);
        Map<EmotionState, Integer> secondCounts = Map.of(EmotionState.ANGRY, 3);
        service.receive(firstConnection, firstRequest, firstCounts);
        service.submit(secondConnection, UUID.randomUUID(), Map.of(EmotionState.ANGRY, 1));
        Object publicId = activity(second).get("public_id");
        long lockKey = UUID.randomUUID().getMostSignificantBits();
        pauseDeviceAfterInsert(first, lockKey);
        var executor = Executors.newFixedThreadPool(3);
        try (Connection gate = dataSource.getConnection()) {
            holdAdvisoryGate(gate, lockKey);
            Future<PressInputResult> firstApply = executor.submit(() ->
                    service.apply(firstConnection, firstRequest, firstCounts));
            awaitDatabaseLock("device_daily_presses", "advisory");

            // when: 첫 기기의 실제 저장이 잠긴 상태에서 다른 기기의 두 전송 경로를 끝낸다.
            Future<PressInputResult> secondApply = executor.submit(() -> {
                assertThat(service.receive(secondConnection, secondRequest, secondCounts).status()).isEqualTo("PENDING");
                return service.apply(secondConnection, secondRequest, secondCounts);
            });
            Future<?> http = executor.submit(() ->
                    httpService.press(second.getPublicId(), Map.of(EmotionState.EXHAUSTED, 2)));
            PressInputResult secondResult = secondApply.get(5, TimeUnit.SECONDS);
            http.get(5, TimeUnit.SECONDS);

            // then: 첫 기기의 잠금을 풀기 전에 별도 연결 조회로 커밋과 ID의 불변성을 확인한다.
            assertThat(firstApply.isDone()).isFalse();
            assertThat(secondResult.status()).isEqualTo("APPLIED");
            assertThat(total(second)).isEqualTo(6);
            assertThat(projectionTotal(second)).isEqualTo(6);
            assertThat(activity(second).get("public_id")).isEqualTo(publicId);
            Map<String, Object> beforeDuplicate = activity(second);
            assertThat(service.submit(secondConnection, secondRequest, secondCounts)).isEqualTo(secondResult);
            assertThat(activity(second)).isEqualTo(beforeDuplicate);
            gate.commit();
            assertThat(firstApply.get(10, TimeUnit.SECONDS).status()).isEqualTo("APPLIED");
            assertThat(total(first)).isEqualTo(4);
        } finally {
            executor.shutdownNow();
            removeDevicePauseTrigger();
        }
    }

    @Test
    void 서버_교체는_진행중_저장과_기기잠금_대기자의_커밋을_기다리고_이후_구서버_쓰기를_막는다() throws Exception {
        // given
        Device first = device();
        Device second = device();
        PressConnection firstConnection = ready(first);
        PressConnection secondConnection = ready(second);
        UUID firstRequest = UUID.randomUUID();
        UUID secondRequest = UUID.randomUUID();
        Map<EmotionState, Integer> counts = Map.of(EmotionState.ANGRY, 2);
        service.receive(firstConnection, firstRequest, counts);
        service.receive(secondConnection, secondRequest, counts);
        UUID nextGeneration = UUID.randomUUID();
        long lockKey = UUID.randomUUID().getMostSignificantBits();
        pauseDeviceAfterInsert(first, lockKey);
        var executor = Executors.newFixedThreadPool(3);
        try (Connection gate = dataSource.getConnection()) {
            holdAdvisoryGate(gate, lockKey);
            try (var statement = gate.prepareStatement(
                    "SELECT device_id FROM press_device_controls WHERE device_id = ? FOR UPDATE")) {
                statement.setLong(1, second.getId());
                statement.execute();
            }
            Future<PressInputResult> firstApply = executor.submit(() ->
                    service.apply(firstConnection, firstRequest, counts));
            awaitDatabaseLock("device_daily_presses", "advisory");
            Future<PressInputResult> secondApply = executor.submit(() ->
                    service.apply(secondConnection, secondRequest, counts));
            awaitDatabaseLock("press_device_controls", null);

            // when: 두 쓰기가 공유 서버 자격 잠금을 가진 뒤 실제 서버 세대 UPDATE를 시작한다.
            Future<Integer> switching = executor.submit(() ->
                    jdbc.sql("UPDATE press_race_control SET active_server_generation = :generation")
                            .param("generation", nextGeneration).update());
            awaitDatabaseLock("UPDATE press_race_control", null);
            assertThat(firstApply.isDone()).isFalse();
            assertThat(secondApply.isDone()).isFalse();
            assertThat(switching.isDone()).isFalse();
            gate.commit();

            // then: 먼저 자격을 얻은 쓰기는 완료되고 교체 커밋 뒤 구 서버는 어느 경로로도 가산하지 못한다.
            assertThat(firstApply.get(10, TimeUnit.SECONDS).status()).isEqualTo("APPLIED");
            assertThat(secondApply.get(10, TimeUnit.SECONDS).status()).isEqualTo("APPLIED");
            assertThat(switching.get(10, TimeUnit.SECONDS)).isOne();
            for (PressConnection stale : List.of(firstConnection, secondConnection)) {
                assertThatThrownBy(() -> service.submit(stale, UUID.randomUUID(), counts))
                        .isInstanceOfSatisfying(PressRaceException.class,
                                exception -> assertThat(exception.code()).isEqualTo("SERVER_REPLACED"));
                assertThatThrownBy(() -> httpService.press(stale.devicePublicId(), counts))
                        .isInstanceOfSatisfying(PressRaceException.class,
                                exception -> assertThat(exception.code()).isEqualTo("SERVER_REPLACED"));
            }
            assertThat(total(first)).isEqualTo(2);
            assertThat(total(second)).isEqualTo(2);
            assertThat(projectionTotal(first)).isEqualTo(2);
            assertThat(projectionTotal(second)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
            removeDevicePauseTrigger();
        }
    }

    @Test
    void 활성_서버_미지정은_503이며_활성_서버의_경쟁_OFF는_HTTP_호환_저장만_허용한다() {
        // given
        Device device = device();
        PressConnection connection = connection(device);
        jdbc.sql("UPDATE press_race_control SET active_server_generation = NULL, enabled = false").update();

        // when / then
        assertThatThrownBy(() -> httpService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, 1)))
                .isInstanceOfSatisfying(PressRaceException.class,
                        exception -> assertThat(exception.getErrorCode().getStatus().value()).isEqualTo(503));
        assertThatThrownBy(() -> service.connect(connection)).isInstanceOf(PressRaceException.class);
        assertThat(total(device)).isZero();

        jdbc.sql("UPDATE press_race_control SET active_server_generation = :generation")
                .param("generation", service.serverGeneration()).update();
        assertThat(httpService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, 2)).total()).isEqualTo(2);
        assertThatThrownBy(() -> service.connect(connection))
                .isInstanceOfSatisfying(PressRaceException.class,
                        exception -> assertThat(exception.code()).isEqualTo("RACE_DISABLED"));
        assertThat(total(device)).isEqualTo(2);
        assertThat(projectionTotal(device)).isEqualTo(2);
    }

    @Test
    void 만료된_PENDING은_거절되고_재전송해도_접수시각과_마감을_갱신하지_않는다() {
        // given
        Device device = device();
        PressConnection connection = ready(device);
        UUID requestId = UUID.randomUUID();
        Map<EmotionState, Integer> counts = Map.of(EmotionState.ANGRY, 5);
        service.receive(connection, requestId, counts);
        jdbc.sql("""
                UPDATE press_inputs SET first_received_at = clock_timestamp() - interval '11 seconds'
                WHERE device_id = :device AND request_id = :request
                """).param("device", device.getId()).param("request", requestId).update();
        Object original = receipt(device, requestId).get("first_received_at");

        // when
        PressInputResult result = service.submit(connection, requestId, counts);

        // then
        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.code()).isEqualTo("INPUT_EXPIRED");
        assertThat(service.submit(connection, requestId, counts)).isEqualTo(result);
        assertThat(receipt(device, requestId).get("first_received_at")).isEqualTo(original);
        assertThat(total(device)).isZero();
    }

    @Test
    void 잠금_대기_중_마감이_지나면_잠금_후_새로운_DB_시각으로_거절한다() throws Exception {
        // given
        Device device = device();
        PressConnection connection = ready(device);
        UUID requestId = UUID.randomUUID();
        Map<EmotionState, Integer> counts = Map.of(EmotionState.ANGRY, 1);
        service.receive(connection, requestId, counts);
        var executor = Executors.newSingleThreadExecutor();
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.createStatement()) {
                statement.execute("SELECT device_id FROM press_device_controls WHERE device_id = "
                        + device.getId() + " FOR UPDATE");
                try (var update = blocker.prepareStatement("""
                        UPDATE press_inputs SET first_received_at = clock_timestamp() - interval '9 seconds'
                        WHERE device_id = ? AND request_id = ?
                        """)) {
                    update.setLong(1, device.getId());
                    update.setObject(2, requestId);
                    update.executeUpdate();
                }
                CountDownLatch started = new CountDownLatch(1);
                Future<PressInputResult> future = executor.submit(() -> {
                    started.countDown();
                    return service.apply(connection, requestId, counts);
                });
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

                // when: 적용이 기기 잠금을 기다리는 동안 DB 수신 마감을 넘긴다.
                statement.execute("SELECT pg_sleep(2)");
                assertThat(future.isDone()).isFalse();
                blocker.commit();
                PressInputResult result = future.get(10, TimeUnit.SECONDS);

                // then
                assertThat(result.code()).isEqualTo("INPUT_EXPIRED");
                assertThat(total(device)).isZero();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 활성_서버가_교체되면_기존_서버의_WS와_HTTP는_가산하지_못한다() {
        // given
        Device device = device();
        PressConnection connection = ready(device);
        UUID nextGeneration = UUID.randomUUID();
        jdbc.sql("UPDATE press_race_control SET active_server_generation = :generation")
                .param("generation", nextGeneration).update();

        // when / then
        assertThatThrownBy(() -> service.submit(connection, UUID.randomUUID(), Map.of(EmotionState.ANGRY, 1)))
                .isInstanceOf(PressRaceException.class);
        assertThatThrownBy(() -> httpService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, 1)))
                .isInstanceOf(PressRaceException.class);
        assertThat(total(device)).isZero();
        assertThat(inputCount(device)).isZero();
        assertThat(jdbc.sql("SELECT active_server_generation FROM press_race_control")
                .query(UUID.class).single()).isEqualTo(nextGeneration);
    }

    @Test
    void 예열_서버의_생성은_활성_서버_세대를_가져가지_않는다() {
        // given
        UUID activeGeneration = service.serverGeneration();

        // when
        PressRaceService warmingServer = applicationContext.getAutowireCapableBeanFactory()
                .createBean(PressRaceService.class);

        // then
        assertThat(warmingServer.serverGeneration()).isNotEqualTo(activeGeneration);
        assertThat(jdbc.sql("SELECT active_server_generation FROM press_race_control")
                .query(UUID.class).single()).isEqualTo(activeGeneration);
    }

    @Test
    void 잠금_대기_중_인증이_만료되면_접수_기록만_유지하고_점수를_가산하지_않는다() throws Exception {
        // given
        Device device = device();
        PressConnection connection = PressConnection.of(device.getPublicId(), service.serverGeneration(),
                UUID.randomUUID(), databaseNow().plusSeconds(2));
        service.connect(connection);
        service.initialize(connection, List.of());
        UUID requestId = UUID.randomUUID();
        Map<EmotionState, Integer> counts = Map.of(EmotionState.ANGRY, 1);
        service.receive(connection, requestId, counts);
        var executor = Executors.newSingleThreadExecutor();
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.createStatement()) {
                statement.execute("SELECT device_id FROM press_device_controls WHERE device_id = "
                        + device.getId() + " FOR UPDATE");
                CountDownLatch started = new CountDownLatch(1);
                Future<String> future = executor.submit(() -> {
                    started.countDown();
                    try {
                        service.apply(connection, requestId, counts);
                        return "APPLIED";
                    } catch (PressRaceException exception) {
                        return exception.code();
                    }
                });
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

                // when
                statement.execute("SELECT pg_sleep(2.2)");
                assertThat(future.isDone()).isFalse();
                blocker.commit();

                // then
                assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("AUTH_EXPIRED");
                assertThat(receipt(device, requestId).get("status")).isEqualTo("PENDING");
                assertThat(total(device)).isZero();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 입력_조회는_자기_기기의_기록만_반환하고_없는_ID를_접수하지_않는다() {
        // given
        Device first = device();
        Device second = device();
        PressConnection firstConnection = ready(first);
        PressConnection secondConnection = ready(second);
        UUID requestId = UUID.randomUUID();
        service.submit(firstConnection, requestId, Map.of(EmotionState.ANGRY, 2));

        // when
        var results = service.status(secondConnection, List.of(requestId));

        // then
        assertThat(results).singleElement().satisfies(result -> assertThat(result.status()).isEqualTo("NOT_FOUND"));
        assertThat(inputCount(second)).isZero();
        assertThat(total(second)).isZero();
    }

    @Test
    void DB는_실제_접수_정보의_부분_NULL과_불완전한_최종_결과를_금지한다() {
        // given
        Device device = device();
        PressConnection connection = ready(device);
        UUID requestId = UUID.randomUUID();
        service.receive(connection, requestId, Map.of(EmotionState.ANGRY, 1));

        // when / then
        assertThatThrownBy(() -> jdbc.sql("""
                UPDATE press_inputs SET first_received_at = NULL
                WHERE device_id = :device AND request_id = :request
                """).param("device", device.getId()).param("request", requestId).update())
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("""
                UPDATE press_inputs SET first_received_at = NULL, original_generation = NULL
                WHERE device_id = :device AND request_id = :request
                """).param("device", device.getId()).param("request", requestId).update())
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("""
                UPDATE press_inputs SET status = 'REJECTED', rejection_code = NULL, finalized_at = clock_timestamp()
                WHERE device_id = :device AND request_id = :request
                """).param("device", device.getId()).param("request", requestId).update())
                .isInstanceOf(DataAccessException.class);
        assertThat(receipt(device, requestId).get("status")).isEqualTo("PENDING");
    }

    private Device device() {
        Device device = deviceRepository.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
        devices.add(device);
        return device;
    }

    private PressConnection ready(Device device) {
        PressConnection connection = connection(device);
        service.connect(connection);
        service.initialize(connection, List.of());
        return connection;
    }

    private PressConnection connection(Device device) {
        return PressConnection.of(device.getPublicId(), service.serverGeneration(), UUID.randomUUID(),
                databaseNow().plusSeconds(60));
    }

    private Instant databaseNow() {
        return jdbc.sql("SELECT clock_timestamp()")
                .query((result, rowNumber) -> result.getTimestamp(1).toInstant()).single();
    }

    private void awaitDatabaseLock(String table, String waitEvent) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            long waiting = jdbc.sql("""
                    SELECT COUNT(*) FROM pg_stat_activity
                    WHERE datname = current_database() AND state = 'active' AND wait_event_type = 'Lock'
                        AND query ILIKE :query AND (:event IS NULL OR wait_event = :event)
                    """).param("query", "%" + table + "%")
                    .param("event", waitEvent, java.sql.Types.VARCHAR).query(Long.class).single();
            if (waiting > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("DB lock wait was not observed for " + table + "/" + waitEvent);
    }

    private void pauseDeviceAfterInsert(Device device, long lockKey) {
        jdbc.sql("""
                CREATE FUNCTION pause_device_press_test() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN PERFORM pg_advisory_xact_lock(%d); RETURN NEW; END $$
                """.formatted(lockKey)).update();
        jdbc.sql("""
                CREATE TRIGGER pause_device_press_test AFTER INSERT ON device_daily_presses
                FOR EACH ROW WHEN (NEW.device_id = %d) EXECUTE FUNCTION pause_device_press_test()
                """.formatted(device.getId())).update();
    }

    private void holdAdvisoryGate(Connection gate, long lockKey) throws java.sql.SQLException {
        gate.setAutoCommit(false);
        try (var statement = gate.createStatement()) {
            statement.execute("SELECT pg_advisory_xact_lock(" + lockKey + ")");
        }
    }

    private void removeDevicePauseTrigger() {
        jdbc.sql("DROP TRIGGER pause_device_press_test ON device_daily_presses").update();
        jdbc.sql("DROP FUNCTION pause_device_press_test()").update();
    }

    private long total(Device device) {
        return jdbc.sql("SELECT COALESCE(SUM(press_count), 0) FROM device_daily_presses WHERE device_id = :device")
                .param("device", device.getId()).query(Long.class).single();
    }

    private long projectionTotal(Device device) {
        return jdbc.sql("SELECT COALESCE(SUM(total), 0) FROM press_daily_scores WHERE device_id = :device")
                .param("device", device.getId()).query(Long.class).single();
    }

    private long inputCount(Device device) {
        return jdbc.sql("SELECT COUNT(*) FROM press_inputs WHERE device_id = :device")
                .param("device", device.getId()).query(Long.class).single();
    }

    private Map<String, Object> activity(Device device) {
        return jdbc.sql("SELECT total, last_applied_at, public_id FROM press_daily_scores WHERE device_id = :device")
                .param("device", device.getId()).query().singleRow();
    }

    private Map<String, Object> receipt(Device device, UUID requestId) {
        return jdbc.sql("SELECT * FROM press_inputs WHERE device_id = :device AND request_id = :request")
                .param("device", device.getId()).param("request", requestId).query().singleRow();
    }
}
