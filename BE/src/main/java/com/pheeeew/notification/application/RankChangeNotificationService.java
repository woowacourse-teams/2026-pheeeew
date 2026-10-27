package com.pheeeew.notification.application;

import com.pheeeew.common.application.Ranking;
import com.pheeeew.notification.domain.DevicePressCount;
import com.pheeeew.notification.domain.repository.DailyPressCountRepository;
import com.pheeeew.notification.domain.repository.NotificationOutboxRepository;
import com.pheeeew.notification.domain.repository.NotificationRankRoundRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class RankChangeNotificationService {

    private static final int NOTIFIED_RANK_LIMIT = 5;
    private static final Duration ROUND_INTERVAL = Duration.ofMinutes(5);
    private static final Duration EARLY_START_TOLERANCE = Duration.ofSeconds(30);
    private static final int RETENTION_DAYS = 7;

    private final DailyPressCountRepository dailyPressCountRepository;
    private final NotificationRankRoundRepository notificationRankRoundRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final PushSender pushSender;
    private final Clock clock;

    @Transactional
    public void compareRanks() {
        if (!pushSender.isAvailable()) {
            return;
        }
        Instant now = clock.instant();
        Instant roundAt = roundOf(now);
        LocalDate targetDate = LocalDate.ofInstant(roundAt, clock.getZone());
        if (!notificationRankRoundRepository.saveRound(roundAt, targetDate, now)) {
            return;
        }

        Map<Long, Integer> previousRanks = notificationRankRoundRepository.findPreviousSnapshot(targetDate, roundAt);
        Map<Long, Integer> currentRanks = findRanks(targetDate);
        saveSnapshot(roundAt, currentRanks);
        saveDroppedOutbox(previousRanks, currentRanks, targetDate, roundAt, now);
    }

    @Transactional
    public void deleteOldRounds() {
        notificationRankRoundRepository.deleteByTargetDateBefore(LocalDate.now(clock).minusDays(RETENTION_DAYS));
    }

    private Instant roundOf(Instant now) {
        long epochSecond = now.plus(EARLY_START_TOLERANCE).getEpochSecond();
        return Instant.ofEpochSecond(epochSecond - Math.floorMod(epochSecond, ROUND_INTERVAL.toSeconds()));
    }

    private Map<Long, Integer> findRanks(LocalDate date) {
        List<DevicePressCount> counts = dailyPressCountRepository.findByDate(date);
        return Ranking.rank(counts, DevicePressCount::pressCount, (rank, count) -> Map.entry(count.deviceId(), rank))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void saveSnapshot(Instant roundAt, Map<Long, Integer> currentRanks) {
        currentRanks.forEach((deviceId, rank) -> {
            if (rank <= NOTIFIED_RANK_LIMIT) {
                notificationRankRoundRepository.saveSnapshot(roundAt, deviceId, rank);
            }
        });
    }

    private void saveDroppedOutbox(
            Map<Long, Integer> previousRanks,
            Map<Long, Integer> currentRanks,
            LocalDate targetDate,
            Instant roundAt,
            Instant now
    ) {
        Instant expiresAt = roundAt.plus(ROUND_INTERVAL);
        previousRanks.forEach((deviceId, previousRank) -> {
            Integer currentRank = currentRanks.get(deviceId);
            if (isDroppedFromTop(previousRank, currentRank)) {
                notificationOutboxRepository.saveRank(
                        deviceId, targetDate, roundAt, previousRank, currentRank, now, expiresAt);
            }
        });
    }

    private boolean isDroppedFromTop(int previousRank, Integer currentRank) {
        return previousRank <= NOTIFIED_RANK_LIMIT && currentRank != null && currentRank > previousRank;
    }
}
