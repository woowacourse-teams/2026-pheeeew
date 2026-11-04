package com.pheeeew.notification.domain.repository;

import com.pheeeew.notification.domain.DevicePushToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DevicePushTokenRepository extends JpaRepository<DevicePushToken, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO device_push_tokens (device_id, token, created_at, updated_at)
                 VALUES (:#{#pushToken.deviceId}, :#{#pushToken.token}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (device_id) DO NOTHING
            """, nativeQuery = true)
    int saveIfAbsent(@Param("pushToken") DevicePushToken pushToken);

    Optional<DevicePushToken> findByDeviceId(Long deviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM DevicePushToken t WHERE t.deviceId = :deviceId")
    Optional<DevicePushToken> findByDeviceIdForUpdate(@Param("deviceId") Long deviceId);

    @Modifying
    @Query("DELETE FROM DevicePushToken t WHERE t.token = :token AND t.deviceId <> :deviceId")
    void deleteByTokenAndDeviceIdNot(@Param("token") String token, @Param("deviceId") Long deviceId);

    @Modifying
    @Query("DELETE FROM DevicePushToken t WHERE t.deviceId = :deviceId")
    void deleteByDeviceId(@Param("deviceId") Long deviceId);

    @Modifying
    @Query("DELETE FROM DevicePushToken t WHERE t.deviceId = :deviceId AND t.token = :token")
    void deleteByDeviceIdAndToken(@Param("deviceId") Long deviceId, @Param("token") String token);
}
