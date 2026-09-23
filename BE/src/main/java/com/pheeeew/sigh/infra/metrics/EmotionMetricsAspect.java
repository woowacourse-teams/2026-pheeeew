package com.pheeeew.sigh.infra.metrics;

import com.pheeeew.sigh.application.dto.SighListResult;
import com.pheeeew.sigh.application.dto.EmotionMapResult;
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

    @Around("execution(* com.pheeeew.sigh.domain.repository.EmotionRepository.findAllWithinBounds(..))")
    public Object recordMapQuery(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = metrics.startQuery();
        try {
            return joinPoint.proceed();
        } finally {
            metrics.recordMapQuery(sample);
        }
    }

    @Around("execution(* com.pheeeew.sigh.domain.repository.EmotionRepository.findListWithinBounds(..))")
    public Object recordListQuery(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = metrics.startQuery();
        try {
            return joinPoint.proceed();
        } finally {
            metrics.recordListQuery(sample);
        }
    }

    @AfterReturning(
            pointcut = "execution(* com.pheeeew.sigh.application.EmotionService.findAllWithinBounds(..))",
            returning = "result"
    )
    public void recordMapResult(EmotionMapResult result) {
        metrics.recordMapResult(result.emotions().size(), result.truncated());
    }

    @AfterReturning(
            pointcut = "execution(* com.pheeeew.sigh.application.EmotionService.findFirstListPage(..))",
            returning = "result"
    )
    public void recordFirstListResult(SighListResult result) {
        metrics.recordListResult("first", result.items().size(), result.hasNext());
    }

    @AfterReturning(
            pointcut = "execution(* com.pheeeew.sigh.application.EmotionService.findNextListPage(..))",
            returning = "result"
    )
    public void recordNextListResult(SighListResult result) {
        metrics.recordListResult("next", result.items().size(), result.hasNext());
    }
}
