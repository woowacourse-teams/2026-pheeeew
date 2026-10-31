package com.pheeeew.emotion.infra.metrics;

import com.pheeeew.emotion.infra.metrics.MapReadMetrics.Stage;
import java.util.function.LongSupplier;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(0)
public class MapReadTimingAspect {

    private final MapReadMetrics metrics;
    private final LongSupplier nanoTime;

    @Autowired
    public MapReadTimingAspect(MapReadMetrics metrics) {
        this(metrics, System::nanoTime);
    }

    MapReadTimingAspect(MapReadMetrics metrics, LongSupplier nanoTime) {
        this.metrics = metrics;
        this.nanoTime = nanoTime;
    }

    @Around("""
            execution(public * com.pheeeew.emotion.presentation.EmotionController.findContentMap(..)) ||
            execution(public * com.pheeeew.emotion.presentation.EmotionController.findRegionMap(..)) ||
            execution(public * com.pheeeew.emotion.presentation.EmotionV4Controller.findContentMapWithinBoundsV4(..)) ||
            execution(public * com.pheeeew.emotion.presentation.EmotionV4Controller.findRegionMap(..)) ||
            execution(public * com.pheeeew.emotion.application.query.EmotionMapQueryService.findContentMapWithinBounds(..)) ||
            execution(public * com.pheeeew.emotion.application.query.EmotionMapQueryService.findContentMapWithinBoundsV4(..)) ||
            execution(public * com.pheeeew.emotion.application.query.EmotionMapQueryService.findRegionMap(..)) ||
            execution(public * com.pheeeew.emotion.application.query.EmotionMapDbQueryService.findValidatedDeviceId(..)) ||
            execution(public * com.pheeeew.emotion.application.query.EmotionMapDbQueryService.findRegionRequest(..)) ||
            execution(public * com.pheeeew.emotion.application.query.EmotionMapDbQueryService.findMap(..)) ||
            execution(public * com.pheeeew.emotion.application.query.EmotionMapDbQueryService.findRegionMapSql(..)) ||
            execution(public * com.pheeeew.emotion.application.query.EmotionMapDbQueryService.findSummariesByRegionCodes(..)) ||
            execution(public * com.pheeeew.emotion.application.mapcache.PinCandidateCache.find(..)) ||
            execution(public * com.pheeeew.emotion.application.mapcache.RegionSummaryCache.summaries(..)) ||
            execution(public * com.pheeeew.emotion.domain.repository.EmotionMapReadRepository.findBlockedTargets(..)) ||
            execution(public * com.pheeeew.region.domain.repository.RegionRepository.findByLevel(..)) ||
            execution(public * com.pheeeew.emotion.application.query.EmotionMapDbQueryService.findPinPage(..))
            """)
    public Object record(ProceedingJoinPoint joinPoint) throws Throwable {
        Stage stage = stage(joinPoint);
        boolean controller = stage == Stage.MAP_PINS_CONTROLLER || stage == Stage.MAP_REGIONS_CONTROLLER;
        if (controller) {
            MapReadObservation.controllerStarted();
        }
        long started = nanoTime.getAsLong();
        boolean succeeded = false;
        try {
            Object result = joinPoint.proceed();
            succeeded = true;
            return result;
        } finally {
            if (controller) {
                MapReadObservation.controllerEnded();
            }
            // 기본 트랜잭션 advice 바깥의 포함 시간이다. 중첩된 단계와 더하지 않는다.
            long duration = nanoTime.getAsLong() - started;
            try {
                metrics.record(stage, duration, succeeded);
            } catch (RuntimeException ignored) {
                // 계측 장애가 조회 결과나 원래 예외를 바꾸지 않는다.
            }
            try {
                MapReadObservation.recordTiming(stage, duration);
            } catch (RuntimeException ignored) {
                // 요청별 로그 기록과 집계 지표의 실패를 서로 격리한다.
            }
        }
    }

    private Stage stage(ProceedingJoinPoint joinPoint) {
        String method = joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();
        return switch (method) {
            case "com.pheeeew.emotion.presentation.EmotionController.findContentMap",
                 "com.pheeeew.emotion.presentation.EmotionV4Controller.findContentMapWithinBoundsV4" -> Stage.MAP_PINS_CONTROLLER;
            case "com.pheeeew.emotion.presentation.EmotionController.findRegionMap",
                 "com.pheeeew.emotion.presentation.EmotionV4Controller.findRegionMap" -> Stage.MAP_REGIONS_CONTROLLER;
            case "com.pheeeew.emotion.application.query.EmotionMapQueryService.findContentMapWithinBounds",
                 "com.pheeeew.emotion.application.query.EmotionMapQueryService.findContentMapWithinBoundsV4" -> Stage.MAP_PINS_SERVICE;
            case "com.pheeeew.emotion.application.query.EmotionMapQueryService.findRegionMap" -> Stage.MAP_REGIONS_SERVICE;
            case "com.pheeeew.emotion.application.query.EmotionMapDbQueryService.findValidatedDeviceId" -> Stage.MAP_PINS_DEVICE;
            case "com.pheeeew.emotion.application.query.EmotionMapDbQueryService.findRegionRequest" -> Stage.MAP_REGIONS_PREPARE;
            case "com.pheeeew.emotion.application.query.EmotionMapDbQueryService.findMap" -> Stage.MAP_PINS_DB_PATH;
            case "com.pheeeew.emotion.application.query.EmotionMapDbQueryService.findRegionMapSql",
                 "com.pheeeew.emotion.application.query.EmotionMapDbQueryService.findSummariesByRegionCodes" -> Stage.MAP_REGIONS_DB_PATH;
            case "com.pheeeew.emotion.application.mapcache.PinCandidateCache.find" -> Stage.MAP_PINS_CACHE;
            case "com.pheeeew.emotion.application.mapcache.RegionSummaryCache.summaries" -> Stage.MAP_REGIONS_CACHE;
            case "com.pheeeew.emotion.domain.repository.EmotionMapReadRepository.findBlockedTargets" -> Stage.MAP_BLOCKS_LOOKUP;
            case "com.pheeeew.region.domain.repository.RegionRepository.findByLevel" -> Stage.MAP_REGION_LIST;
            case "com.pheeeew.emotion.application.query.EmotionMapDbQueryService.findPinPage" -> Stage.MAP_PINS_RESPONSE;
            default -> throw new IllegalStateException("계측 대상이 아닌 지도 조회 메서드입니다.");
        };
    }
}
