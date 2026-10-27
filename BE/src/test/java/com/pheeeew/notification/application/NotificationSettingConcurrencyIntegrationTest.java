package com.pheeeew.notification.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.notification.fixture.PushTokenFixture.기본_등록_값;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.notification.domain.DevicePushToken;
import com.pheeeew.notification.domain.repository.DevicePushTokenRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({NotificationSettingService.class, DevicePushTokenRegistrationService.class})
class NotificationSettingConcurrencyIntegrationTest {

    private static final Duration 대기_상한 = Duration.ofSeconds(10);

    @Autowired
    private NotificationSettingService notificationSettingService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoSpyBean
    private DevicePushTokenRepository devicePushTokenRepository;

    private final List<Long> 만든_기기들 = new ArrayList<>();

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM device_push_tokens WHERE device_id IN (:deviceIds)")
                .param("deviceIds", 만든_기기들).update();
        jdbcClient.sql("DELETE FROM device_notification_settings WHERE device_id IN (:deviceIds)")
                .param("deviceIds", 만든_기기들).update();
        jdbcClient.sql("DELETE FROM devices WHERE id IN (:deviceIds)")
                .param("deviceIds", 만든_기기들).update();
    }

    @Test
    void 두_기기가_같은_값을_동시에_넣다_충돌하면_새_트랜잭션으로_한_번_다시_해서_둘_다_끝난다() throws Exception {
        // given
        Device 먼저_넣는_기기 = 기기를_만든다();
        Device 나중에_넣는_기기 = 기기를_만든다();
        CountDownLatch 먼저_넣는_기기가_커밋_전에_멈춤 = new CountDownLatch(1);
        CountDownLatch 커밋_허용 = new CountDownLatch(1);
        먼저_넣는_기기의_등록을_커밋_전에_멈춘다(먼저_넣는_기기, 먼저_넣는_기기가_커밋_전에_멈춤, 커밋_허용);

        // when
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> 먼저 = executor.submit(() ->
                    notificationSettingService.updatePushToken(먼저_넣는_기기.getPublicId(), 기본_등록_값));
            assertThat(먼저_넣는_기기가_커밋_전에_멈춤.await(대기_상한.toSeconds(), TimeUnit.SECONDS)).isTrue();

            Future<?> 나중 = executor.submit(() ->
                    notificationSettingService.updatePushToken(나중에_넣는_기기.getPublicId(), 기본_등록_값));
            잠금에서_기다리는_세션이_생길_때까지_기다린다("INSERT INTO device_push_tokens");
            커밋_허용.countDown();

            먼저.get(대기_상한.toSeconds(), TimeUnit.SECONDS);
            나중.get(대기_상한.toSeconds(), TimeUnit.SECONDS);
        }

        // then
        assertThat(소유_기기들(기본_등록_값)).containsExactly(나중에_넣는_기기.getId());
        verify(devicePushTokenRepository, times(2)).saveIfAbsent(argThat(token -> 등록한_것(token, 나중에_넣는_기기)));
        verify(devicePushTokenRepository, times(1)).saveIfAbsent(argThat(token -> 등록한_것(token, 먼저_넣는_기기)));
    }

    private Device 기기를_만든다() {
        Device device = deviceRepository.saveAndFlush(기본_기기_빌더().build());
        만든_기기들.add(device.getId());
        return device;
    }

    private void 먼저_넣는_기기의_등록을_커밋_전에_멈춘다(Device 먼저_넣는_기기, CountDownLatch 멈춤, CountDownLatch 커밋_허용) {
        Answer<?> 실제_저장 = 실제_동작(devicePushTokenRepository);
        doAnswer(invocation -> {
            Object inserted = 실제_저장.answer(invocation);
            if (등록한_것(invocation.getArgument(0), 먼저_넣는_기기)) {
                멈춤.countDown();
                커밋_허용.await(대기_상한.toSeconds(), TimeUnit.SECONDS);
            }
            return inserted;
        }).when(devicePushTokenRepository).saveIfAbsent(any(DevicePushToken.class));
    }

    private boolean 등록한_것(DevicePushToken pushToken, Device device) {
        return pushToken != null && device.getId().equals(pushToken.getDeviceId());
    }

    private Answer<?> 실제_동작(Object spy) {
        return mockingDetails(spy).getMockCreationSettings().getDefaultAnswer();
    }

    private List<Long> 소유_기기들(String token) {
        return jdbcClient.sql("SELECT device_id FROM device_push_tokens WHERE token = :token")
                .param("token", token)
                .query(Long.class)
                .list();
    }

    private void 잠금에서_기다리는_세션이_생길_때까지_기다린다(String 문장) throws InterruptedException {
        Instant 기한 = Instant.now().plus(대기_상한);
        while (Instant.now().isBefore(기한)) {
            Integer 기다리는_세션_수 = jdbcClient.sql("""
                    SELECT count(*)
                      FROM pg_stat_activity
                     WHERE datname = current_database()
                       AND wait_event_type = 'Lock'
                       AND query ILIKE :pattern
                    """).param("pattern", "%" + 문장 + "%").query(Integer.class).single();
            if (기다리는_세션_수 > 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("나중 요청의 문장이 잠금에서 기다리지 않았다: " + 문장);
    }
}
