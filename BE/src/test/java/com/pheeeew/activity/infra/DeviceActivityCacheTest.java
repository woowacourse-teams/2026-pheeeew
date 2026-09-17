package com.pheeeew.activity.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DeviceActivityCacheTest {

    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final Instant MIDNIGHT = Instant.parse("2026-09-15T15:00:00Z");
    private final AtomicLong nanos = new AtomicLong();
    private final DeviceActivityCache cache = new DeviceActivityCache(2,
            Caffeine.newBuilder().ticker(nanos::get).executor(Runnable::run));

    @Test
    void 같은_기기의_같은_KST_활동_날짜만_중복으로_취급한다() {
        // given
        cache.acquire(DEVICE_ID, MIDNIGHT.minusSeconds(1), MIDNIGHT);

        // when / then
        assertThat(cache.acquire(DEVICE_ID, MIDNIGHT.minusSeconds(60), MIDNIGHT)).isNull();
        assertThat(cache.acquire(DEVICE_ID, MIDNIGHT, MIDNIGHT)).isNotNull();
        assertThat(cache.acquire(UUID.randomUUID(), MIDNIGHT, MIDNIGHT)).isNotNull();
    }

    @Test
    void KST_자정에_만료되고_이전_실패는_새_표식을_지우지_않는다() {
        // given
        Instant occurredAt = MIDNIGHT.minusSeconds(1);
        DeviceActivityCache.ActivityMarker old = cache.acquire(DEVICE_ID, occurredAt, occurredAt);

        // when
        nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(500));
        assertThat(cache.acquire(DEVICE_ID, occurredAt, occurredAt.plusMillis(500))).isNull();
        nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(500));
        DeviceActivityCache.ActivityMarker current = cache.acquire(DEVICE_ID, occurredAt, MIDNIGHT);
        cache.release(old);

        // then
        assertThat(current).isNotNull().isNotSameAs(old);
        assertThat(cache.acquire(DEVICE_ID, occurredAt, MIDNIGHT)).isNull();
        cache.release(current);
        assertThat(cache.acquire(DEVICE_ID, occurredAt, MIDNIGHT)).isNotNull();
    }

    @Test
    void 용량을_넘어_제거된_활동은_다시_기록을_시도할_수_있다() {
        // given
        List<UUID> devices = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            UUID device = UUID.randomUUID();
            devices.add(device);
            cache.acquire(device, MIDNIGHT, MIDNIGHT);
        }

        // when
        long retryable = devices.stream()
                .filter(device -> cache.acquire(device, MIDNIGHT, MIDNIGHT) != null)
                .count();

        // then
        assertThat(retryable).isGreaterThanOrEqualTo(98);
    }

    @Test
    void 같은_기기와_날짜의_동시_요청은_하나만_표식을_얻는다() throws Exception {
        // given
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService callers = Executors.newFixedThreadPool(8)) {
            List<Future<DeviceActivityCache.ActivityMarker>> results = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                results.add(callers.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시 요청 시작 대기 시간 초과");
                    }
                    return cache.acquire(DEVICE_ID, MIDNIGHT, MIDNIGHT);
                }));
            }

            // when
            boolean allReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertThat(allReady).isTrue();
            List<DeviceActivityCache.ActivityMarker> acquired = new ArrayList<>();
            for (Future<DeviceActivityCache.ActivityMarker> result : results) {
                acquired.add(result.get(5, TimeUnit.SECONDS));
            }

            // then
            assertThat(acquired.stream().filter(marker -> marker != null)).hasSize(1);
        }
    }
}
