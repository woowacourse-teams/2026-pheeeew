package com.pheeeew.pressrace.v2.application;

import com.pheeeew.pressrace.v2.domain.PressDaySnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PressBufferFlusher {

    private static final int CHUNK_SIZE = 500;
    private static final Comparator<PressDaySnapshot> WRITE_ORDER = Comparator.comparingLong(PressDaySnapshot::deviceId)
            .thenComparing(PressDaySnapshot::date);

    private final PressWriteBuffer pressWriteBuffer;
    private final PressBufferWriter pressBufferWriter;
    private final Clock clock;
    private final Counter commits;
    private final Counter devices;
    private final Timer duration;

    public PressBufferFlusher(
            PressWriteBuffer pressWriteBuffer,
            PressBufferWriter pressBufferWriter,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.pressWriteBuffer = pressWriteBuffer;
        this.pressBufferWriter = pressBufferWriter;
        this.clock = clock;
        this.commits = Counter.builder("pheeeew.press.buffer.flush.commits").register(meterRegistry);
        this.devices = Counter.builder("pheeeew.press.buffer.flush.devices").register(meterRegistry);
        this.duration = Timer.builder("pheeeew.press.buffer.flush.duration").register(meterRegistry);
        Gauge.builder("pheeeew.press.buffer.dirty", pressWriteBuffer, PressWriteBuffer::countDirty)
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${pheeeew.press-race.flush-interval-ms:5000}")
    public synchronized void flush() {
        List<PressDaySnapshot> snapshots = pressWriteBuffer.findDirtySnapshots().stream()
                .sorted(WRITE_ORDER)
                .toList();
        for (int start = 0; start < snapshots.size(); start += CHUNK_SIZE) {
            List<PressDaySnapshot> chunk = snapshots.subList(start, Math.min(start + CHUNK_SIZE, snapshots.size()));
            duration.record(() -> pressBufferWriter.write(chunk));
            commits.increment();
            devices.increment(chunk.size());
            pressWriteBuffer.markFlushed(chunk);
        }
        pressWriteBuffer.evictFlushedBefore(LocalDate.now(clock).minusDays(1));
    }

    @PreDestroy
    public void flushBeforeShutdown() {
        flush();
    }
}
