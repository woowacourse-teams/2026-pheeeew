package com.pheeeew.pressrace.v2.application;

import com.pheeeew.pressrace.v2.domain.PressScreenRange;
import com.pheeeew.pressrace.v2.domain.PressScreenRead;
import com.pheeeew.pressrace.exception.PressRaceException;
import com.pheeeew.pressrace.v2.infra.PressRedisStore;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@ConditionalOnProperty(name = "pheeeew.press-race.snapshot-source", havingValue = "RANK", matchIfMissing = true)
@ConditionalOnProperty(name = "pheeeew.press-race.write-mode", havingValue = "REDIS")
@Component
public class PressRedisScreenReader {

    private static final long STORE_UNAVAILABLE_RETRY_SECONDS = 1;
    private static final int MAX_ATTEMPTS = 3;

    private final PressRedisStore pressRedisStore;
    private final PressRedisSeeder pressRedisSeeder;
    private final PressRedisTodayBoard pressRedisTodayBoard;
    private final Clock clock;

    public PressScreenRead read(UUID devicePublicId, PressScreenRange range) {
        LocalDate date = LocalDate.now(clock);
        try {
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                PressScreenRead read = pressRedisStore.readScreen(date, devicePublicId, range);
                switch (read.status()) {
                    case DATE_MISMATCH -> date = read.date();
                    case NOT_SEEDED -> pressRedisSeeder.seed(read.date());
                    case READ, UNCHANGED -> {
                        return withKnownNicknames(read);
                    }
                }
            }
        } catch (DataAccessException exception) {
            throw new PressRaceException("STORE_UNAVAILABLE", STORE_UNAVAILABLE_RETRY_SECONDS);
        }
        throw new PressRaceException("STORE_UNAVAILABLE", STORE_UNAVAILABLE_RETRY_SECONDS);
    }

    private PressScreenRead withKnownNicknames(PressScreenRead read) {
        if (read.missingNicknames().isEmpty()) {
            return read;
        }
        pressRedisTodayBoard.requestNicknames(read.missingNicknames());
        return read.withFallbackNicknames(pressRedisTodayBoard.findKnownNicknames(read.missingNicknames()));
    }
}
