package com.pheeeew.pressrace.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.EmotionPressMetrics;
import com.pheeeew.emotion.application.command.EmotionPressService;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.DeviceDailyPressRepository;
import com.pheeeew.pressrace.exception.PressRaceException;
import com.pheeeew.pressrace.infra.PressRaceRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서비스 재생성으로 부팅 세대 변경을 재현한다. 실제 프로세스·컨테이너 재시작 검증은 별도다.
 * PHEEEEW_PRESS_RESTART_REPRO=true일 때만 실행하며, 영구 복구 전에는 미설정 세대 시나리오가 실패한다.
 */
@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "PHEEEEW_PRESS_RESTART_REPRO", matches = "true")
class PressHttpRestartReproductionIntegrationTest {

    @Autowired
    private DeviceRepository devices;
    @Autowired
    private DeviceDailyPressRepository presses;
    @Autowired
    private PressRaceRepository repository;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private EmotionPressMetrics metrics;
    @Autowired
    private JdbcClient jdbc;

    private Device device;

    @AfterEach
    void tearDown() {
        if (device != null) {
            jdbc.sql("DELETE FROM device_daily_presses WHERE device_id = :device")
                    .param("device", device.getId()).update();
            devices.deleteById(device.getId());
        }
        jdbc.sql("""
                UPDATE press_race_control SET active_server_generation = NULL,
                    enabled = false, activation_date = NULL
                """).update();
    }

    @Test
    void 세대_설정_없는_서버를_다시_생성해도_기존_HTTP_저장이_계속된다() {
        // given: 임시 복구처럼 실행 중인 세대를 지정하고 경쟁 OFF에서 저장한다.
        device = devices.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
        PressRaceService before = server("");
        jdbc.sql("""
                UPDATE press_race_control SET active_server_generation = :generation,
                    enabled = false, activation_date = NULL
                """).param("generation", before.serverGeneration()).update();
        assertThat(http(before).press(device.getPublicId(), Map.of(EmotionState.ANGRY, 1)).total()).isOne();
        assertTotals(1);

        // when: 동일 DB를 유지하고 서버의 부팅 수명만 다시 시작한다.
        PressRaceService after = server("");
        Throwable failure = catchThrowable(() ->
                http(after).press(device.getPublicId(), Map.of(EmotionState.ANGRY, 1)));

        // then: 부팅이 DB 소유권을 가져가지 않으면서 기존 HTTP 저장은 계속돼야 한다.
        assertThat(jdbc.sql("SELECT active_server_generation FROM press_race_control")
                .query(UUID.class).single()).isEqualTo(before.serverGeneration());
        if (failure instanceof PressRaceException rejection) {
            assertThat(rejection.code()).isEqualTo("SERVER_REPLACED");
            assertThat(rejection.getErrorCode().getStatus().value()).isEqualTo(503);
            assertTotals(1);
        }
        assertThat(failure).as("서버 재생성 후 HTTP 입력 저장 실패: %s",
                failure instanceof PressRaceException rejection ? rejection.code() : failure).isNull();
        assertTotals(2);
    }

    @Test
    void 동일한_명시적_세대를_유지하면_재생성_후에도_OFF에서_HTTP가_저장된다() {
        // given
        String generation = "10000000-0000-0000-0000-000000000001";
        PressRaceService before = server(generation);
        assign(before);
        assertThat(http(before).press(device.getPublicId(), Map.of(EmotionState.ANGRY, 1)).total()).isOne();

        // when
        PressRaceService after = server(generation);
        var result = http(after).press(device.getPublicId(), Map.of(EmotionState.ANGRY, 1));

        // then
        assertThat(result.total()).isEqualTo(2);
        assertTotals(2);
        assertThat(jdbc.sql("SELECT enabled FROM press_race_control").query(Boolean.class).single()).isFalse();
    }

    @Test
    void 다른_명시적_세대는_부팅만으로_기존_소유권을_얻거나_HTTP를_저장하지_못한다() {
        // given
        PressRaceService before = server("10000000-0000-0000-0000-000000000001");
        assign(before);
        http(before).press(device.getPublicId(), Map.of(EmotionState.ANGRY, 1));

        // when
        PressRaceService replacement = server("20000000-0000-0000-0000-000000000002");
        Throwable failure = catchThrowable(() ->
                http(replacement).press(device.getPublicId(), Map.of(EmotionState.ANGRY, 1)));

        // then
        assertThat(failure).isInstanceOfSatisfying(PressRaceException.class, rejection -> {
            assertThat(rejection.code()).isEqualTo("SERVER_REPLACED");
            assertThat(rejection.getErrorCode().getStatus().value()).isEqualTo(503);
        });
        assertTotals(1);
        assertThat(jdbc.sql("SELECT active_server_generation FROM press_race_control")
                .query(UUID.class).single()).isEqualTo(before.serverGeneration());
        assertThat(http(before).press(device.getPublicId(), Map.of(EmotionState.ANGRY, 1)).total()).isEqualTo(2);
        assertTotals(2);
    }

    private void assign(PressRaceService server) {
        device = devices.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
        jdbc.sql("""
                UPDATE press_race_control SET active_server_generation = :generation,
                    enabled = false, activation_date = NULL
                """).param("generation", server.serverGeneration()).update();
    }

    private PressRaceService server(String generation) {
        return new PressRaceService(repository, presses, transactions, generation);
    }

    private EmotionPressService http(PressRaceService server) {
        return new EmotionPressService(devices, metrics, server);
    }

    private void assertTotals(long expected) {
        assertThat(jdbc.sql("SELECT COALESCE(SUM(press_count), 0) FROM device_daily_presses WHERE device_id = :device")
                .param("device", device.getId()).query(Long.class).single()).isEqualTo(expected);
        assertThat(jdbc.sql("SELECT COALESCE(SUM(total), 0) FROM press_daily_scores WHERE device_id = :device")
                .param("device", device.getId()).query(Long.class).single()).isEqualTo(expected);
    }
}
