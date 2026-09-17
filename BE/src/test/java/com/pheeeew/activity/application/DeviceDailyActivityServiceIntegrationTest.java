package com.pheeeew.activity.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.activity.application.dto.DeviceActivityCount;
import com.pheeeew.activity.domain.DeviceDailyActivity;
import com.pheeeew.activity.domain.repository.DeviceDailyActivityRepository;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.DevicePlatform;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@PostgisDataJpaTest
@Import(DeviceDailyActivityService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeviceDailyActivityServiceIntegrationTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-09-15T15:00:00Z");

    @Autowired
    private DeviceDailyActivityService service;

    @Autowired
    private DeviceDailyActivityRepository activityRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Long> createdDeviceIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        activityRepository.deleteAllInBatch();
        deviceRepository.deleteAllByIdInBatch(createdDeviceIds);
    }

    @ParameterizedTest
    @EnumSource(DevicePlatform.class)
    void 등록된_기기는_처리_날짜가_아닌_활동_시각의_KST_날짜로_저장한다(DevicePlatform platform) {
        // given
        Device device = 기기를_저장한다(platform);

        // when
        service.save(device.getPublicId(), OCCURRED_AT);

        // then
        assertThat(activityRepository.findAll()).singleElement().satisfies(activity -> {
            assertThat(activity.getDeviceId()).isEqualTo(device.getId());
            assertThat(activity.getActivityDate()).isEqualTo(LocalDate.of(2026, 9, 16));
            assertThat(activity.getCreatedAt()).isNotNull();
            assertThat(activity.getUpdatedAt()).isNotNull();
        });
    }

    @Test
    void 같은_날의_반복_활동은_기존_행과_감사_시각을_수정하지_않는다() {
        // given
        Device device = 기기를_저장한다(DevicePlatform.ANDROID);
        service.save(device.getPublicId(), OCCURRED_AT);
        DeviceDailyActivity first = activityRepository.findAll().getFirst();

        // when
        service.save(device.getPublicId(), OCCURRED_AT.plusSeconds(60));

        // then
        assertThat(activityRepository.findAll()).singleElement().satisfies(activity -> {
            assertThat(activity.getId()).isEqualTo(first.getId());
            assertThat(activity.getCreatedAt()).isEqualTo(first.getCreatedAt());
            assertThat(activity.getUpdatedAt()).isEqualTo(first.getUpdatedAt());
        });
    }

    @Test
    void KST_자정_전후의_활동은_각각_다른_날짜로_저장한다() {
        // given
        Device device = 기기를_저장한다(DevicePlatform.ANDROID);

        // when
        service.save(device.getPublicId(), OCCURRED_AT.minusNanos(1));
        service.save(device.getPublicId(), OCCURRED_AT);

        // then
        assertThat(activityRepository.findAll()).extracting(DeviceDailyActivity::getActivityDate)
                .containsExactlyInAnyOrder(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 16));
    }

    @Test
    void 등록되지_않은_기기는_활동을_저장하지_않는다() {
        // given / when
        service.save(UUID.randomUUID(), OCCURRED_AT);

        // then
        assertThat(activityRepository.count()).isZero();
    }

    @Test
    void 같은_기기의_동시_활동은_예외_없이_한_행만_저장한다() throws Exception {
        // given
        Device device = 기기를_저장한다(DevicePlatform.ANDROID);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);

        // when
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("concurrent start timed out");
                    }
                    service.save(device.getPublicId(), OCCURRED_AT);
                    return null;
                }));
            }
            boolean allReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertThat(allReady).isTrue();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }

        // then
        assertThat(activityRepository.count()).isOne();
    }

    @Test
    void 활동_저장이_실패해도_예외를_처리한_바깥_트랜잭션은_커밋할_수_있다() {
        // given
        Device device = 기기를_저장한다(DevicePlatform.ANDROID);
        jdbcClient.sql("""
                ALTER TABLE device_daily_activities ADD CONSTRAINT ck_test_activity_failure
                CHECK (activity_date < DATE '2000-01-01')
                """).update();
        try {
            // when
            Long savedDeviceId = transactionTemplate.execute(status -> {
                Device anotherDevice = 기기를_저장한다(DevicePlatform.IOS);
                assertThatThrownBy(() -> service.save(device.getPublicId(), OCCURRED_AT))
                        .isInstanceOf(DataIntegrityViolationException.class);
                return anotherDevice.getId();
            });

            // then
            assertThat(deviceRepository.existsById(savedDeviceId)).isTrue();
            assertThat(activityRepository.count()).isZero();
        } finally {
            jdbcClient.sql("ALTER TABLE device_daily_activities DROP CONSTRAINT ck_test_activity_failure").update();
        }
    }

    @Test
    void 플랫폼별로_기준일과_최근_30일의_고유_기기를_집계한다() {
        // given
        Device android = 기기를_저장한다(DevicePlatform.ANDROID);
        Device androidAtStart = 기기를_저장한다(DevicePlatform.ANDROID);
        Device ios = 기기를_저장한다(DevicePlatform.IOS);
        Device iosYesterday = 기기를_저장한다(DevicePlatform.IOS);
        Device outsideWindow = 기기를_저장한다(DevicePlatform.ANDROID);
        service.save(android.getPublicId(), OCCURRED_AT);
        service.save(android.getPublicId(), OCCURRED_AT.plusSeconds(60));
        service.save(android.getPublicId(), OCCURRED_AT.minus(1, ChronoUnit.DAYS));
        service.save(android.getPublicId(), OCCURRED_AT.minus(29, ChronoUnit.DAYS));
        service.save(androidAtStart.getPublicId(), OCCURRED_AT.minus(29, ChronoUnit.DAYS));
        service.save(ios.getPublicId(), OCCURRED_AT);
        service.save(iosYesterday.getPublicId(), OCCURRED_AT.minusNanos(1));
        service.save(outsideWindow.getPublicId(), OCCURRED_AT.minus(29, ChronoUnit.DAYS).minusNanos(1));
        service.save(outsideWindow.getPublicId(), OCCURRED_AT.plus(1, ChronoUnit.DAYS));

        // when
        List<DeviceActivityCount> counts = service.findCounts(LocalDate.of(2026, 9, 16));

        // then
        assertThat(counts).containsExactlyInAnyOrder(
                DeviceActivityCount.of(DevicePlatform.ANDROID, 1, 2),
                DeviceActivityCount.of(DevicePlatform.IOS, 1, 2)
        );
    }

    @Test
    void 기준일_활동이_없어도_최근_활동은_MAU에_남고_없는_플랫폼은_0이다() {
        // given
        Device device = 기기를_저장한다(DevicePlatform.ANDROID);
        service.save(device.getPublicId(), OCCURRED_AT.minusNanos(1));

        // when
        List<DeviceActivityCount> counts = service.findCounts(LocalDate.of(2026, 9, 16));

        // then
        assertThat(counts).containsExactlyInAnyOrder(
                DeviceActivityCount.of(DevicePlatform.ANDROID, 0, 1),
                DeviceActivityCount.of(DevicePlatform.IOS, 0, 0)
        );
    }

    @Test
    void 활동이_없으면_등록된_기기가_있어도_두_플랫폼의_집계는_0이다() {
        // given
        기기를_저장한다(DevicePlatform.ANDROID);
        기기를_저장한다(DevicePlatform.IOS);

        // when
        List<DeviceActivityCount> counts = service.findCounts(LocalDate.of(2026, 9, 16));

        // then
        assertThat(counts).containsExactlyInAnyOrder(
                DeviceActivityCount.of(DevicePlatform.ANDROID, 0, 0),
                DeviceActivityCount.of(DevicePlatform.IOS, 0, 0)
        );
    }

    private Device 기기를_저장한다(DevicePlatform platform) {
        Device device = deviceRepository.save(기본_기기_빌더().platform(platform).build());
        createdDeviceIds.add(device.getId());
        return device;
    }
}
