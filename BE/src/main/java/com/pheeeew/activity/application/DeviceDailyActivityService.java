package com.pheeeew.activity.application;

import com.pheeeew.activity.domain.repository.DeviceDailyActivityRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class DeviceDailyActivityService {

    private static final ZoneId ACTIVITY_ZONE = ZoneId.of("Asia/Seoul");

    private final DeviceDailyActivityRepository deviceDailyActivityRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 3)
    public void save(UUID devicePublicId, Instant occurredAt) {
        Objects.requireNonNull(devicePublicId);
        Objects.requireNonNull(occurredAt);
        LocalDate activityDate = occurredAt.atZone(ACTIVITY_ZONE).toLocalDate();

        deviceDailyActivityRepository.saveIfAbsent(devicePublicId, activityDate, Instant.now(clock));
    }
}
