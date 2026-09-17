package com.pheeeew.activity.application;

import com.pheeeew.activity.domain.repository.DeviceActivityAggregationStateRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class DeviceActivityAggregationService {

    private final DeviceActivityAggregationStateRepository stateRepository;
    private final Clock clock;

    @Transactional(timeout = 3)
    public void initialize() {
        // 재시작하거나 여러 서버가 동시에 초기화해도 최초 수집 시작 시각과 진행 상태를 유지한다.
        stateRepository.saveIfAbsent(clock.instant());
    }
}
