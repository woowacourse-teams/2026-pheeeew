package com.pheeeew.pressrace.v2.application;

import com.pheeeew.pressrace.v2.domain.PressDayRead;
import com.pheeeew.pressrace.exception.PressRaceException;
import com.pheeeew.pressrace.v2.infra.PressRedisStore;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@ConditionalOnProperty(name = "pheeeew.press-race.write-mode", havingValue = "REDIS")
@Component
public class PressRedisDayReader {

    private static final long STORE_UNAVAILABLE_RETRY_SECONDS = 1;
    private static final int MAX_ATTEMPTS = 3;

    private final PressRedisStore pressRedisStore;
    private final PressRedisSeeder pressRedisSeeder;
    private final Clock clock;

    public PressDayRead findToday(UUID devicePublicId) {
        return read(date -> pressRedisStore.readDay(date, devicePublicId));
    }

    public PressDayRead findTotal() {
        return read(pressRedisStore::readTotal);
    }

    private PressDayRead read(Function<LocalDate, PressDayRead> reader) {
        LocalDate date = LocalDate.now(clock);
        try {
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                PressDayRead read = reader.apply(date);
                switch (read.status()) {
                    case DATE_MISMATCH -> date = read.date();
                    case NOT_SEEDED -> pressRedisSeeder.seed(read.date());
                    case READ, UNCHANGED -> {
                        return read;
                    }
                }
            }
        } catch (DataAccessException exception) {
            throw new PressRaceException("STORE_UNAVAILABLE", STORE_UNAVAILABLE_RETRY_SECONDS);
        }
        throw new PressRaceException("STORE_UNAVAILABLE", STORE_UNAVAILABLE_RETRY_SECONDS);
    }
}
