package com.pheeeew.device.domain.repository;

import com.pheeeew.device.domain.Device;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByRequestId(UUID requestId);

    Optional<Device> findByPublicId(UUID publicId);
}
