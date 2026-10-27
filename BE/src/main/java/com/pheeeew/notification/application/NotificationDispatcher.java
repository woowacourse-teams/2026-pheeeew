package com.pheeeew.notification.application;

import static com.pheeeew.notification.domain.outbox.NotificationOutboxType.COLLECTION_CLAIMABLE;

import com.pheeeew.collection.domain.repository.CollectionCardRepository;
import com.pheeeew.notification.domain.DeviceNotificationSetting;
import com.pheeeew.notification.domain.DevicePushToken;
import com.pheeeew.notification.domain.NotificationSettings;
import com.pheeeew.notification.domain.PushRecipient;
import com.pheeeew.notification.domain.PushSendResult;
import com.pheeeew.notification.domain.PushTarget;
import com.pheeeew.notification.domain.outbox.NotificationOutbox;
import com.pheeeew.notification.domain.outbox.NotificationOutboxType;
import com.pheeeew.notification.domain.repository.DeviceNotificationSettingRepository;
import com.pheeeew.notification.domain.repository.DevicePushTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
    private static final String NO_TOKEN = "NO_TOKEN";
    private static final String NOTIFICATION_DISABLED = "NOTIFICATION_DISABLED";
    private static final String CARD_CLAIMED = "CARD_CLAIMED";
    private static final String SUPERSEDED = "SUPERSEDED";
    private static final String EXPIRED = "EXPIRED";
    private static final String UNREGISTERED = "UNREGISTERED";
    private static final String REJECTED = "REJECTED";
    private static final String RETRY_EXHAUSTED = "RETRY_EXHAUSTED";

    private final NotificationOutboxService notificationOutboxService;
    private final DevicePushTokenRepository devicePushTokenRepository;
    private final DeviceNotificationSettingRepository deviceNotificationSettingRepository;
    private final CollectionCardRepository collectionCardRepository;
    private final PushSender pushSender;
    private final Clock clock;

    public void dispatch(NotificationOutboxType type) {
        if (!pushSender.isAvailable()) {
            return;
        }
        notificationOutboxService.expire(type, EXPIRED, clock.instant());

        Map<String, Integer> outcomes = new TreeMap<>();
        for (int count = 0; count < MAX_DISPATCH_COUNT; count++) {
            Instant now = clock.instant();
            Optional<NotificationOutbox> claimed = notificationOutboxService.claim(
                    type, UUID.randomUUID(), now.plus(CLAIM_DURATION), now);
            if (claimed.isEmpty()) {
                break;
            }
            String outcome = deliver(claimed.get(), now);
            outcomes.merge(outcome, 1, Integer::sum);
            if (PushSendResult.RETRYABLE.name().equals(outcome)) {
                break;
            }
        }
        logSummary(type, outcomes);
    }

    public void deleteOldOutbox() {
        notificationOutboxService.deleteByTargetDateBefore(LocalDate.now(clock).minusDays(RETENTION_DAYS));
    }

    private String deliver(NotificationOutbox outbox, Instant now) {
        if (outbox.getAttemptCount() > outbox.type().maxAttempts()) {
            return fail(outbox, RETRY_EXHAUSTED);
        }
        if (isTakenOverByReminder(outbox, now)) {
            return cancel(outbox, SUPERSEDED);
        }
        Optional<PushRecipient> recipient = findRecipient(outbox.getDeviceId());
        if (recipient.isEmpty()) {
            return cancel(outbox, NO_TOKEN);
        }
        if (!recipient.get().accepts(outbox.type())) {
            return cancel(outbox, NOTIFICATION_DISABLED);
        }
        if (outbox.type().isCollection() && isCardClaimed(outbox)) {
            return cancel(outbox, CARD_CLAIMED);
        }
        if (!clock.instant().isBefore(outbox.getExpiresAt())) {
            return cancel(outbox, EXPIRED);
        }

        PushTarget target = recipient.get().target();
        PushSendResult result = send(outbox, target);
        record(outbox, target, result);
        return result.name();
    }

    private String fail(NotificationOutbox outbox, String resultCode) {
        boolean recorded = notificationOutboxService.fail(outbox, resultCode, clock.instant());
        warnIfClaimLost(recorded, outbox);
        return resultCode;
    }

    private void warnIfClaimLost(boolean recorded, NotificationOutbox outbox) {
        if (!recorded) {
            log.warn("선점을 잃어 결과를 기록하지 못했습니다. type={}, outboxId={}", outbox.type(), outbox.getId());
        }
    }

    private boolean isTakenOverByReminder(NotificationOutbox outbox, Instant now) {
        return outbox.type() == COLLECTION_CLAIMABLE
                && !LocalTime.ofInstant(now, clock.getZone()).isBefore(REMINDER_TAKEOVER_TIME);
    }

    private String cancel(NotificationOutbox outbox, String resultCode) {
        boolean recorded = notificationOutboxService.cancel(outbox, resultCode, clock.instant());
        warnIfClaimLost(recorded, outbox);
        return resultCode;
    }

    private Optional<PushRecipient> findRecipient(Long deviceId) {
        Optional<PushTarget> target = devicePushTokenRepository.findByDeviceId(deviceId)
                .map(DevicePushToken::toTarget);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        NotificationSettings settings = deviceNotificationSettingRepository.findByDeviceId(deviceId)
                .map(DeviceNotificationSetting::toSettings)
                .orElseGet(NotificationSettings::defaults);
        return Optional.of(PushRecipient.of(target.get(), settings));
    }

    private boolean isCardClaimed(NotificationOutbox outbox) {
        return collectionCardRepository.findByDeviceIdAndCardDate(outbox.getDeviceId(), outbox.getTargetDate())
                .isPresent();
    }

    private PushSendResult send(NotificationOutbox outbox, PushTarget target) {
        try {
            return pushSender.send(target.token(), outbox.message());
        } catch (RuntimeException exception) {
            log.warn("알림을 보내지 못했습니다. type={}, outboxId={}, deviceId={}",
                    outbox.type(), outbox.getId(), outbox.getDeviceId(), exception);
            return PushSendResult.RETRYABLE;
        }
    }

    private void record(NotificationOutbox outbox, PushTarget target, PushSendResult result) {
        switch (result) {
            case SENT -> markSent(outbox);
            case UNREGISTERED -> {
                devicePushTokenRepository.deleteByDeviceIdAndToken(target.deviceId(), target.token());
                fail(outbox, UNREGISTERED);
            }
            case REJECTED -> fail(outbox, REJECTED);
            case RETRYABLE -> retryOrFail(outbox);
        }
    }

    private void markSent(NotificationOutbox outbox) {
        boolean recorded = notificationOutboxService.markSent(outbox, clock.instant());
        warnIfClaimLost(recorded, outbox);
    }

    private void retryOrFail(NotificationOutbox outbox) {
        Instant failedAt = clock.instant();
        Optional<Instant> nextAvailableAt = outbox.type().retryDelayAfter(outbox.getAttemptCount())
                .map(failedAt::plus)
                .filter(availableAt -> availableAt.isBefore(outbox.getExpiresAt()));
        if (nextAvailableAt.isEmpty()) {
            fail(outbox, RETRY_EXHAUSTED);
            return;
        }
        boolean recorded = notificationOutboxService.scheduleRetry(outbox, nextAvailableAt.get(), failedAt);
        warnIfClaimLost(recorded, outbox);
    }

    private void logSummary(NotificationOutboxType type, Map<String, Integer> outcomes) {
        if (outcomes.isEmpty()) {
            return;
        }
        int processed = outcomes.values().stream().mapToInt(Integer::intValue).sum();
        log.info("알림 발송 작업을 처리했습니다. type={}, processed={}, outcomes={}", type, processed, outcomes);
    }
}
