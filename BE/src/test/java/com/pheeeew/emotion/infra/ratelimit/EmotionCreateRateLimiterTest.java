package com.pheeeew.emotion.infra.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.emotion.exception.EmotionErrorCode;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.emotion.infra.metrics.EmotionMetrics;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EmotionCreateRateLimiterTest {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    private final EmotionMetrics emotionMetrics = Mockito.mock(EmotionMetrics.class);

    @Test
    void 기기의_첫_요청은_통과한다() {
        // given
        MutableClock clock = new MutableClock(BASE);
        EmotionCreateRateLimiter limiter = new EmotionCreateRateLimiter(clock, emotionMetrics);

        // when
        boolean accepted = limiter.tryAcquire(UUID.randomUUID());

        // then
        assertThat(accepted).isTrue();
    }

    @Test
    void 같은_기기가_1초_안에_다시_요청하면_거부한다() {
        // given
        MutableClock clock = new MutableClock(BASE);
        EmotionCreateRateLimiter limiter = new EmotionCreateRateLimiter(clock, emotionMetrics);
        UUID device = UUID.randomUUID();
        limiter.tryAcquire(device);

        // when
        clock.advance(Duration.ofMillis(999));
        boolean accepted = limiter.tryAcquire(device);

        // then
        assertThat(accepted).isFalse();
    }

    @Test
    void 같은_기기라도_1초가_지나면_다시_통과한다() {
        // given
        MutableClock clock = new MutableClock(BASE);
        EmotionCreateRateLimiter limiter = new EmotionCreateRateLimiter(clock, emotionMetrics);
        UUID device = UUID.randomUUID();
        limiter.tryAcquire(device);

        // when
        clock.advance(Duration.ofSeconds(1));
        boolean accepted = limiter.tryAcquire(device);

        // then
        assertThat(accepted).isTrue();
    }

    @Test
    void 거부된_요청은_다음_통과_시각을_미루지_않는다() {
        // given
        MutableClock clock = new MutableClock(BASE);
        EmotionCreateRateLimiter limiter = new EmotionCreateRateLimiter(clock, emotionMetrics);
        UUID device = UUID.randomUUID();
        limiter.tryAcquire(device);

        // when
        clock.advance(Duration.ofMillis(900));
        limiter.tryAcquire(device);
        clock.advance(Duration.ofMillis(100));
        boolean accepted = limiter.tryAcquire(device);

        // then
        assertThat(accepted).isTrue();
    }

    @Test
    void 기기가_다르면_서로_제한하지_않는다() {
        // given
        MutableClock clock = new MutableClock(BASE);
        EmotionCreateRateLimiter limiter = new EmotionCreateRateLimiter(clock, emotionMetrics);
        limiter.tryAcquire(UUID.randomUUID());

        // when
        boolean accepted = limiter.tryAcquire(UUID.randomUUID());

        // then
        assertThat(accepted).isTrue();
    }

    @Test
    void 같은_기기가_동시에_요청해도_하나만_통과한다() throws Exception {
        // given
        MutableClock clock = new MutableClock(BASE);
        EmotionCreateRateLimiter limiter = new EmotionCreateRateLimiter(clock, emotionMetrics);
        UUID device = UUID.randomUUID();
        int threads = 32;
        CyclicBarrier barrier = new CyclicBarrier(threads);

        // when
        List<Boolean> results;
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Callable<Boolean>> tasks = IntStream.range(0, threads)
                    .<Callable<Boolean>>mapToObj(index -> () -> {
                        barrier.await();
                        return limiter.tryAcquire(device);
                    })
                    .toList();
            List<Future<Boolean>> futures = executor.invokeAll(tasks);
            results = futures.stream().map(EmotionCreateRateLimiterTest::get).toList();
        }

        // then
        assertThat(results).filteredOn(accepted -> accepted).hasSize(1);
    }

    @Test
    void 오래된_기기_기록만_정리한다() {
        // given
        MutableClock clock = new MutableClock(BASE);
        EmotionCreateRateLimiter limiter = new EmotionCreateRateLimiter(clock, emotionMetrics);
        limiter.tryAcquire(UUID.randomUUID());
        clock.advance(Duration.ofMinutes(2));
        limiter.tryAcquire(UUID.randomUUID());

        // when
        limiter.evictIdleDevices();

        // then
        assertThat(limiter.trackedDeviceCount()).isEqualTo(1);
    }

    @Test
    void 제한을_넘기면_재시도_시간을_담은_예외를_던지고_지표에_기록한다() {
        // given
        MutableClock clock = new MutableClock(BASE);
        EmotionCreateRateLimiter limiter = new EmotionCreateRateLimiter(clock, emotionMetrics);
        UUID device = UUID.randomUUID();
        limiter.require(device);

        // when
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> limiter.require(device));

        // then
        assertThat(thrown).isInstanceOf(EmotionException.class);
        EmotionException exception = (EmotionException) thrown;
        assertThat(exception.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_CREATE_RATE_LIMITED);
        assertThat(exception.getRetryAfter()).isEqualTo(EmotionCreateRateLimiter.WINDOW);
        Mockito.verify(emotionMetrics).recordCreateThrottled();
    }

    private static Boolean get(Future<Boolean> future) {
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration amount) {
            instant = instant.plus(amount);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
