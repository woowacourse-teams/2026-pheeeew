package com.pheeeew.sigh.application;

import com.pheeeew.sigh.application.dto.SighMapResult;
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
public class SighMapMetricsAspect {

    private final SighMapMetrics metrics;

    @Around("execution(* com.pheeeew.sigh.domain.repository.SighRepository.findAllWithinBounds(..))")
    public Object recordQuery(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = metrics.startQuery();
        try {
            return joinPoint.proceed();
        } finally {
            metrics.recordQuery(sample);
        }
    }

    @AfterReturning(
            pointcut = "execution(* com.pheeeew.sigh.application.SighService.findAllWithinBounds(..))",
            returning = "result"
    )
    public void recordResult(SighMapResult result) {
        metrics.recordResult(result.sighs().size(), result.truncated());
    }
}
