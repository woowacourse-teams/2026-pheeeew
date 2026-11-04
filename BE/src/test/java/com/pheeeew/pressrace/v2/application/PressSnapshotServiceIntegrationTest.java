package com.pheeeew.pressrace.v2.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.command.EmotionPressService;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.pressrace.application.PressRaceService;
import com.pheeeew.pressrace.v2.domain.PressRanker;
import com.pheeeew.pressrace.v2.domain.PressScreen;
import com.pheeeew.pressrace.v2.domain.PressView;
import com.pheeeew.pressrace.v2.domain.repository.PressSnapshotRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Import({PressSnapshotRepository.class, PressSnapshotService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PressSnapshotServiceIntegrationTest {

    @Autowired
    private PressSnapshotService pressSnapshotService;

    @Autowired
    private EmotionPressService emotionPressService;

    @Autowired
    private PressRaceService pressRaceService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private JdbcClient jdbc;

    private final List<Device> devices = new ArrayList<>();

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                UPDATE press_race_control SET active_server_generation = :generation, enabled = TRUE,
                  activation_date = (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date
                """).param("generation", pressRaceService.serverGeneration()).update();
    }

    @AfterEach
    void tearDown() {
        for (Device device : devices) {
            jdbc.sql("DELETE FROM device_daily_presses WHERE device_id = :device")
                    .param("device", device.getId()).update();
            deviceRepository.deleteById(device.getId());
        }
        jdbc.sql("UPDATE press_race_control SET active_server_generation = NULL, enabled = FALSE, activation_date = NULL")
                .update();
    }

    @Test
    void 한_기기가_여러_감정을_나눠_누르면_점수는_합계이고_활동_인원은_한_기기로_센다() {
        // given
        Device device = 기기();
        emotionPressService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, 3, EmotionState.EXHAUSTED, 2));
        emotionPressService.press(device.getPublicId(), Map.of(EmotionState.IRRITATED, 1));

        // when
        PressScreen screen = pressSnapshotService.findSnapshot(device.getPublicId(), PressView.PRESS);

        // then
        assertThat(screen.activeCount()).isEqualTo(1);
        assertThat(screen.me().participating()).isTrue();
        assertThat(screen.me().score()).isEqualTo(6);
        assertThat(screen.me().rank()).isEqualTo(1);
        assertThat(screen.nearby()).singleElement().isEqualTo(screen.me());
        assertThat(screen.top100()).isEmpty();
    }

    @Test
    void 같은_점수는_공동_순위이고_공개_ID_오름차순으로_표시한다() {
        // given
        Device first = 기기();
        Device second = 기기();
        Device third = 기기();
        Device observer = 기기();
        emotionPressService.press(first.getPublicId(), Map.of(EmotionState.ANGRY, 5));
        emotionPressService.press(second.getPublicId(), Map.of(EmotionState.FRUSTRATED, 5));
        emotionPressService.press(third.getPublicId(), Map.of(EmotionState.ANGRY, 3));

        // when
        PressScreen thirdScreen = pressSnapshotService.findSnapshot(third.getPublicId(), PressView.ALL);
        PressScreen observerScreen = pressSnapshotService.findSnapshot(observer.getPublicId(), PressView.PRESS);

        // then
        assertThat(thirdScreen.top100()).extracting(PressRanker::score, PressRanker::rank)
                .containsExactly(
                        tuple(5L, 1L),
                        tuple(5L, 1L),
                        tuple(3L, 3L)
                );
        assertThat(thirdScreen.top100().subList(0, 2)).extracting(PressRanker::publicId)
                .isSortedAccordingTo(Comparator.naturalOrder());
        assertThat(thirdScreen.me().rank()).isEqualTo(3);
        assertThat(thirdScreen.activeCount()).isEqualTo(3);
        assertThat(observerScreen.me().participating()).isFalse();
        assertThat(observerScreen.me().rank()).isNull();
        assertThat(observerScreen.nearby()).extracting(PressRanker::score).containsExactly(5L, 5L);
    }

    @Test
    void 새_프레스가_없으면_시간이_지나며_활동_인원이_줄고_점수는_유지된다() {
        // given
        Device idle = 기기();
        Device active = 기기();
        emotionPressService.press(idle.getPublicId(), Map.of(EmotionState.ANGRY, 4));
        emotionPressService.press(active.getPublicId(), Map.of(EmotionState.ANGRY, 2));
        int before = pressSnapshotService.findSnapshot(idle.getPublicId(), PressView.PRESS).activeCount();

        // when: 마지막 반영 시각을 5초 앞으로 옮겨 시간 경과를 만든다. 빈 입력은 활동을 연장하지 않는다.
        지난_시간(idle, 5);
        emotionPressService.press(idle.getPublicId(), Map.of());
        PressScreen after = pressSnapshotService.findSnapshot(idle.getPublicId(), PressView.PRESS);

        // then
        assertThat(before).isEqualTo(2);
        assertThat(after.activeCount()).isEqualTo(1);
        assertThat(after.me().score()).isEqualTo(4);
        assertThat(after.me().rank()).isEqualTo(1);
    }

    @Test
    void 지난_기기가_다시_누르면_활동_인원에_다시_포함된다() {
        // given
        Device device = 기기();
        emotionPressService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, 1));
        지난_시간(device, 5);
        int expired = pressSnapshotService.findSnapshot(device.getPublicId(), PressView.PRESS).activeCount();

        // when
        emotionPressService.press(device.getPublicId(), Map.of(EmotionState.DISCOURAGED, 1));
        PressScreen screen = pressSnapshotService.findSnapshot(device.getPublicId(), PressView.PRESS);

        // then
        assertThat(expired).isZero();
        assertThat(screen.activeCount()).isEqualTo(1);
        assertThat(screen.me().score()).isEqualTo(2);
    }

    @Test
    void 웹소켓_경쟁이_꺼지고_활성_서버가_없어도_오늘_순위를_조회한다() {
        // given
        Device device = 기기();
        emotionPressService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, 2));
        jdbc.sql("UPDATE press_race_control SET active_server_generation = NULL, enabled = FALSE, activation_date = NULL")
                .update();

        // when
        PressScreen screen = pressSnapshotService.findSnapshot(device.getPublicId(), PressView.PRESS);

        // then
        assertThat(screen.me().score()).isEqualTo(2);
        assertThat(screen.me().rank()).isEqualTo(1);
    }

    private Device 기기() {
        Device device = deviceRepository.save(기본_기기_빌더().build());
        devices.add(device);
        return device;
    }

    private void 지난_시간(Device device, int seconds) {
        jdbc.sql("""
                UPDATE press_daily_scores SET last_applied_at = last_applied_at - make_interval(secs => :seconds)
                WHERE device_id = :device
                """).param("seconds", seconds).param("device", device.getId()).update();
    }
}
