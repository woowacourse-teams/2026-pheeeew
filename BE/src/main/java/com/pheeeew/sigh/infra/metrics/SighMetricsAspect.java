package com.pheeeew.sigh.infra.metrics;

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
public class SighMetricsAspect {

    private final SighMetrics metrics;

    @Around("execution(* com.pheeeew.sigh.domain.repository.SighRepository.findAllWithinBounds(..))")
    public Object recordMapQuery(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = metrics.startQuery();
        try {
            return joinPoint.proceed();
        } finally {
            metrics.recordMapQuery(sample);
        }
    }

    @Around("execution(* com.pheeeew.sigh.domain.repository.SighRepository.findListWithinBounds(..))")
    public Object recordListQuery(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = metrics.startQuery();
        try {
            return joinPoint.proceed();
        } finally {
            metrics.recordListQuery(sample);
        }
    }

    @AfterReturning(
            pointcut = "execution(* com.pheeeew.sigh.application.SighService.findAllWithinBounds(..))",
            returning = "result"
    )
    public void recordMapResult(SighMapResult result) {
        metrics.recordMapResult(result.sighs().size(), result.truncated());
    }
}
