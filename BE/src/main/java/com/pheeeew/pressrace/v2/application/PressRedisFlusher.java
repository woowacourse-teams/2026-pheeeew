package com.pheeeew.pressrace.v2.application;

import com.pheeeew.pressrace.v2.domain.PressDirtyDay;
import com.pheeeew.pressrace.v2.domain.PressStoredDay;
import com.pheeeew.pressrace.v2.infra.PressRedisStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(name = "pheeeew.press-race.write-mode", havingValue = "REDIS")
@Component
public class PressRedisFlusher {

    private static final int CHUNK_SIZE = 500;
    private static final Duration LEASE = Duration.ofSeconds(30);

    private final PressRedisStore pressRedisStore;
    private final PressRedisFlushWriter pressRedisFlushWriter;
    private final Counter commits;
    private final Counter devices;
    private final Counter rejected;
    private final Timer duration;

    public PressRedisFlusher(
            PressRedisStore pressRedisStore,
            PressRedisFlushWriter pressRedisFlushWriter,
            MeterRegistry meterRegistry
    ) {
        this.pressRedisStore = pressRedisStore;
        this.pressRedisFlushWriter = pressRedisFlushWriter;
        this.commits = Counter.builder("pheeeew.press.redis.flush.commits").register(meterRegistry);
        this.devices = Counter.builder("pheeeew.press.redis.flush.devices").register(meterRegistry);
        this.rejected = Counter.builder("pheeeew.press.redis.flush.rejected").register(meterRegistry);
        this.duration = Timer.builder("pheeeew.press.redis.flush.duration").register(meterRegistry);
        Gauge.builder("pheeeew.press.redis.dirty", this, PressRedisFlusher::countDirty).register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${pheeeew.press-race.flush-interval-ms:5000}")
    public synchronized void flush() {
        String token = UUID.randomUUID().toString();
        if (!pressRedisStore.acquireFlushLease(token, LEASE)) {
            return;
        }
        try {
            List<PressDirtyDay> dirtyDays = pressRedisStore.findDirty();
            for (int start = 0; start < dirtyDays.size(); start += CHUNK_SIZE) {
                flushChunk(dirtyDays.subList(start, Math.min(start + CHUNK_SIZE, dirtyDays.size())));
            }
        } finally {
            pressRedisStore.releaseFlushLease(token);
        }
    }

    @PreDestroy
    public void flushBeforeShutdown() {
        flush();
    }

    private void flushChunk(List<PressDirtyDay> dirtyDays) {
        List<PressStoredDay> days = pressRedisStore.findDays(dirtyDays);
        int accepted = duration.record(() -> pressRedisFlushWriter.write(days));
        commits.increment();
        devices.increment(accepted);
        rejected.increment(days.size() - accepted);
        Map<String, Long> versions = new LinkedHashMap<>();
        dirtyDays.forEach(dirtyDay -> versions.put(dirtyDay.member(), dirtyDay.version()));
        days.forEach(day -> versions.put(day.dirtyMember(), day.version()));
        pressRedisStore.clearDirty(versions);
    }

    private double countDirty() {
        try {
            return pressRedisStore.countDirty();
        } catch (DataAccessException exception) {
            return Double.NaN;
        }
    }
}
