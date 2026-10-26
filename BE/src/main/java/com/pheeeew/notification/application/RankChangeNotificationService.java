package com.pheeeew.notification.application;

import com.pheeeew.common.application.Ranking;
import com.pheeeew.notification.domain.DevicePressCount;
import com.pheeeew.notification.domain.PushMessage;
import com.pheeeew.notification.domain.PushSendResult;
import com.pheeeew.notification.domain.PushTarget;
import com.pheeeew.notification.domain.repository.DailyPressCountRepository;
import com.pheeeew.notification.domain.repository.DevicePushTokenRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class RankChangeNotificationService {

    private static final int NOTIFIED_RANK_LIMIT = 5;
    private static final String TYPE = "RANK_DROPPED";
    private static final String TITLE = "앗, 프레스 순위가 내려갔어요👀";
    private static final String BODY = "오늘 내 순위 %d위 → %d위!\n쌓인 마음으로 순위를 올리러 갈까요?";

    private final DailyPressCountRepository dailyPressCountRepository;
    private final DevicePushTokenRepository devicePushTokenRepository;
    private final PushSender pushSender;
    private final Clock clock;

    private Map<Long, Integer> previousRanks = Map.of();
    private LocalDate trackedDate;

    public synchronized void notifyRankChanged() {
        if (!pushSender.isAvailable()) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        Map<Long, Integer> currentRanks = findRanks(today);
        if (!today.equals(trackedDate)) {
            previousRanks = currentRanks;
            trackedDate = today;
            return;
        }

        Map<Long, Integer> previous = previousRanks;
        previousRanks = currentRanks;
        List<Long> dropped = currentRanks.keySet().stream()
                .filter(deviceId -> isDroppedFromTop(previous.get(deviceId), currentRanks.get(deviceId)))
                .toList();

        send(dropped, previous, currentRanks, today);
    }

    private Map<Long, Integer> findRanks(LocalDate date) {
        List<DevicePressCount> counts = dailyPressCountRepository.findByDate(date);
        return Ranking.rank(counts, DevicePressCount::pressCount, (rank, count) -> Map.entry(count.deviceId(), rank))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private boolean isDroppedFromTop(Integer previousRank, int currentRank) {
        return previousRank != null && previousRank <= NOTIFIED_RANK_LIMIT && currentRank > previousRank;
    }

    private void send(List<Long> deviceIds, Map<Long, Integer> previous, Map<Long, Integer> current, LocalDate date) {
        if (deviceIds.isEmpty()) {
            return;
        }
        List<PushTarget> targets = devicePushTokenRepository.findRankChangedTargets(deviceIds);
        for (PushTarget target : targets) {
            sendTo(target, message(previous.get(target.deviceId()), current.get(target.deviceId()), date));
        }
        log.info("순위 하락 알림을 보냈습니다. targets={}", targets.size());
    }

    private PushMessage message(int previousRank, int currentRank, LocalDate date) {
        return PushMessage.of(TITLE, BODY.formatted(previousRank, currentRank), Map.of(
                "type", TYPE,
                "date", date.toString(),
                "previousRank", String.valueOf(previousRank),
                "currentRank", String.valueOf(currentRank)
        ));
    }

    private void sendTo(PushTarget target, PushMessage message) {
        try {
            if (pushSender.send(target.token(), message) == PushSendResult.UNREGISTERED) {
                devicePushTokenRepository.deleteByDeviceIdAndToken(target.deviceId(), target.token());
            }
        } catch (RuntimeException exception) {
            log.warn("순위 하락 알림을 보내지 못했습니다. deviceId={}", target.deviceId(), exception);
        }
    }
}
