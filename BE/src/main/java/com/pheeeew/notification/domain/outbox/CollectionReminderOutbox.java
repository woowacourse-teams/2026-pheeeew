package com.pheeeew.notification.domain.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "collection_reminder_outbox")
@Entity
public class CollectionReminderOutbox extends NotificationOutbox {

    @Builder
    private CollectionReminderOutbox(
            Long deviceId,
            LocalDate targetDate,
            Instant discoveredAt,
            Instant availableAt,
            Instant expiresAt
    ) {
        super(deviceId, targetDate, discoveredAt, availableAt, expiresAt);
    }

    @Override
    public NotificationOutboxType type() {
        return NotificationOutboxType.COLLECTION_CLAIM_REMINDER;
    }
}
