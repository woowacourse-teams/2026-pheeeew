package com.pheeeew.notification.application;

import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
import com.pheeeew.notification.domain.outbox.CollectionClaimableOutbox;
import com.pheeeew.notification.domain.outbox.CollectionReminderOutbox;
import com.pheeeew.notification.domain.repository.CollectionClaimableOutboxRepository;
import com.pheeeew.notification.domain.repository.CollectionReminderOutboxRepository;
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
    private final CollectionClaimableOutboxRepository collectionClaimableOutboxRepository;
    private final CollectionReminderOutboxRepository collectionReminderOutboxRepository;
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
                collectionClaimableOutboxRepository.saveIfAbsent(CollectionClaimableOutbox.builder()
                        .deviceId(deviceId)
                        .targetDate(today)
                        .discoveredAt(now)
                        .availableAt(availableAt)
                        .expiresAt(expiresAt)
                        .build()));
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
                collectionReminderOutboxRepository.saveIfAbsent(CollectionReminderOutbox.builder()
                        .deviceId(deviceId)
                        .targetDate(today)
                        .discoveredAt(now)
                        .availableAt(now)
                        .expiresAt(expiresAt)
                        .build()));
    }

    private Instant endOf(LocalDate date) {
        return date.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
    }
}
