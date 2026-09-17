package com.pheeeew.device.domain.repository;

import com.pheeeew.device.domain.DeviceRefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRefreshTokenRepository extends JpaRepository<DeviceRefreshToken, Long> {

    Optional<DeviceRefreshToken> findBySessionId(UUID sessionId);
}
