package com.pheeeew.climb.stairs;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class StairsService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<String> CHARACTERS = Set.of("defeated", "tired", "frustrated", "annoyed", "angry");
    private final StairsRepository repository;
    private final Clock clock;

    public StairsService(StairsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Today today() {
        LocalDate day = LocalDate.now(clock.withZone(SEOUL));
        return new Today(day, StairsRules.seed(day));
    }

    public Result submit(UUID guest, LocalDate day, String character, List<StairsRules.Input> inputs) {
        if (guest == null || character == null || !CHARACTERS.contains(character)) {
            throw new IllegalArgumentException("INVALID_REQUEST");
        }
        requireDay(day);
        StairsRules.Score score = StairsRules.replay(StairsRules.seed(day), inputs);
        repository.save(guest, day, character, score);
        StairsRepository.Item best = repository.ranking(day, guest).me();
        return new Result(score.height(), score.timeMs(), new StairsRules.Score(best.height(), best.timeMs()), best.rank());
    }

    public StairsRepository.Board ranking(LocalDate day, UUID guest) {
        if (day == null) throw new IllegalArgumentException("INVALID_DAY");
        return repository.ranking(day, guest);
    }

    public void requireDay(LocalDate day) {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(SEOUL));
        LocalDate today = now.toLocalDate();
        boolean grace = today.minusDays(1).equals(day) && now.toLocalTime().toSecondOfDay() < 300;
        if (!today.equals(day) && !grace) throw new IllegalArgumentException("INVALID_DAY");
    }

    public record Today(LocalDate day, int seed) { }
    public record Result(int height, int timeMs, StairsRules.Score best, int rank) { }
}
