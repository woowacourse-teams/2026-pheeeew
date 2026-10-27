package com.pheeeew.notification.domain.repository;

import com.pheeeew.notification.domain.outbox.NotificationOutbox;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

@NoRepositoryBean
public interface NotificationOutboxRepository<T extends NotificationOutbox> extends JpaRepository<T, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT o FROM #{#entityName} o
             WHERE o.status = com.pheeeew.notification.domain.outbox.NotificationOutboxStatus.PENDING
               AND o.availableAt <= :now AND o.expiresAt > :now
               AND (o.claimExpiresAt IS NULL OR o.claimExpiresAt <= :now)
             ORDER BY o.expiresAt, o.availableAt, o.id
            """)
    List<T> findClaimable(@Param("now") Instant now, Limit limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT o FROM #{#entityName} o
             WHERE o.status = com.pheeeew.notification.domain.outbox.NotificationOutboxStatus.PENDING
               AND o.expiresAt <= :now
               AND (o.claimExpiresAt IS NULL OR o.claimExpiresAt <= :now)
            """)
    List<T> findExpired(@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<T> findByIdAndClaimToken(Long id, UUID claimToken);

    @Modifying
    @Query("DELETE FROM #{#entityName} o WHERE o.targetDate < :date")
    int deleteByTargetDateBefore(@Param("date") LocalDate date);
}
