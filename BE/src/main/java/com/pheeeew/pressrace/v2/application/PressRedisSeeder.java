package com.pheeeew.pressrace.v2.application;

import com.pheeeew.pressrace.v2.infra.PressBufferRepository;
import com.pheeeew.pressrace.v2.infra.PressRedisStore;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pheeeew.press-race.write-mode", havingValue = "REDIS")
@Component
public class PressRedisSeeder {

    private final PressBufferRepository pressBufferRepository;
    private final PressRedisStore pressRedisStore;
    private final Clock clock;

    @PostConstruct
    public void seedToday() {
        seed(LocalDate.now(clock));
    }

    public synchronized void seed(LocalDate date) {
        if (pressRedisStore.isSeeded(date)) {
            return;
        }
        long seeded = pressBufferRepository.findStoredDays(date).stream()
                .filter(pressRedisStore::seedDay)
                .count();
        pressRedisStore.markSeeded(date);
        log.info("press redis seed date={} seeded={}", date, seeded);
    }
}
