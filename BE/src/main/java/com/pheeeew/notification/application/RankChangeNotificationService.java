package com.pheeeew.notification.application;

import com.pheeeew.common.application.Ranking;
import com.pheeeew.notification.domain.DevicePressCount;
import com.pheeeew.notification.domain.NotificationRankRound;
import com.pheeeew.notification.domain.NotificationRankSnapshot;
import com.pheeeew.notification.domain.outbox.RankDroppedOutbox;
import com.pheeeew.notification.domain.repository.DailyPressCountRepository;
import com.pheeeew.notification.domain.repository.NotificationRankRoundRepository;
import com.pheeeew.notification.domain.repository.NotificationRankSnapshotRepository;
import com.pheeeew.notification.domain.repository.RankDroppedOutboxRepository;
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
@Transactional(readOnly = true)
@Service
public class RankChangeNotificationService {

    private static final int NOTIFIED_RANK_LIMIT = 5;
    private static final Duration ROUND_INTERVAL = Duration.ofMinutes(5);
    private static final Duration EARLY_START_TOLERANCE = Duration.ofSeconds(30);
    private static final int RETENTION_DAYS = 7;

    private final DailyPressCountRepository dailyPressCountRepository;
    private final NotificationRankRoundRepository notificationRankRoundRepository;
    private final NotificationRankSnapshotRepository notificationRankSnapshotRepository;
    private final RankDroppedOutboxRepository rankDroppedOutboxRepository;
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
        NotificationRankRound round = NotificationRankRound.builder()
                .roundAt(roundAt)
                .targetDate(targetDate)
                .build();
        if (notificationRankRoundRepository.saveIfAbsent(round) == 0) {
            return;
        }

        Map<Long, Integer> previousRanks = findPreviousRanks(targetDate, roundAt);
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

    private Map<Long, Integer> findPreviousRanks(LocalDate targetDate, Instant roundAt) {
        return notificationRankRoundRepository.findFirstByTargetDateAndRoundAtBeforeOrderByRoundAtDesc(
                        targetDate, roundAt)
                .map(previousRound -> notificationRankSnapshotRepository.findByRoundAt(previousRound.getRoundAt()))
                .orElseGet(List::of)
                .stream()
                .collect(Collectors.toMap(NotificationRankSnapshot::getDeviceId, NotificationRankSnapshot::getRank));
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
                notificationRankSnapshotRepository.save(NotificationRankSnapshot.builder()
                        .roundAt(roundAt)
                        .deviceId(deviceId)
                        .rank(rank)
                        .build());
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
                rankDroppedOutboxRepository.save(RankDroppedOutbox.builder()
                        .deviceId(deviceId)
                        .targetDate(targetDate)
                        .roundAt(roundAt)
                        .previousRank(previousRank)
                        .currentRank(currentRank)
                        .discoveredAt(now)
                        .availableAt(now)
                        .expiresAt(expiresAt)
                        .build());
            }
        });
    }

    private boolean isDroppedFromTop(int previousRank, Integer currentRank) {
        return previousRank <= NOTIFIED_RANK_LIMIT && currentRank != null && currentRank > previousRank;
    }
}
