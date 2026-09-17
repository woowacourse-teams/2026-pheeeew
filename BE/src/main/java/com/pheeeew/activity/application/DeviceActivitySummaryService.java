package com.pheeeew.activity.application;

import com.pheeeew.activity.application.dto.DeviceActivityCount;
import com.pheeeew.activity.domain.repository.DeviceActivitySummaryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class DeviceActivitySummaryService {

    private static final ZoneId ACTIVITY_ZONE = ZoneId.of("Asia/Seoul");

    private final DeviceDailyActivityService activityService;
    private final DeviceActivitySummaryRepository summaryRepository;
    private final Clock clock;

    @Transactional(timeout = 3)
    public void save(LocalDate activityDate) {
        Objects.requireNonNull(activityDate);
        Instant aggregatedAt = clock.instant();
        if (!activityDate.isBefore(aggregatedAt.atZone(ACTIVITY_ZONE).toLocalDate())) {
            throw new IllegalArgumentException("최종 집계는 KST 기준으로 종료된 날짜만 저장할 수 있습니다.");
        }

        List<DeviceActivityCount> counts = activityService.findCounts(activityDate);

        // 두 플랫폼을 같은 트랜잭션에 저장한다. 하나라도 실패하면 앞서 저장한 결과도 취소된다.
        // 재시도나 동시 실행에서 이미 저장된 최종 집계는 덮어쓰지 않는다.
        for (DeviceActivityCount count : counts) {
            summaryRepository.saveIfAbsent(
                    activityDate, count.platform().name(), count.dau(), count.mau(), aggregatedAt
            );
        }
    }
}
