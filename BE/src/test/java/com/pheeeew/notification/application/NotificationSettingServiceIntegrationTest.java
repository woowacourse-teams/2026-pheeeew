package com.pheeeew.notification.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.notification.fixture.PushTokenFixture.기본_등록_값;
import static com.pheeeew.notification.fixture.PushTokenFixture.새_등록_값;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.notification.domain.NotificationSettings;
import com.pheeeew.support.PostgisDataJpaTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({NotificationSettingService.class, DevicePushTokenRegistrationService.class})
class NotificationSettingServiceIntegrationTest {

    @Autowired
    private NotificationSettingService notificationSettingService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private JdbcClient jdbcClient;

    private final List<Long> 만든_기기들 = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (만든_기기들.isEmpty()) {
            return;
        }
        for (String table : List.of("device_push_tokens", "device_notification_settings")) {
            jdbcClient.sql("DELETE FROM " + table + " WHERE device_id IN (:deviceIds)")
                    .param("deviceIds", 만든_기기들)
                    .update();
        }
        jdbcClient.sql("DELETE FROM devices WHERE id IN (:deviceIds)")
                .param("deviceIds", 만든_기기들)
                .update();
    }

    @Test
    void 같은_기기가_새_값을_보내면_이전_값을_바꾼다() {
        // given
        Device device = 기기를_만든다();
        notificationSettingService.updatePushToken(device.getPublicId(), 기본_등록_값);

        // when
        notificationSettingService.updatePushToken(device.getPublicId(), 새_등록_값);

        // then
        assertThat(등록_값들(device)).containsExactly(새_등록_값);
    }

    @Test
    void 같은_값을_여러_번_보내도_행은_하나다() {
        // given
        Device device = 기기를_만든다();

        // when
        notificationSettingService.updatePushToken(device.getPublicId(), 기본_등록_값);
        assertThat(등록_값들(device)).containsExactly(기본_등록_값);
        notificationSettingService.updatePushToken(device.getPublicId(), 기본_등록_값);

        // then
        assertThat(같은_값의_행_수(기본_등록_값)).isOne();
    }

    @Test
    void 다른_기기가_가진_값을_등록하면_마지막_등록_기기로_옮긴다() {
        // given
        Device 이전_기기 = 기기를_만든다();
        Device 새_기기 = 기기를_만든다();
        notificationSettingService.updatePushToken(이전_기기.getPublicId(), 기본_등록_값);

        // when
        notificationSettingService.updatePushToken(새_기기.getPublicId(), 기본_등록_값);

        // then
        assertThat(등록_값들(이전_기기)).isEmpty();
        assertThat(등록_값들(새_기기)).containsExactly(기본_등록_값);
    }

    @Test
    void 해제하면_기기의_값을_지우고_값이_없어도_실패하지_않는다() {
        // given
        Device device = 기기를_만든다();
        notificationSettingService.updatePushToken(device.getPublicId(), 기본_등록_값);

        // when
        notificationSettingService.deletePushToken(device.getPublicId());
        notificationSettingService.deletePushToken(device.getPublicId());

        // then
        assertThat(등록_값들(device)).isEmpty();
    }

    @Test
    void 바꾼_설정은_다른_기기에_영향을_주지_않는다() {
        // given
        Device device = 기기를_만든다();
        Device 다른_기기 = 기기를_만든다();

        // when
        notificationSettingService.updateSettings(device.getPublicId(), false, true);

        // then
        assertThat(notificationSettingService.findSettings(device.getPublicId())).isEqualTo(NotificationSettings.of(false, true));
        assertThat(notificationSettingService.findSettings(다른_기기.getPublicId())).isEqualTo(NotificationSettings.of(true, true));
    }

    @Test
    void 설정을_다시_바꾸면_마지막에_바꾼_값이_남는다() {
        // given
        Device device = 기기를_만든다();
        notificationSettingService.updateSettings(device.getPublicId(), false, true);

        // when
        notificationSettingService.updateSettings(device.getPublicId(), true, false);

        // then
        assertThat(notificationSettingService.findSettings(device.getPublicId())).isEqualTo(NotificationSettings.of(true, false));
        assertThat(jdbcClient.sql("SELECT count(*) FROM device_notification_settings WHERE device_id = :deviceId")
                .param("deviceId", device.getId())
                .query(Integer.class)
                .single()).isOne();
    }

    @Test
    void 등록_값을_바꾸거나_해제해도_바꾼_설정은_유지된다() {
        // given
        Device device = 기기를_만든다();
        notificationSettingService.updateSettings(device.getPublicId(), false, false);

        // when
        notificationSettingService.updatePushToken(device.getPublicId(), 기본_등록_값);
        notificationSettingService.updatePushToken(device.getPublicId(), 새_등록_값);
        notificationSettingService.deletePushToken(device.getPublicId());
        notificationSettingService.updatePushToken(device.getPublicId(), 새_등록_값);

        // then
        assertThat(notificationSettingService.findSettings(device.getPublicId())).isEqualTo(NotificationSettings.of(false, false));
    }

    @Test
    void 없는_기기의_요청은_기기_없음으로_거절한다() {
        // given
        UUID 없는_기기 = UUID.randomUUID();

        // when / then
        assertThatThrownBy(() -> notificationSettingService.updatePushToken(없는_기기, 기본_등록_값))
                .isInstanceOf(DeviceException.class)
                .extracting("errorCode").isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND);
    }

    private Device 기기를_만든다() {
        Device device = deviceRepository.saveAndFlush(기본_기기_빌더().build());
        만든_기기들.add(device.getId());
        return device;
    }

    private List<String> 등록_값들(Device device) {
        return jdbcClient.sql("SELECT token FROM device_push_tokens WHERE device_id = :deviceId")
                .param("deviceId", device.getId())
                .query(String.class)
                .list();
    }

    private int 같은_값의_행_수(String token) {
        return jdbcClient.sql("SELECT count(*) FROM device_push_tokens WHERE token = :token")
                .param("token", token)
                .query(Integer.class)
                .single();
    }
}
