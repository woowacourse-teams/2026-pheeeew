package com.pheeeew.activity.infra;

import com.pheeeew.activity.application.DeviceActivityAggregationService;
import com.pheeeew.activity.infra.metrics.DeviceActivityMetrics;
import com.pheeeew.common.logging.ExceptionLogFormatter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Profile("prod")
@Component
public class DeviceActivityAggregationScheduler {

    private final DeviceActivityAggregationService aggregationService;
    private final DeviceActivityMetrics metrics;
    private final Instant collectionStartedAt;
    private final Counter failures;
    private final ExceptionLogFormatter exceptionLogFormatter = new ExceptionLogFormatter();

    public DeviceActivityAggregationScheduler(
            DeviceActivityAggregationService aggregationService,
            DeviceActivityMetrics metrics,
            MeterRegistry registry,
            Clock clock
    ) {
        this.aggregationService = aggregationService;
        this.metrics = metrics;
        // 초기화 재시도가 자정을 넘어도 수집 시작일이 뒤로 밀리지 않도록 한 번만 기억한다.
        this.collectionStartedAt = clock.instant();
        this.failures = registry.counter("pheeeew.activity.aggregation.failures");
    }

    // 기동 후 첫 실행부터 집계하고, 이전 실행이 끝난 뒤 기본 5분을 기다린다.
    @Scheduled(fixedDelayString = "${pheeeew.activity.aggregation-interval:PT5M}")
    public void update() {
        try {
            // 초기화도 스케줄러 안에서 수행하여 실패가 애플리케이션 기동을 막지 않도록 한다.
            aggregationService.initialize(collectionStartedAt);
            // 서비스 트랜잭션이 성공적으로 끝난 결과만 지표에 반영한다.
            metrics.update(aggregationService.update());
        } catch (RuntimeException failure) {
            failures.increment();
            // SQL 값이나 기기 식별자가 포함될 수 있는 예외 원문은 남기지 않는다.
            log.atError()
                    .addKeyValue("event", "device_activity_aggregation_failed")
                    .addKeyValue("errorStack", exceptionLogFormatter.format(failure))
                    .log("기기 활동 집계에 실패했습니다");
        }
    }
}
