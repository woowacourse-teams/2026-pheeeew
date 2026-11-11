package com.pheeeew.pressrace.v2.application;

import com.github.benmanes.caffeine.cache.Ticker;
import com.pheeeew.common.logging.ScheduledTaskErrorHandler;
import com.pheeeew.pressrace.v2.domain.PressReadStatus;
import com.pheeeew.pressrace.v2.domain.PressTodayObservation;
import com.pheeeew.pressrace.v2.domain.PressTodayRead;
import com.pheeeew.pressrace.exception.PressRaceException;
import com.pheeeew.pressrace.v2.infra.PressBufferRepository;
import com.pheeeew.pressrace.v2.infra.PressRedisStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.support.DelegatingErrorHandlingRunnable;
import org.springframework.stereotype.Component;

@Slf4j
@ConditionalOnProperty(name = "pheeeew.press-race.write-mode", havingValue = "REDIS")
@Component
public class PressRedisTodayBoard {

    private static final long REFRESH_INTERVAL_MILLIS = 250;
    private static final Duration STALE_AFTER = Duration.ofSeconds(3);
    private static final long STORE_UNAVAILABLE_RETRY_SECONDS = 1;
    private static final int MAX_ATTEMPTS = 3;
    private static final String NO_TOKEN = "";
    private static final long NO_VERSION = -1;

