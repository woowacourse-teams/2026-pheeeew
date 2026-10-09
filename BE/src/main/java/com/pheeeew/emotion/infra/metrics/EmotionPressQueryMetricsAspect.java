package com.pheeeew.emotion.infra.metrics;

import com.pheeeew.emotion.application.EmotionPressMetrics;
import com.pheeeew.emotion.application.EmotionPressMetrics.QueryOperation;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Aspect
@Component
public class EmotionPressQueryMetricsAspect {

    private final EmotionPressMetrics metrics;

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
        boolean succeeded = false;
        try {
            Object result = joinPoint.proceed();
            succeeded = true;
            return result;
        } finally {
            try {
                metrics.recordQuery(sample, operation, succeeded);
            } catch (RuntimeException ignored) {
                // 계측 기록 실패가 저장·조회 결과나 원래 예외를 바꾸지 않게 한다.
            }
        }
    }
}
