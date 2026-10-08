package com.pheeeew.device.domain.repository;

import com.pheeeew.device.domain.Device;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByRequestId(UUID requestId);

    Optional<Device> findByPublicId(UUID publicId);

    @Query("SELECT count(d) > 0 FROM Device d WHERE lower(d.nickname) = :nickname")
    boolean existsByNickname(@Param("nickname") String nickname);
}
