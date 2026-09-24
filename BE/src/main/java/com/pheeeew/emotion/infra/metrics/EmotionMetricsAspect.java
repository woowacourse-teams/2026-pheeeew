package com.pheeeew.emotion.infra.metrics;

import com.pheeeew.emotion.application.query.dto.EmotionPageView;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Aspect
@Component
public class EmotionMetricsAspect {

    private final EmotionMetrics metrics;

    @Around("execution(* com.pheeeew.emotion.domain.repository.EmotionRepository.findVisiblePageWithinBounds(..))")
    public Object recordListQuery(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = metrics.startQuery();
        try {
            return joinPoint.proceed();
        } finally {
            metrics.recordListQuery(sample);
        }
    }

    @AfterReturning(
            pointcut = "execution(* com.pheeeew.emotion.application.query.EmotionQueryService.findFirstListPage(..))",
            returning = "result"
    )
    public void recordFirstListResult(EmotionPageView result) {
        metrics.recordListResult("first", result.items().size(), result.hasNext());
    }

    @AfterReturning(
            pointcut = "execution(* com.pheeeew.emotion.application.query.EmotionQueryService.findNextListPage(..))",
            returning = "result"
    )
    public void recordNextListResult(EmotionPageView result) {
        metrics.recordListResult("next", result.items().size(), result.hasNext());
    }
}
