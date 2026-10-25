package com.pheeeew.notification.domain.repository;

import com.pheeeew.notification.domain.NotificationSettings;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class DeviceNotificationSettingRepository {

    private final JdbcClient jdbc;

    public Optional<NotificationSettings> findByDeviceId(Long deviceId) {
        return jdbc.sql("""
                SELECT collection_issued_enabled, rank_dropped_enabled
                  FROM device_notification_settings
                 WHERE device_id = :deviceId
                """).param("deviceId", deviceId)
                .query((row, rowNumber) -> NotificationSettings.of(
                        row.getBoolean("collection_issued_enabled"), row.getBoolean("rank_dropped_enabled")))
                .optional();
    }

    public void save(Long deviceId, NotificationSettings settings, Instant now) {
        jdbc.sql("""
                INSERT INTO device_notification_settings
                     (device_id, collection_issued_enabled, rank_dropped_enabled, created_at, updated_at)
                     VALUES (:deviceId, :collectionIssuedEnabled, :rankDroppedEnabled, :now, :now)
                ON CONFLICT (device_id) DO UPDATE
                   SET collection_issued_enabled = :collectionIssuedEnabled,
                       rank_dropped_enabled = :rankDroppedEnabled,
                       updated_at = :now
                """).param("deviceId", deviceId)
                .param("collectionIssuedEnabled", settings.collectionIssuedEnabled())
                .param("rankDroppedEnabled", settings.rankDroppedEnabled())
                .param("now", now.atOffset(ZoneOffset.UTC))
                .update();
    }
}
