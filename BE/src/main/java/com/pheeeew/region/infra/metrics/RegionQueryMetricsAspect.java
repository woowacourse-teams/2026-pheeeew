package com.pheeeew.region.infra.metrics;

import com.pheeeew.region.infra.metrics.RegionQueryMetrics.Operation;
import java.util.function.LongSupplier;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RegionQueryMetricsAspect {

    private final RegionQueryMetrics metrics;
    private final LongSupplier nanoTime;

    @Autowired
    public RegionQueryMetricsAspect(RegionQueryMetrics metrics) {
        this(metrics, System::nanoTime);
    }

    RegionQueryMetricsAspect(RegionQueryMetrics metrics, LongSupplier nanoTime) {
        this.metrics = metrics;
        this.nanoTime = nanoTime;
    }

    @Around("execution(* com.pheeeew.region.domain.repository.RegionRepository.findIntersectingRegions(..)) || "
            + "execution(* com.pheeeew.region.domain.repository.RegionRepository.areBoundariesVerified(..)) || "
            + "execution(* com.pheeeew.region.domain.repository.RegionRepository.isAggregationReady(..)) || "
            + "execution(* com.pheeeew.region.domain.repository.RegionRepository.findEmdCode(..))")
    public Object recordQuery(ProceedingJoinPoint joinPoint) throws Throwable {
        Operation operation = switch (joinPoint.getSignature().getName()) {
            case "findIntersectingRegions" -> Operation.INTERSECTING_REGIONS;
            case "areBoundariesVerified" -> Operation.BOUNDARIES_VERIFIED;
            case "isAggregationReady" -> Operation.AGGREGATION_READY;
            case "findEmdCode" -> Operation.EMD_CODE;
            default -> throw new IllegalStateException("계측 대상이 아닌 지역 조회 메서드입니다.");
        };
        long startedAt = nanoTime.getAsLong();
        boolean succeeded = false;
        try {
            Object result = joinPoint.proceed();
            succeeded = true;
            return result;
        } finally {
            try {
                metrics.recordQuery(operation, nanoTime.getAsLong() - startedAt, succeeded);
            } catch (RuntimeException ignored) {
                // 계측 기록 실패가 조회 결과나 원래 예외를 바꾸지 않게 한다.
            }
        }
    }
}
