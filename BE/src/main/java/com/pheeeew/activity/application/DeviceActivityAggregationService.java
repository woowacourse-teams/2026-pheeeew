package com.pheeeew.activity.application;

import com.pheeeew.activity.application.dto.DeviceActivityCount;
import com.pheeeew.activity.application.dto.DeviceActivitySnapshot;
import com.pheeeew.activity.domain.DeviceActivityAggregationState;
import com.pheeeew.activity.domain.repository.DeviceActivityAggregationStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class DeviceActivityAggregationService {

    private static final ZoneId ACTIVITY_ZONE = ZoneId.of("Asia/Seoul");

    private final DeviceActivityAggregationStateRepository stateRepository;
    private final DeviceActivitySummaryService summaryService;
    private final DeviceDailyActivityService activityService;
    private final Clock clock;

    @Transactional(timeout = 3)
    public void initialize() {
        // 재시작 후 다시 초기화해도 최초 수집 시작 시각과 진행 상태를 유지한다.
        stateRepository.saveIfAbsent(clock.instant());
    }

    @Transactional(timeout = 3)
    public boolean finalizeNextDate() {
        // 집계 작업은 단일 스케줄러에서 순차 실행한다.
        DeviceActivityAggregationState state = stateRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("활동 집계 상태가 초기화되지 않았습니다."));
        LocalDate activityDate = state.nextFinalizationDate();
        LocalDate today = clock.instant().atZone(ACTIVITY_ZONE).toLocalDate();
        if (!activityDate.isBefore(today)) {
            return false;
        }

        // 집계 결과와 완료 날짜는 함께 저장하거나 함께 롤백한다.
        summaryService.save(activityDate);
        state.completeFinalization(activityDate);
        return true;
    }

    @Transactional(timeout = 3)
    public DeviceActivitySnapshot update() {
        DeviceActivityAggregationState state = stateRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("활동 집계 상태가 초기화되지 않았습니다."));

        // 자정을 지나더라도 집계 기준일과 시각이 어긋나지 않도록 같은 시각을 사용한다.
        Instant aggregatedAt = clock.instant();
        LocalDate activityDate = aggregatedAt.atZone(ACTIVITY_ZONE).toLocalDate();
        List<DeviceActivityCount> counts = activityService.findCounts(activityDate);

        // 조회 실패 시 성공 시각을 갱신하지 않으며, 저장까지 성공해야 호출자에게 결과가 전달된다.
        state.completeAggregation(aggregatedAt);
        return DeviceActivitySnapshot.of(activityDate, aggregatedAt, state.getCreatedAt(), counts);
    }
}
