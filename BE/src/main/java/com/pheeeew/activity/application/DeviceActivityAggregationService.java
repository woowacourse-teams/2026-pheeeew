package com.pheeeew.activity.application;

import com.pheeeew.activity.domain.DeviceActivityAggregationState;
import com.pheeeew.activity.domain.repository.DeviceActivityAggregationStateRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class DeviceActivityAggregationService {

    private static final ZoneId ACTIVITY_ZONE = ZoneId.of("Asia/Seoul");

    private final DeviceActivityAggregationStateRepository stateRepository;
    private final DeviceActivitySummaryService summaryService;
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
}
