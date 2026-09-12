package com.pheeeew.device.application;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_ATTESTATION_UNAVAILABLE;

import com.pheeeew.device.domain.repository.DeviceAttestationBudgetRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.device.infra.attestation.PlayIntegrityProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class DeviceAttestationBudgetService {

    private static final Duration BUDGET_RETRY_AFTER = Duration.ofHours(1);

    private final DeviceAttestationBudgetRepository deviceAttestationBudgetRepository;
    private final PlayIntegrityProperties playIntegrityProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consumeCall() {
        int consumedCount = deviceAttestationBudgetRepository.increaseCallCount(
                LocalDate.now(ZoneOffset.UTC),
                playIntegrityProperties.dailyCallBudget(),
                Instant.now()
        );
        if (consumedCount == 0) {
            throw new DeviceException(DEVICE_ATTESTATION_UNAVAILABLE, null, BUDGET_RETRY_AFTER);
        }
    }
}
