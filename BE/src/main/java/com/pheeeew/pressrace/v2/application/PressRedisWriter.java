package com.pheeeew.pressrace.v2.application;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;

import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.PressCounts;
import com.pheeeew.pressrace.application.PressRaceService;
import com.pheeeew.pressrace.exception.PressRaceException;
import com.pheeeew.pressrace.v2.domain.PressWriteResult;
import com.pheeeew.pressrace.v2.infra.PressRedisStore;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@ConditionalOnProperty(name = "pheeeew.press-race.write-mode", havingValue = "REDIS")
@Component
public class PressRedisWriter {

    private static final long RATE_LIMITED_RETRY_SECONDS = 5;
    private static final long STORE_UNAVAILABLE_RETRY_SECONDS = 1;
    private static final int MAX_ATTEMPTS = 3;

    private final PressRedisStore pressRedisStore;
    private final PressRedisSeeder pressRedisSeeder;
    private final PressRaceService pressRaceService;
    private final DeviceRepository deviceRepository;
    private final Clock clock;
    private final Set<UUID> knownDevices = ConcurrentHashMap.newKeySet();

    public Map<EmotionState, Long> press(UUID devicePublicId, PressCounts counts) {
        return press(devicePublicId, counts, null);
    }

    public Map<EmotionState, Long> press(UUID devicePublicId, PressCounts counts, UUID requestId) {
        requireDevice(devicePublicId);
        pressRaceService.admissionRevision();
        LocalDate date = LocalDate.now(clock);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            PressWriteResult result = write(devicePublicId, date, counts, requestId);
            switch (result.status()) {
                case APPLIED, DUPLICATE -> {
                    return result.counts();
                }
                case RATE_LIMITED -> throw new PressRaceException("RATE_LIMITED", RATE_LIMITED_RETRY_SECONDS);
                case INPUT_CONFLICT -> throw new PressRaceException("INPUT_CONFLICT");
                case DATE_MISMATCH -> date = result.storeDate();
                case NOT_SEEDED -> seed(date);
            }
        }
        throw new PressRaceException("STORE_UNAVAILABLE", STORE_UNAVAILABLE_RETRY_SECONDS);
    }

    private void requireDevice(UUID devicePublicId) {
        if (knownDevices.contains(devicePublicId)) {
            return;
        }
        if (deviceRepository.findByPublicId(devicePublicId).isEmpty()) {
            throw new DeviceException(DEVICE_NOT_FOUND);
        }
        knownDevices.add(devicePublicId);
    }

    private void seed(LocalDate date) {
        try {
            pressRedisSeeder.seed(date);
        } catch (DataAccessException exception) {
            throw new PressRaceException("STORE_UNAVAILABLE", STORE_UNAVAILABLE_RETRY_SECONDS);
        }
    }

    private PressWriteResult write(UUID devicePublicId, LocalDate date, PressCounts counts, UUID requestId) {
        try {
            if (requestId == null) {
                return pressRedisStore.write(devicePublicId, date, counts.presses(), UUID.randomUUID());
            }
            return pressRedisStore.write(devicePublicId, date, counts.presses(), UUID.randomUUID(), requestId,
                    requestHash(counts));
        } catch (DataAccessException exception) {
            throw new PressRaceException("STORE_UNAVAILABLE", STORE_UNAVAILABLE_RETRY_SECONDS);
        }
    }

    private String requestHash(PressCounts counts) {
        return counts.presses().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(EmotionState::name)))
                .map(entry -> entry.getKey().name() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
    }
}
