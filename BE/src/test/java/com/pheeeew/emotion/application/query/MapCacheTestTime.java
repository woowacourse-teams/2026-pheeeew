package com.pheeeew.emotion.application.query;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
class MapCacheTestTime {

    @Bean
    @Primary
    MutableClock mapCacheClock() {
        return new MutableClock(Instant.now().plusSeconds(60).truncatedTo(ChronoUnit.MICROS));
    }

    @Bean
    @Primary
    ManualScheduledExecutor mapCacheExecutor(MutableClock clock) {
        return new ManualScheduledExecutor(clock);
    }

    static final class MutableClock extends Clock {

        private final AtomicReference<Instant> now;

        MutableClock(Instant now) {
            this.now = new AtomicReference<>(now);
        }

        void advance(Duration duration) {
            now.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    static final class ManualScheduledExecutor extends AbstractExecutorService implements ScheduledExecutorService {

        private final MutableClock clock;
        private final PriorityQueue<Task<?>> pending = new PriorityQueue<>();
        private final List<Runnable> submitted = new ArrayList<>();
        private volatile boolean shutdown;
        private volatile boolean reject;

        ManualScheduledExecutor(MutableClock clock) {
            this.clock = clock;
        }

        void runDueTasks() {
            while (true) {
                Task<?> task;
                synchronized (this) {
                    if (pending.isEmpty() || pending.peek().deadline.isAfter(clock.instant())) {
                        return;
                    }
                    task = pending.remove();
                }
                task.run();
            }
        }

        void forcePreviouslySubmittedTasks() {
            List<Runnable> captured;
            synchronized (this) {
                captured = List.copyOf(submitted);
            }
            captured.forEach(Runnable::run);
        }

        void rejectNewTasks() {
            reject = true;
        }

        @Override
        public synchronized ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            submitted.add(command);
            return schedule(() -> { command.run(); return null; }, delay, unit);
        }

        @Override
        public synchronized <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            if (shutdown || reject) {
                throw new RejectedExecutionException("검증용 실행기가 작업을 거부했습니다.");
            }
            Task<V> task = new Task<>(callable, clock.instant().plusNanos(unit.toNanos(delay)), clock);
            pending.add(task);
            return task;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public synchronized List<Runnable> shutdownNow() {
            shutdown = true;
            pending.forEach(task -> task.cancel(false));
            pending.clear();
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public synchronized boolean isTerminated() {
            return shutdown && pending.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }

        @Override
        public void execute(Runnable command) {
            schedule(command, 0, TimeUnit.NANOSECONDS);
        }
    }

    private static final class Task<V> extends FutureTask<V> implements RunnableScheduledFuture<V> {

        private final Instant deadline;
        private final Clock clock;

        Task(Callable<V> callable, Instant deadline, Clock clock) {
            super(callable);
            this.deadline = deadline;
            this.clock = clock;
        }

        @Override
        public boolean isPeriodic() {
            return false;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(Duration.between(clock.instant(), deadline).toNanos(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }
    }
}
