package com.pheeeew.emotion.infra.metrics;

import com.pheeeew.emotion.application.dto.EmotionPageView;
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

    @Around("execution(* com.pheeeew.emotion.domain.repository.EmotionRepository.findVisiblePageWithinBounds(..))"
            + " || execution(* com.pheeeew.emotion.domain.repository.EmotionRepository.findVisiblePageWithoutBounds(..))")
    public Object recordListQuery(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = metrics.startQuery();
        try {
            return joinPoint.proceed();
        } finally {
            metrics.recordListQuery(sample);
        }
    }

    @AfterReturning(
            pointcut = "(execution(* com.pheeeew.emotion.application.query.EmotionQueryService.findListWithinBounds(..))"
                    + " || execution(* com.pheeeew.emotion.application.query.EmotionQueryService.findListWithoutBounds(..)))"
                    + " && args(.., encodedCursor)",
            returning = "result",
            argNames = "encodedCursor,result"
    )
    public void recordListResult(String encodedCursor, EmotionPageView result) {
        String page = encodedCursor == null ? "first" : "next";
        metrics.recordListResult(page, result.items().size(), result.hasNext());
    }
}