    private final PressRedisStore pressRedisStore;
    private final PressRedisSeeder pressRedisSeeder;
    private final PressBufferRepository pressBufferRepository;
    private final Clock clock;
    private final Ticker ticker;
    private final Executor nicknameFiller;
    private final Timer readDuration;
    private final Counter fullReads;
    private final Counter unchangedReads;
    private final Counter failures;
    private final Counter nicknameFillFailures;
    private final ScheduledExecutorService updates = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("press-redis-today").daemon().factory());
    private final AtomicBoolean nicknameFilling = new AtomicBoolean();
    private volatile State state = State.of(null, 0);
    private boolean failing;

    @Autowired
    public PressRedisTodayBoard(
            PressRedisStore pressRedisStore,
            PressRedisSeeder pressRedisSeeder,
            PressBufferRepository pressBufferRepository,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this(pressRedisStore, pressRedisSeeder, pressBufferRepository, clock, meterRegistry, Ticker.systemTicker(),
                Executors.newSingleThreadExecutor(Thread.ofPlatform().name("press-redis-nickname").daemon().factory()));
    }

    PressRedisTodayBoard(
            PressRedisStore pressRedisStore,
            PressRedisSeeder pressRedisSeeder,
            PressBufferRepository pressBufferRepository,
            Clock clock,
            MeterRegistry meterRegistry,
            Ticker ticker,
            Executor nicknameFiller
    ) {
        this.pressRedisStore = pressRedisStore;
        this.pressRedisSeeder = pressRedisSeeder;
        this.pressBufferRepository = pressBufferRepository;
        this.clock = clock;
        this.ticker = ticker;
        this.nicknameFiller = nicknameFiller;
        this.readDuration = Timer.builder("pheeeew.press.redis.read.duration").register(meterRegistry);
        this.fullReads = Counter.builder("pheeeew.press.redis.read.full").register(meterRegistry);
        this.unchangedReads = Counter.builder("pheeeew.press.redis.read.unchanged").register(meterRegistry);
        this.failures = Counter.builder("pheeeew.press.redis.read.failures").register(meterRegistry);
        this.nicknameFillFailures = Counter.builder("pheeeew.press.redis.nickname.fill.failures").register(meterRegistry);
    }

    @PostConstruct
    void start() {
        succeed(observe(null));
        updates.scheduleWithFixedDelay(new DelegatingErrorHandlingRunnable(this::refresh,
                new ScheduledTaskErrorHandler()), REFRESH_INTERVAL_MILLIS, REFRESH_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        updates.shutdownNow();
        if (nicknameFiller instanceof ExecutorService executor) {
            executor.shutdownNow();
        }
    }

    public PressTodayObservation current() {
        State current = state;
        if (current.observation() == null || ticker.read() - current.succeededAt() >= STALE_AFTER.toNanos()) {
            throw new PressRaceException("STORE_UNAVAILABLE", STORE_UNAVAILABLE_RETRY_SECONDS);
        }
        return current.observation();
    }

    public void requestNicknames(Set<UUID> devicePublicIds) {
        if (!nicknameFilling.compareAndSet(false, true)) {
            return;
        }
        try {
            nicknameFiller.execute(() -> fillNicknames(devicePublicIds));
        } catch (RejectedExecutionException exception) {
            nicknameFilling.set(false);
        }
    }

    public Map<UUID, String> findKnownNicknames(Set<UUID> devicePublicIds) {
        PressTodayObservation observation = state.observation();
        if (observation == null) {
            return Map.of();
        }
        Map<UUID, String> nicknames = new HashMap<>();
        observation.participants().stream()
                .filter(participant -> devicePublicIds.contains(participant.devicePublicId()))
                .filter(participant -> participant.nickname() != null)
                .forEach(participant -> nicknames.put(participant.devicePublicId(), participant.nickname()));
        return Map.copyOf(nicknames);
    }

    void refresh() {
        try {
            succeed(observe(state.observation()));
        } catch (DataAccessException | PressRaceException exception) {
            failures.increment();
            if (!failing) {
                failing = true;
                log.warn("press redis board refresh failing", exception);
            }
            return;
        }
        if (failing) {
            failing = false;
            log.info("press redis board refresh recovered");
        }
    }

    private void succeed(PressTodayObservation observation) {
        state = State.of(observation, ticker.read());
    }

    private PressTodayObservation observe(PressTodayObservation previous) {
        LocalDate date = previous == null ? LocalDate.now(clock) : previous.date();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            PressTodayRead read = read(date, previous);
            switch (read.status()) {
                case DATE_MISMATCH -> date = requireNotEarlier(previous, read.date());
                case NOT_SEEDED -> pressRedisSeeder.seed(read.date());
                case READ, UNCHANGED -> {
                    return PressTodayObservation.advance(previous, withKnownNicknames(read, previous));
                }
            }
        }
        throw new PressRaceException("STORE_UNAVAILABLE", STORE_UNAVAILABLE_RETRY_SECONDS);
    }

    private PressTodayRead read(LocalDate date, PressTodayObservation previous) {
        boolean sameDate = previous != null && previous.date().equals(date) && previous.token() != null;
        PressTodayRead read = readDuration.record(() -> sameDate
                ? pressRedisStore.readToday(date, previous.token(), previous.version(), previous.nicknameVersion())
                : pressRedisStore.readToday(date, NO_TOKEN, NO_VERSION, NO_VERSION));
        if (read.status() == PressReadStatus.READ) {
            fullReads.increment();
        }
        if (read.status() == PressReadStatus.UNCHANGED) {
            unchangedReads.increment();
        }
        return read;
    }

    private LocalDate requireNotEarlier(PressTodayObservation previous, LocalDate storeDate) {
        if (previous != null && storeDate.isBefore(previous.date())) {
            throw new PressRaceException("STORE_UNAVAILABLE", STORE_UNAVAILABLE_RETRY_SECONDS);
        }
        return storeDate;
    }

    private PressTodayRead withKnownNicknames(PressTodayRead read, PressTodayObservation previous) {
        if (read.missingNicknames().isEmpty()) {
            return read;
        }
        requestNicknames(read.missingNicknames());
        return read.withFallbackNicknames(previous == null ? Map.of() : previous.nicknames());
    }

    private void fillNicknames(Set<UUID> devicePublicIds) {
        try {
            Map<UUID, String> found = pressBufferRepository.findNicknames(devicePublicIds);
            Map<UUID, String> nicknames = new HashMap<>();
            devicePublicIds.forEach(devicePublicId -> nicknames.put(devicePublicId, found.get(devicePublicId)));
            pressRedisStore.fillNicknames(nicknames);
        } catch (DataAccessException exception) {
            nicknameFillFailures.increment();
        } finally {
            nicknameFilling.set(false);
        }
    }

    private record State(PressTodayObservation observation, long succeededAt) {
        private static State of(PressTodayObservation observation, long succeededAt) {
            return new State(observation, succeededAt);
        }
    }
}
