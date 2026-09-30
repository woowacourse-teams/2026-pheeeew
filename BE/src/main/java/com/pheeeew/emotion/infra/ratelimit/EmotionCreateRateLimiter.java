package com.pheeeew.emotion.infra.ratelimit;

import com.pheeeew.emotion.exception.EmotionErrorCode;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.emotion.infra.metrics.EmotionMetrics;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class EmotionCreateRateLimiter {

    public static final Duration WINDOW = Duration.ofSeconds(1);

    private static final Duration EVICTION_THRESHOLD = Duration.ofMinutes(1);

    private final Map<UUID, Instant> lastAcceptedAt = new ConcurrentHashMap<>();
    private final Clock clock;
    private final EmotionMetrics emotionMetrics;

    public void require(UUID devicePublicId) {
        if (tryAcquire(devicePublicId)) {
            return;
        }
        emotionMetrics.recordCreateThrottled();
        throw new EmotionException(EmotionErrorCode.EMOTION_CREATE_RATE_LIMITED, WINDOW);
    }

    public boolean tryAcquire(UUID devicePublicId) {
        Instant now = clock.instant();
        AtomicBoolean accepted = new AtomicBoolean();
        lastAcceptedAt.compute(devicePublicId, (key, last) -> {
            if (last != null && now.isBefore(last.plus(WINDOW))) {
                return last;
            }
            accepted.set(true);
            return now;
        });
        return accepted.get();
    }

    @Scheduled(fixedDelayString = "PT1M")
    public void evictIdleDevices() {
        Instant threshold = clock.instant().minus(EVICTION_THRESHOLD);
        lastAcceptedAt.values().removeIf(threshold::isAfter);
    }

    public int trackedDeviceCount() {
        return lastAcceptedAt.size();
    }
}
