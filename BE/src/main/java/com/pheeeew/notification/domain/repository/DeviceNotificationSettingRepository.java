package com.pheeeew.notification.domain.repository;

import com.pheeeew.notification.domain.DeviceNotificationSetting;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface DeviceNotificationSettingRepository extends JpaRepository<DeviceNotificationSetting, Long> {

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO device_notification_settings
                 (device_id, collection_claimable_enabled, rank_changed_enabled, created_at, updated_at)
                 VALUES (:#{#setting.deviceId}, :#{#setting.collectionClaimableEnabled},
                         :#{#setting.rankChangedEnabled}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (device_id) DO NOTHING
            """, nativeQuery = true)
    int saveIfAbsent(@Param("setting") DeviceNotificationSetting setting);

    Optional<DeviceNotificationSetting> findByDeviceId(Long deviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DeviceNotificationSetting s WHERE s.deviceId = :deviceId")
    Optional<DeviceNotificationSetting> findByDeviceIdForUpdate(@Param("deviceId") Long deviceId);
}
