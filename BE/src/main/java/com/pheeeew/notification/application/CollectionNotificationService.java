package com.pheeeew.notification.application;

import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
import com.pheeeew.notification.domain.PushMessage;
import com.pheeeew.notification.domain.PushSendResult;
import com.pheeeew.notification.domain.PushTarget;
import com.pheeeew.notification.domain.repository.DevicePushTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class CollectionNotificationService {

    private static final Duration CLAIMABLE_NOTIFICATION_DELAY = Duration.ofHours(1);
    private static final String CLAIMABLE_TYPE = "COLLECTION_CLAIMABLE";
    private static final String CLAIMABLE_TITLE = "컬렉션 발급 조건 달성!";
    private static final String CLAIMABLE_BODY = "오늘의 감정 카드를 받으세요. 카드 받으러 가기👉";
    private static final String REMINDER_TYPE = "COLLECTION_CLAIM_REMINDER";
    private static final String REMINDER_TITLE = "아직 받지 않은 카드가 있어요";
    private static final String REMINDER_BODY = "오늘의 카드는 자정이 지나면 받을 수 없어요. 카드 받으러 가기👉";

    private final CollectionProgressRepository collectionProgressRepository;
    private final DevicePushTokenRepository devicePushTokenRepository;
    private final PushSender pushSender;
    private final Clock clock;

    private final Map<Long, Instant> claimableSince = new HashMap<>();
    private final Set<Long> notifiedDeviceIds = new HashSet<>();
    private LocalDate trackedDate;

    public synchronized void notifyClaimable() {
        if (!pushSender.isAvailable()) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        resetIfDateChanged(today);

        Set<Long> claimable = new HashSet<>(collectionProgressRepository.findClaimableDeviceIds(today));
        claimableSince.keySet().retainAll(claimable);
        claimable.stream()
                .filter(deviceId -> !notifiedDeviceIds.contains(deviceId))
                .forEach(deviceId -> claimableSince.putIfAbsent(deviceId, now));

        List<Long> due = claimableSince.entrySet().stream()
                .filter(entry -> !entry.getValue().plus(CLAIMABLE_NOTIFICATION_DELAY).isAfter(now))
                .map(Map.Entry::getKey)
                .toList();
        due.forEach(claimableSince::remove);
        notifiedDeviceIds.addAll(due);

        send(due, message(CLAIMABLE_TYPE, CLAIMABLE_TITLE, CLAIMABLE_BODY, today));
    }

    public void remindClaimable() {
        if (!pushSender.isAvailable()) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        List<Long> claimable = collectionProgressRepository.findClaimableDeviceIds(today);

        send(claimable, message(REMINDER_TYPE, REMINDER_TITLE, REMINDER_BODY, today));
    }

    private void resetIfDateChanged(LocalDate today) {
        if (today.equals(trackedDate)) {
            return;
        }
        claimableSince.clear();
        notifiedDeviceIds.clear();
        trackedDate = today;
    }

    private PushMessage message(String type, String title, String body, LocalDate date) {
        return PushMessage.of(title, body, Map.of("type", type, "date", date.toString()));
    }

    private void send(List<Long> deviceIds, PushMessage message) {
        if (deviceIds.isEmpty()) {
            return;
        }
        List<PushTarget> targets = devicePushTokenRepository.findCollectionClaimableTargets(deviceIds);
        for (PushTarget target : targets) {
            sendTo(target, message);
        }
        log.info("컬렉션 알림을 보냈습니다. type={}, targets={}", message.data().get("type"), targets.size());
    }

    private void sendTo(PushTarget target, PushMessage message) {
        try {
            if (pushSender.send(target.token(), message) == PushSendResult.UNREGISTERED) {
                devicePushTokenRepository.deleteByDeviceIdAndToken(target.deviceId(), target.token());
            }
        } catch (RuntimeException exception) {
            log.warn("컬렉션 알림을 보내지 못했습니다. deviceId={}", target.deviceId(), exception);
        }
    }
}
