package com.pheeeew.notification.domain.repository;

import com.pheeeew.notification.domain.NotificationSettings;
import com.pheeeew.notification.domain.PushRecipient;
import com.pheeeew.notification.domain.PushTarget;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class DevicePushTokenRepository {

    private final JdbcClient jdbc;

    public void deleteByTokenAndDeviceIdNot(String token, Long deviceId) {
        jdbc.sql("DELETE FROM device_push_tokens WHERE token = :token AND device_id <> :deviceId")
                .param("token", token).param("deviceId", deviceId)
                .update();
    }

    public void save(Long deviceId, String token, Instant now) {
        jdbc.sql("""
                INSERT INTO device_push_tokens (device_id, token, created_at, updated_at)
                     VALUES (:deviceId, :token, :now, :now)
                ON CONFLICT (device_id) DO UPDATE
                   SET token = :token,
                       updated_at = :now
                """).param("deviceId", deviceId).param("token", token)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .update();
    }

    public void deleteByDeviceId(Long deviceId) {
        jdbc.sql("DELETE FROM device_push_tokens WHERE device_id = :deviceId")
                .param("deviceId", deviceId)
                .update();
    }

    public void deleteByDeviceIdAndToken(Long deviceId, String token) {
        jdbc.sql("DELETE FROM device_push_tokens WHERE device_id = :deviceId AND token = :token")
                .param("deviceId", deviceId).param("token", token)
                .update();
    }

    public Optional<PushRecipient> findRecipientByDeviceId(Long deviceId) {
        return jdbc.sql("""
                SELECT tokens.device_id, tokens.token,
                       COALESCE(settings.collection_claimable_enabled, TRUE) AS collection_claimable_enabled,
                       COALESCE(settings.rank_changed_enabled, TRUE) AS rank_changed_enabled
                FROM device_push_tokens tokens
                LEFT JOIN device_notification_settings settings ON settings.device_id = tokens.device_id
                WHERE tokens.device_id = :deviceId
                """).param("deviceId", deviceId)
                .query((row, index) -> PushRecipient.of(
                        PushTarget.of(row.getLong("device_id"), row.getString("token")),
                        NotificationSettings.of(
                                row.getBoolean("collection_claimable_enabled"),
                                row.getBoolean("rank_changed_enabled"))))
                .optional();
    }
}
