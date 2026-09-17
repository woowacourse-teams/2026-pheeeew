package com.pheeeew.activity.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import com.pheeeew.activity.domain.DeviceActivitySummary;
import com.pheeeew.activity.domain.repository.DeviceActivitySummaryRepository;
import com.pheeeew.activity.domain.repository.DeviceDailyActivityRepository;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.DevicePlatform;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Import({DeviceActivitySummaryService.class, DeviceDailyActivityService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeviceActivitySummaryServiceIntegrationTest {

    private static final LocalDate ACTIVITY_DATE = LocalDate.of(2026, 9, 16);
    private static final Instant NOW = Instant.parse("2026-09-16T15:05:00Z");

    @Autowired
    private DeviceActivitySummaryService service;
    @Autowired
    private DeviceDailyActivityService activityService;
    @Autowired
    private DeviceActivitySummaryRepository summaryRepository;
    @Autowired
    private DeviceDailyActivityRepository activityRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private JdbcClient jdbcClient;
    @MockitoBean
    private Clock clock;

    private final List<Long> createdDeviceIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
    }

    @AfterEach
    void tearDown() {
        summaryRepository.deleteAllInBatch();
        activityRepository.deleteAllInBatch();
        deviceRepository.deleteAllByIdInBatch(createdDeviceIds);
    }

    @Test
    void 두_플랫폼의_최종_집계를_저장하고_원본이_달라져도_재시도로_덮어쓰지_않는다() {
        // given
        활동을_저장한다();

        // when
        service.save(ACTIVITY_DATE);
        List<DeviceActivitySummary> first = summaryRepository.findAll();

        // then
        assertThat(first).extracting(DeviceActivitySummary::getPlatform,
                        DeviceActivitySummary::getDau, DeviceActivitySummary::getMau)
                .containsExactlyInAnyOrder(tuple(DevicePlatform.ANDROID, 1L, 1L), tuple(DevicePlatform.IOS, 0L, 0L));
        assertThat(first).allSatisfy(summary -> {
            assertThat(summary.getActivityDate()).isEqualTo(ACTIVITY_DATE);
            assertThat(summary.getAggregatedAt()).isEqualTo(NOW);
        });

        // when / then
        활동을_저장한다();
        when(clock.instant()).thenReturn(NOW.plusSeconds(300));
        service.save(ACTIVITY_DATE);
        assertThat(summaryRepository.findAll()).usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrderElementsOf(first);
    }

    @Test
    void 두번째_플랫폼_저장이_실패하면_첫번째_플랫폼도_남지_않는다() {
        // given
        jdbcClient.sql("""
                ALTER TABLE device_activity_summaries ADD CONSTRAINT ck_test_summary_failure
                CHECK (platform <> 'IOS')
                """).update();
        try {
            // when / then
            assertThatThrownBy(() -> service.save(ACTIVITY_DATE)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(summaryRepository.count()).isZero();
        } finally {
            jdbcClient.sql("ALTER TABLE device_activity_summaries DROP CONSTRAINT ck_test_summary_failure").update();
        }
    }

    @Test
    void 동시에_최종_집계를_저장해도_두_플랫폼의_결과는_한_건씩만_남는다() throws Exception {
        // given
        활동을_저장한다();
        CountDownLatch ready = new CountDownLatch(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> results = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                    service.save(ACTIVITY_DATE);
                    return null;
                }));
            }

            // when
            for (Future<?> result : results) {
                result.get(10, TimeUnit.SECONDS);
            }
        }

        // then
        assertThat(summaryRepository.findAll()).extracting(DeviceActivitySummary::getPlatform)
                .containsExactlyInAnyOrder(DevicePlatform.ANDROID, DevicePlatform.IOS);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void KST_오늘과_미래_날짜는_최종_집계로_저장하지_않는다(int days) {
        // given / when / then
        assertThatThrownBy(() -> service.save(ACTIVITY_DATE.plusDays(days)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(summaryRepository.count()).isZero();
    }

    private void 활동을_저장한다() {
        Device device = deviceRepository.save(기본_기기_빌더().build());
        createdDeviceIds.add(device.getId());
        activityService.save(device.getPublicId(), NOW.minusSeconds(600));
    }
}
