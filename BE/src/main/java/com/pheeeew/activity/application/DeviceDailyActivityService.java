package com.pheeeew.activity.application;

import com.pheeeew.activity.application.dto.DeviceActivityCount;
import com.pheeeew.activity.domain.repository.DeviceDailyActivityRepository;
import com.pheeeew.activity.domain.repository.projection.DeviceActivityCountProjection;
import com.pheeeew.device.domain.DevicePlatform;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    @Transactional(readOnly = true, timeout = 3)
    public List<DeviceActivityCount> findCounts(LocalDate activityDate) {
        Objects.requireNonNull(activityDate);

        // KST 기준일을 포함한 최근 30일을 조회해 기준일의 DAU와 기간 전체의 MAU를 함께 계산한다.
        List<DeviceActivityCountProjection> projections = deviceDailyActivityRepository.findCounts(
                activityDate.minusDays(29), activityDate
        );

        // 활동이 없는 플랫폼은 조회 결과에 없으므로, 조회 성공 후 모든 플랫폼의 기본값을 0으로 준비한다.
        Map<DevicePlatform, DeviceActivityCount> counts = new EnumMap<>(DevicePlatform.class);
        for (DevicePlatform platform : DevicePlatform.values()) {
            counts.put(platform, DeviceActivityCount.of(platform, 0, 0));
        }

        // 활동이 있는 플랫폼은 기본값을 실제 집계 결과로 교체한다.
        for (DeviceActivityCountProjection projection : projections) {
            DevicePlatform platform = projection.getPlatform();
            counts.put(platform, DeviceActivityCount.of(platform, projection.getDau(), projection.getMau()));
        }

        return List.copyOf(counts.values());
    }
}
