package com.pheeeew.activity.infra;

import static com.pheeeew.activity.fixture.DeviceDailyActivityFixture.기본_일별_활동_빌더;
import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.activity.domain.DeviceDailyActivity;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.DevicePlatform;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

@PostgisDataJpaTest
class DeviceDailyActivitySchemaIntegrationTest {

    private static final LocalDate ACTIVITY_DATE = LocalDate.of(2026, 9, 16);

    @Autowired
    private EntityManager entityManager;

    @ParameterizedTest
    @EnumSource(DevicePlatform.class)
    void 등록된_기기의_활동_날짜와_감사_시각을_저장한다(DevicePlatform platform) {
        // given
        Device device = 기본_기기_빌더().platform(platform).build();
        entityManager.persist(device);
        DeviceDailyActivity activity = 기본_일별_활동_빌더().deviceId(device.getId()).build();

        // when
        entityManager.persist(activity);
        entityManager.flush();
        entityManager.clear();
        DeviceDailyActivity saved = entityManager.find(DeviceDailyActivity.class, activity.getId());

        // then
        assertThat(saved.getDeviceId()).isEqualTo(device.getId());
        assertThat(saved.getActivityDate()).isEqualTo(ACTIVITY_DATE);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(entityManager.find(Device.class, saved.getDeviceId()).getPlatform()).isEqualTo(platform);
    }

    @Test
    void 같은_기기의_같은_날짜_활동은_중복_저장할_수_없다() {
        // given
        Device device = 기본_기기_빌더().build();
        entityManager.persist(device);
        entityManager.persist(기본_일별_활동_빌더().deviceId(device.getId()).build());
        entityManager.flush();
        DeviceDailyActivity duplicate = 기본_일별_활동_빌더().deviceId(device.getId()).build();

        // when / then
        assertThatThrownBy(() -> entityManager.persist(duplicate))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("uk_device_daily_activities_date_device");
    }

    @Test
    void 기기나_날짜가_다르면_각각_활동을_저장한다() {
        // given
        Device firstDevice = 기본_기기_빌더().build();
        Device secondDevice = 기본_기기_빌더().build();
        entityManager.persist(firstDevice);
        entityManager.persist(secondDevice);
        List<DeviceDailyActivity> activities = List.of(
                기본_일별_활동_빌더().deviceId(firstDevice.getId()).build(),
                기본_일별_활동_빌더().deviceId(secondDevice.getId()).build(),
                기본_일별_활동_빌더().deviceId(firstDevice.getId()).activityDate(ACTIVITY_DATE.plusDays(1)).build()
        );

        // when
        activities.forEach(entityManager::persist);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(activities).extracting(DeviceDailyActivity::getId).doesNotHaveDuplicates();
        for (DeviceDailyActivity activity : activities) {
            DeviceDailyActivity saved = entityManager.find(DeviceDailyActivity.class, activity.getId());
            assertThat(saved.getDeviceId()).isEqualTo(activity.getDeviceId());
            assertThat(saved.getActivityDate()).isEqualTo(activity.getActivityDate());
        }
    }

    @Test
    void 등록되지_않은_기기의_활동은_저장할_수_없다() {
        // given
        DeviceDailyActivity activity = 기본_일별_활동_빌더().deviceId(-1L).build();

        // when / then
        assertThatThrownBy(() -> entityManager.persist(activity))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("fk_device_daily_activities_device");
    }
}
