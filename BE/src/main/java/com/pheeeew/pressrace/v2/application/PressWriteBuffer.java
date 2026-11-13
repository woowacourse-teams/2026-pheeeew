package com.pheeeew.pressrace.v2.application;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;

import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.PressCounts;
import com.pheeeew.emotion.domain.repository.DeviceDailyPressRepository;
import com.pheeeew.pressrace.application.PressRaceService;
import com.pheeeew.pressrace.exception.PressRaceException;
import com.pheeeew.pressrace.v2.domain.PressDaySnapshot;
import com.pheeeew.pressrace.v2.domain.PressDayTally;
import com.pheeeew.pressrace.v2.domain.PressDeviceBudget;
import com.pheeeew.pressrace.v2.domain.PressWriteMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PressWriteBuffer {

    private static final long RATE_LIMITED_RETRY_SECONDS = 5;

    private final boolean enabled;
    private final Clock clock;
    private final DeviceRepository deviceRepository;
    private final DeviceDailyPressRepository deviceDailyPressRepository;
    private final PressRaceService pressRaceService;
    private final Map<UUID, PressDeviceBudget> budgets = new ConcurrentHashMap<>();
    private final Map<DeviceDay, PressDayTally> tallies = new ConcurrentHashMap<>();

    public PressWriteBuffer(
            @Value("${pheeeew.press-race.write-mode:DATABASE}") PressWriteMode writeMode,
            Clock clock,
            DeviceRepository deviceRepository,
            DeviceDailyPressRepository deviceDailyPressRepository,
            PressRaceService pressRaceService
    ) {
        this.enabled = writeMode == PressWriteMode.MEMORY;
        this.clock = clock;
        this.deviceRepository = deviceRepository;
        this.deviceDailyPressRepository = deviceDailyPressRepository;
        this.pressRaceService = pressRaceService;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Map<EmotionState, Long> press(UUID devicePublicId, PressCounts counts) {
        Instant now = clock.instant();
        PressDeviceBudget budget = findBudget(devicePublicId, now);
        pressRaceService.admissionRevision();
        LocalDate today = LocalDate.ofInstant(now, clock.getZone());
        synchronized (budget) {
            PressDayTally tally = tallies.computeIfAbsent(DeviceDay.of(budget.deviceId(), today),
                    day -> PressDayTally.builder().deviceId(day.deviceId()).date(day.date()).build());
            synchronized (tally) {
                if (!tally.isLoaded()) {
                    tally.load(findStoredCounts(budget.deviceId(), today));
                }
                if (counts.appliedTotal() > 0) {
                    if (!budget.consume(counts.appliedTotal(), now)) {
                        throw new PressRaceException("RATE_LIMITED", RATE_LIMITED_RETRY_SECONDS);
                    }
                    tally.add(counts.presses(), now);
                }
                return tally.counts();
            }
        }
    }

    public List<PressDaySnapshot> findDirtySnapshots() {
        List<PressDaySnapshot> snapshots = new ArrayList<>();
        for (PressDayTally tally : tallies.values()) {
            synchronized (tally) {
                if (tally.isDirty()) {
                    snapshots.add(tally.snapshot());
                }
            }
        }
        return snapshots;
    }

    public void markFlushed(List<PressDaySnapshot> snapshots) {
        for (PressDaySnapshot snapshot : snapshots) {
            PressDayTally tally = tallies.get(DeviceDay.of(snapshot.deviceId(), snapshot.date()));
            if (tally != null) {
                synchronized (tally) {
                    tally.markFlushed(snapshot.version());
                }
            }
        }
    }

    public void evictFlushedBefore(LocalDate date) {
        tallies.forEach((day, tally) -> {
            synchronized (tally) {
                if (day.date().isBefore(date) && !tally.isDirty()) {
                    tallies.remove(day, tally);
                }
            }
        });
    }

    public long countDirty() {
        return tallies.values().stream().filter(this::isDirty).count();
    }

    private PressDeviceBudget findBudget(UUID devicePublicId, Instant now) {
        PressDeviceBudget budget = budgets.get(devicePublicId);
        if (budget != null) {
            return budget;
        }
        long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND))
                .getId();
        return budgets.computeIfAbsent(devicePublicId,
                ignored -> PressDeviceBudget.builder().deviceId(deviceId).refilledAt(now).build());
    }

    private Map<EmotionState, Long> findStoredCounts(long deviceId, LocalDate date) {
        Map<EmotionState, Long> stored = new EnumMap<>(EmotionState.class);
        deviceDailyPressRepository.findByPressDateAndDeviceId(date, deviceId)
                .forEach(sum -> stored.put(sum.state(), sum.pressCount()));
        return stored;
    }

    private boolean isDirty(PressDayTally tally) {
        synchronized (tally) {
            return tally.isDirty();
        }
    }

    private record DeviceDay(long deviceId, LocalDate date) {
        private static DeviceDay of(long deviceId, LocalDate date) {
            return new DeviceDay(deviceId, date);
        }
    }
}
