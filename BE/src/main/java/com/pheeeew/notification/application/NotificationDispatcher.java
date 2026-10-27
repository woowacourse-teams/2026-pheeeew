package com.pheeeew.notification.application;

import static com.pheeeew.notification.domain.NotificationOutboxType.COLLECTION_CLAIMABLE;

import com.pheeeew.collection.domain.repository.CollectionCardRepository;
import com.pheeeew.notification.domain.NotificationOutbox;
import com.pheeeew.notification.domain.PushSendResult;
import com.pheeeew.notification.domain.PushTarget;
import com.pheeeew.notification.domain.repository.DevicePushTokenRepository;
import com.pheeeew.notification.domain.repository.NotificationOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class NotificationDispatcher {

    private static final int MAX_DISPATCH_COUNT = 50;
    private static final Duration CLAIM_DURATION = Duration.ofMinutes(1);
    private static final LocalTime REMINDER_TAKEOVER_TIME = LocalTime.of(23, 0);
    private static final int RETENTION_DAYS = 7;
    private static final String NO_TARGET = "NO_TARGET";
    private static final String CARD_CLAIMED = "CARD_CLAIMED";
    private static final String SUPERSEDED = "SUPERSEDED";
    private static final String EXPIRED = "EXPIRED";
    private static final String UNREGISTERED = "UNREGISTERED";
    private static final String REJECTED = "REJECTED";
    private static final String RETRY_EXHAUSTED = "RETRY_EXHAUSTED";

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final DevicePushTokenRepository devicePushTokenRepository;
    private final CollectionCardRepository collectionCardRepository;
    private final PushSender pushSender;
    private final Clock clock;

    public void dispatch() {
        if (!pushSender.isAvailable()) {
            return;
        }
        notificationOutboxRepository.expire(clock.instant());

        Map<String, Integer> outcomes = new TreeMap<>();
        for (int count = 0; count < MAX_DISPATCH_COUNT; count++) {
            Instant now = clock.instant();
            Optional<NotificationOutbox> claimed = notificationOutboxRepository.claim(
                    now, UUID.randomUUID(), now.plus(CLAIM_DURATION));
            if (claimed.isEmpty()) {
                break;
            }
            String outcome = deliver(claimed.get(), now);
            outcomes.merge(outcome, 1, Integer::sum);
            if (PushSendResult.RETRYABLE.name().equals(outcome)) {
                break;
            }
        }
        logSummary(outcomes);
    }

    public void deleteOldOutbox() {
        notificationOutboxRepository.deleteByTargetDateBefore(LocalDate.now(clock).minusDays(RETENTION_DAYS));
    }

    private String deliver(NotificationOutbox outbox, Instant now) {
        if (outbox.attemptCount() > outbox.type().maxAttempts()) {
            return fail(outbox, RETRY_EXHAUSTED);
        }
        if (isTakenOverByReminder(outbox, now)) {
            return cancel(outbox, SUPERSEDED);
        }
        Optional<PushTarget> target = findTarget(outbox);
        if (target.isEmpty()) {
            return cancel(outbox, NO_TARGET);
        }
        if (outbox.type().isCollection() && isCardClaimed(outbox)) {
            return cancel(outbox, CARD_CLAIMED);
        }
        if (!clock.instant().isBefore(outbox.expiresAt())) {
            return cancel(outbox, EXPIRED);
        }

        PushSendResult result = send(outbox, target.get());
        record(outbox, target.get(), result);
        return result.name();
    }

    private String fail(NotificationOutbox outbox, String resultCode) {
        boolean recorded = notificationOutboxRepository.fail(
                outbox.type(), outbox.id(), outbox.claimToken(), resultCode, clock.instant());
        warnIfClaimLost(recorded, outbox);
        return resultCode;
    }

    private void warnIfClaimLost(boolean recorded, NotificationOutbox outbox) {
        if (!recorded) {
            log.warn("선점을 잃어 결과를 기록하지 못했습니다. type={}, outboxId={}", outbox.type(), outbox.id());
        }
    }

    private boolean isTakenOverByReminder(NotificationOutbox outbox, Instant now) {
        return outbox.type() == COLLECTION_CLAIMABLE
                && !LocalTime.ofInstant(now, clock.getZone()).isBefore(REMINDER_TAKEOVER_TIME);
    }

    private String cancel(NotificationOutbox outbox, String resultCode) {
        boolean recorded = notificationOutboxRepository.cancel(
                outbox.type(), outbox.id(), outbox.claimToken(), resultCode, clock.instant());
        warnIfClaimLost(recorded, outbox);
        return resultCode;
    }

    private Optional<PushTarget> findTarget(NotificationOutbox outbox) {
        List<Long> deviceIds = List.of(outbox.deviceId());
        List<PushTarget> targets = outbox.type().isCollection()
                ? devicePushTokenRepository.findCollectionClaimableTargets(deviceIds)
                : devicePushTokenRepository.findRankChangedTargets(deviceIds);
        return targets.stream().findFirst();
    }

    private boolean isCardClaimed(NotificationOutbox outbox) {
        return collectionCardRepository.findByDeviceIdAndCardDate(outbox.deviceId(), outbox.targetDate())
                .isPresent();
    }

    private PushSendResult send(NotificationOutbox outbox, PushTarget target) {
        try {
            return pushSender.send(target.token(), outbox.message());
        } catch (RuntimeException exception) {
            log.warn("알림을 보내지 못했습니다. type={}, outboxId={}, deviceId={}",
                    outbox.type(), outbox.id(), outbox.deviceId(), exception);
            return PushSendResult.RETRYABLE;
        }
    }

    private void record(NotificationOutbox outbox, PushTarget target, PushSendResult result) {
        switch (result) {
            case SENT -> warnIfClaimLost(notificationOutboxRepository.markSent(
                    outbox.type(), outbox.id(), outbox.claimToken(), clock.instant()), outbox);
            case UNREGISTERED -> {
                devicePushTokenRepository.deleteByDeviceIdAndToken(target.deviceId(), target.token());
                fail(outbox, UNREGISTERED);
            }
            case REJECTED -> fail(outbox, REJECTED);
            case RETRYABLE -> retryOrFail(outbox);
        }
    }

    private void retryOrFail(NotificationOutbox outbox) {
        Instant failedAt = clock.instant();
        Optional<Instant> nextAvailableAt = outbox.type().retryDelayAfter(outbox.attemptCount())
                .map(failedAt::plus)
                .filter(availableAt -> availableAt.isBefore(outbox.expiresAt()));
        if (nextAvailableAt.isEmpty()) {
            fail(outbox, RETRY_EXHAUSTED);
            return;
        }
        boolean recorded = notificationOutboxRepository.retry(
                outbox.type(), outbox.id(), outbox.claimToken(), nextAvailableAt.get(), failedAt);
        warnIfClaimLost(recorded, outbox);
    }

    private void logSummary(Map<String, Integer> outcomes) {
        if (outcomes.isEmpty()) {
            return;
        }
        int processed = outcomes.values().stream().mapToInt(Integer::intValue).sum();
        log.info("알림 발송 작업을 처리했습니다. processed={}, outcomes={}", processed, outcomes);
    }
}
