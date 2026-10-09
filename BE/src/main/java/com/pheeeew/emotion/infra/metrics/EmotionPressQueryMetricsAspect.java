package com.pheeeew.emotion.infra.metrics;

import com.pheeeew.common.logging.RequestTiming;
import com.pheeeew.common.logging.RequestTiming.Stage;
import com.pheeeew.emotion.application.EmotionPressMetrics;
import com.pheeeew.emotion.application.EmotionPressMetrics.QueryOperation;
import io.micrometer.core.instrument.Timer;
import java.util.function.LongSupplier;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class EmotionPressQueryMetricsAspect {

    private final EmotionPressMetrics metrics;
    private final LongSupplier nanoTime;

    @Autowired
    public EmotionPressQueryMetricsAspect(EmotionPressMetrics metrics) {
        this(metrics, System::nanoTime);
    }

    EmotionPressQueryMetricsAspect(EmotionPressMetrics metrics, LongSupplier nanoTime) {
        this.metrics = metrics;
        this.nanoTime = nanoTime;
    }

    @Around("execution(* com.pheeeew.emotion.domain.repository.DeviceDailyPressRepository.increase(..)) || "
            + "execution(* com.pheeeew.emotion.domain.repository.DeviceDailyPressRepository.findByPressDateAndDeviceId(..)) || "
            + "execution(* com.pheeeew.emotion.domain.repository.DeviceDailyPressRepository.sumByPressDate(..))")
    public Object recordQuery(ProceedingJoinPoint joinPoint) throws Throwable {
        QueryOperation operation = switch (joinPoint.getSignature().getName()) {
            case "increase" -> QueryOperation.INCREASE;
            case "findByPressDateAndDeviceId" -> QueryOperation.MY_DAILY;
            case "sumByPressDate" -> QueryOperation.DAILY_TOTAL;
            default -> throw new IllegalStateException("계측 대상이 아닌 프레스 메서드입니다.");
        };
        Timer.Sample sample = metrics.startQuery();
        long startedAt = nanoTime.getAsLong();
        boolean succeeded = false;
        try {
            Object result = joinPoint.proceed();
            succeeded = true;
            return result;
        } finally {
            long durationNanos = nanoTime.getAsLong() - startedAt;
            try {
                RequestTiming.record(requestStage(operation), durationNanos);
            } catch (RuntimeException ignored) {
                // 요청별 기록 실패가 저장·조회 결과나 전체 지표 기록을 막지 않게 한다.
            }
            try {
                metrics.recordQuery(sample, operation, succeeded);
            } catch (RuntimeException ignored) {
                // 계측 기록 실패가 저장·조회 결과나 원래 예외를 바꾸지 않게 한다.
            }
        }
    }

    private Stage requestStage(QueryOperation operation) {
        return switch (operation) {
            case INCREASE -> Stage.PRESS_INCREASE;
            case MY_DAILY -> Stage.PRESS_MY_DAILY;
            case DAILY_TOTAL -> Stage.PRESS_DAILY_TOTAL;
        };
    }
}
