package com.pheeeew.notification.domain.repository;

import com.pheeeew.notification.domain.outbox.CollectionClaimableOutbox;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CollectionClaimableOutboxRepository extends NotificationOutboxRepository<CollectionClaimableOutbox> {

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO collection_claimable_outbox
                 (device_id, target_date, status, discovered_at, available_at, expires_at, attempt_count,
                  created_at, updated_at)
                 VALUES (:#{#outbox.deviceId}, :#{#outbox.targetDate}, :#{#outbox.status.name()},
                         :#{#outbox.discoveredAt}, :#{#outbox.availableAt}, :#{#outbox.expiresAt},
                         :#{#outbox.attemptCount}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void saveIfAbsent(@Param("outbox") CollectionClaimableOutbox outbox);
}
