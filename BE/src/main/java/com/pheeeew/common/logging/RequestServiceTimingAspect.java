package com.pheeeew.common.logging;

import com.pheeeew.common.logging.RequestTiming.Stage;
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
public class RequestServiceTimingAspect {

    private final LongSupplier nanoTime;

    @Autowired
    public RequestServiceTimingAspect() {
        this(System::nanoTime);
    }

    RequestServiceTimingAspect(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    @Around("execution(public * com.pheeeew.emotion.application.query.EmotionQueryService.findRegionMap(..)) || "
            + "execution(public * com.pheeeew.emotion.application.query.EmotionQueryService.findContentRegionMap(..)) || "
            + "execution(public * com.pheeeew.emotion.application.command.EmotionPressService.press(..)) || "
            + "execution(public * com.pheeeew.emotion.application.query.EmotionPressQueryService.findMyDailyPresses(..)) || "
            + "execution(public * com.pheeeew.emotion.application.query.EmotionPressQueryService.findDailyTotal(..))")
    public Object recordService(ProceedingJoinPoint joinPoint) throws Throwable {
        Stage stage = switch (joinPoint.getSignature().getName()) {
            case "findRegionMap", "findContentRegionMap" -> Stage.REGIONS_SERVICE;
            case "press" -> Stage.PRESS_SERVICE;
            case "findMyDailyPresses" -> Stage.PRESS_MY_SERVICE;
            case "findDailyTotal" -> Stage.PRESS_TOTAL_SERVICE;
            default -> throw new IllegalStateException("계측 대상이 아닌 서비스 메서드입니다.");
        };
        long startedAt = nanoTime.getAsLong();
        try {
            return joinPoint.proceed();
        } finally {
            try {
                // 기본 트랜잭션 advice 바깥의 호출 시간이다. 외부 트랜잭션에 참여하면 그 시작·종료는 포함하지 않는다.
                RequestTiming.record(stage, nanoTime.getAsLong() - startedAt);
            } catch (RuntimeException ignored) {
                // 계측 기록 실패가 서비스 결과나 원래 예외를 바꾸지 않게 한다.
            }
        }
    }
}
