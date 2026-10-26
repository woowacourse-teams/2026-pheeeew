package com.pheeeew.notification.domain.repository;

import java.time.Instant;
import java.time.ZoneOffset;
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
}
