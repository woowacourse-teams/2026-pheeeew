package com.pheeeew.emotion.infra.metrics;

import com.pheeeew.common.logging.RequestTiming;
import com.pheeeew.common.logging.RequestTiming.Stage;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class EmotionRegionQueryTimingAspect {

    // 전체 집계 지표는 Spring Data Repository Timer가 담당하고, 여기서는 요청별 시간만 기록한다.
    @Around("execution(* com.pheeeew.emotion.domain.repository.EmotionRepository.findSummariesByRegionCodes(..))")
    public Object recordSummary(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            try {
                // JDBC 통신·결과 매핑 등을 포함한 Repository 호출 시간이며 순수 SQL 실행 시간은 아니다.
                RequestTiming.record(Stage.REGIONS_SUMMARY, System.nanoTime() - startedAt);
            } catch (RuntimeException ignored) {
                // 요청별 기록 실패가 조회 결과나 원래 예외를 바꾸지 않게 한다.
            }
        }
    }
}
