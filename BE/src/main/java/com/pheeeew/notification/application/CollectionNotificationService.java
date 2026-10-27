package com.pheeeew.notification.application;

import static com.pheeeew.notification.domain.NotificationOutboxType.COLLECTION_CLAIMABLE;
import static com.pheeeew.notification.domain.NotificationOutboxType.COLLECTION_CLAIM_REMINDER;

import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
import com.pheeeew.notification.domain.repository.NotificationOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class CollectionNotificationService {

    private static final Duration CLAIMABLE_NOTIFICATION_DELAY = Duration.ofHours(1);

    private final CollectionProgressRepository collectionProgressRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final PushSender pushSender;
    private final Clock clock;

    @Transactional
    public void saveClaimableOutbox() {
        if (!pushSender.isAvailable()) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        Instant availableAt = now.plus(CLAIMABLE_NOTIFICATION_DELAY);
        Instant expiresAt = endOf(today);

        collectionProgressRepository.findClaimableDeviceIds(today).forEach(deviceId ->
                notificationOutboxRepository.saveCollection(
                        COLLECTION_CLAIMABLE, deviceId, today, now, availableAt, expiresAt));
    }

    @Transactional
    public void saveReminderOutbox() {
        if (!pushSender.isAvailable()) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        Instant expiresAt = endOf(today);

        collectionProgressRepository.findClaimableDeviceIds(today).forEach(deviceId ->
                notificationOutboxRepository.saveCollection(
                        COLLECTION_CLAIM_REMINDER, deviceId, today, now, now, expiresAt));
    }

    private Instant endOf(LocalDate date) {
        return date.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
    }
}
