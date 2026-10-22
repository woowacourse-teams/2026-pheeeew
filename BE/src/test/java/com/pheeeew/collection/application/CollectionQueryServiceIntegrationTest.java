package com.pheeeew.collection.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.collection.fixture.DeviceDailyStampFixture.일별_스탬프를_저장한다;
import static com.pheeeew.emotion.fixture.DeviceDailyPressFixture.개인_프레스를_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
import com.pheeeew.collection.exception.CollectionErrorCode;
import com.pheeeew.collection.exception.CollectionException;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@PostgisDataJpaTest
@Import({CollectionQueryService.class, CollectionProgressRepository.class})
class CollectionQueryServiceIntegrationTest {

    @Autowired
    private CollectionQueryService service;

    @Autowired
    private DeviceRepository devices;

    @Autowired
    private JdbcClient jdbc;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(Instant.parse("2026-10-22T03:00:00Z"));
    }

    @Test
    void 활동이_없어도_오늘의_다섯_감정과_부족분을_반환한다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());

        // when
        var result = service.findToday(device.getPublicId());

        // then
        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(result.items()).extracting(item -> item.state()).containsExactly(EmotionState.values());
        assertThat(result.items()).allSatisfy(item -> {
            assertThat(item.stamps().count()).isZero();
            assertThat(item.stamps().target()).isEqualTo(1);
            assertThat(item.stamps().remaining()).isEqualTo(1);
            assertThat(item.presses().count()).isZero();
            assertThat(item.presses().target()).isEqualTo(100);
            assertThat(item.presses().remaining()).isEqualTo(100);
        });
    }

    @Test
    void 조회_도중_KST_자정을_넘기면_새_날짜의_집계로_다시_조회한다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        일별_스탬프를_저장한다(jdbc, device.getId(), LocalDate.of(2026, 10, 22), EmotionState.ANGRY, 2);
        개인_프레스를_저장한다(jdbc, device.getId(), LocalDate.of(2026, 10, 22), EmotionState.ANGRY, 150);
        일별_스탬프를_저장한다(jdbc, device.getId(), LocalDate.of(2026, 10, 23), EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), LocalDate.of(2026, 10, 23), EmotionState.ANGRY, 70);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-22T14:59:59.999Z"),
                Instant.parse("2026-10-22T15:00:00Z"));

        // when
        var result = service.findToday(device.getPublicId());

        // then
        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(result.items()).filteredOn(item -> item.state() == EmotionState.ANGRY)
                .singleElement().satisfies(item -> {
                    assertThat(item.stamps().count()).isEqualTo(1);
                    assertThat(item.presses().count()).isEqualTo(70);
                    assertThat(item.presses().remaining()).isEqualTo(30);
                });
    }

    @ParameterizedTest
    @CsvSource({"0,70,1,30", "1,99,0,1", "1,100,0,0", "3,150,0,0", "2,0,0,100"})
    void 실제_누적_횟수를_유지하고_목표_초과분의_부족분은_영이다(
            long stamps, long presses, long remainingStamps, long remainingPresses) {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        LocalDate date = LocalDate.of(2026, 10, 22);
        if (stamps > 0) {
            일별_스탬프를_저장한다(jdbc, device.getId(), date, EmotionState.FRUSTRATED, stamps);
        }
        if (presses > 0) {
            개인_프레스를_저장한다(jdbc, device.getId(), date, EmotionState.FRUSTRATED, presses);
        }

        // when
        var result = service.findToday(device.getPublicId());

        // then
        assertThat(result.items()).filteredOn(item -> item.state() == EmotionState.FRUSTRATED)
                .singleElement().satisfies(item -> {
                    assertThat(item.stamps().count()).isEqualTo(stamps);
                    assertThat(item.stamps().target()).isEqualTo(1);
                    assertThat(item.stamps().remaining()).isEqualTo(remainingStamps);
                    assertThat(item.presses().count()).isEqualTo(presses);
                    assertThat(item.presses().target()).isEqualTo(100);
                    assertThat(item.presses().remaining()).isEqualTo(remainingPresses);
                });
    }

    @Test
    void 같은_날짜의_다른_기기와_다른_날짜와_감정의_집계를_섞지_않는다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var other = devices.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
        LocalDate today = LocalDate.of(2026, 10, 22);
        일별_스탬프를_저장한다(jdbc, owner.getId(), today, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, owner.getId(), today, EmotionState.ANGRY, 70);
        일별_스탬프를_저장한다(jdbc, owner.getId(), today, EmotionState.EXHAUSTED, 3);
        개인_프레스를_저장한다(jdbc, owner.getId(), today, EmotionState.EXHAUSTED, 20);
        일별_스탬프를_저장한다(jdbc, other.getId(), today, EmotionState.ANGRY, 7);
        개인_프레스를_저장한다(jdbc, other.getId(), today, EmotionState.ANGRY, 500);
        일별_스탬프를_저장한다(jdbc, owner.getId(), today.minusDays(1), EmotionState.ANGRY, 9);
        개인_프레스를_저장한다(jdbc, owner.getId(), today.minusDays(1), EmotionState.ANGRY, 900);
        일별_스탬프를_저장한다(jdbc, owner.getId(), today.plusDays(1), EmotionState.ANGRY, 11);
        개인_프레스를_저장한다(jdbc, owner.getId(), today.plusDays(1), EmotionState.ANGRY, 1100);

        // when
        var ownResult = service.findToday(owner.getPublicId());
        var otherResult = service.findToday(other.getPublicId());

        // then
        assertThat(ownResult.items()).filteredOn(item -> item.state() == EmotionState.ANGRY)
                .singleElement().satisfies(item -> {
                    assertThat(item.stamps().count()).isEqualTo(1);
                    assertThat(item.presses().count()).isEqualTo(70);
                });
        assertThat(ownResult.items()).filteredOn(item -> item.state() == EmotionState.EXHAUSTED)
                .singleElement().satisfies(item -> {
                    assertThat(item.stamps().count()).isEqualTo(3);
                    assertThat(item.presses().count()).isEqualTo(20);
                });
        assertThat(otherResult.items()).filteredOn(item -> item.state() == EmotionState.ANGRY)
                .singleElement().satisfies(item -> {
                    assertThat(item.stamps().count()).isEqualTo(7);
                    assertThat(item.presses().count()).isEqualTo(500);
                });
    }

    @Test
    void 인증_기기가_존재하지_않으면_조회하지_못한다() {
        // given
        UUID missingDevice = UUID.randomUUID();

        // when / then
        assertThatThrownBy(() -> service.findToday(missingDevice))
                .isInstanceOfSatisfying(DeviceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
    }

    @Test
    void 날짜가_재조회_중에도_변하면_무한_반복하지_않고_재시도를_요청한다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        when(clock.instant()).thenReturn(Instant.parse("2026-10-22T14:59:59Z"),
                Instant.parse("2026-10-22T15:00:00Z"), Instant.parse("2026-10-22T15:00:00Z"),
                Instant.parse("2026-10-23T15:00:00Z"));

        // when / then
        assertThatThrownBy(() -> service.findToday(device.getPublicId()))
                .isInstanceOfSatisfying(CollectionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(CollectionErrorCode.COLLECTION_DATE_CHANGED));
    }
}
