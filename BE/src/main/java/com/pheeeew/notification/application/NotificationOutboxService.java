package com.pheeeew.notification.application;

import com.pheeeew.notification.domain.outbox.NotificationOutbox;
import com.pheeeew.notification.domain.outbox.NotificationOutboxType;
import com.pheeeew.notification.domain.repository.CollectionClaimableOutboxRepository;
import com.pheeeew.notification.domain.repository.CollectionReminderOutboxRepository;
import com.pheeeew.notification.domain.repository.NotificationOutboxRepository;
import com.pheeeew.notification.domain.repository.RankDroppedOutboxRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class NotificationOutboxService {

    private final CollectionClaimableOutboxRepository collectionClaimableOutboxRepository;
    private final CollectionReminderOutboxRepository collectionReminderOutboxRepository;
    private final RankDroppedOutboxRepository rankDroppedOutboxRepository;

    @Transactional
    public int expire(NotificationOutboxType type, String resultCode, Instant now) {
        List<? extends NotificationOutbox> expired = repositoryOf(type).findExpired(now);
        expired.forEach(outbox -> outbox.expire(resultCode, now));
        return expired.size();
    }

    @Transactional
    public Optional<NotificationOutbox> claim(
            NotificationOutboxType type,
            UUID claimToken,
            Instant claimExpiresAt,
            Instant now
    ) {
        Optional<NotificationOutbox> claimable = repositoryOf(type).findClaimable(now, Limit.of(1)).stream()
                .findFirst()
                .map(NotificationOutbox.class::cast);
        claimable.ifPresent(outbox -> outbox.claim(claimToken, claimExpiresAt, now));
        return claimable;
    }

    @Transactional
    public boolean markSent(NotificationOutbox claimed, Instant now) {
        return recordResult(claimed, outbox -> outbox.markSent(claimed.getClaimToken(), now));
    }

    @Transactional
    public boolean scheduleRetry(NotificationOutbox claimed, Instant nextAvailableAt, Instant now) {
        return recordResult(claimed, outbox -> outbox.scheduleRetry(claimed.getClaimToken(), nextAvailableAt, now));
    }

    @Transactional
    public boolean cancel(NotificationOutbox claimed, String resultCode, Instant now) {
        return recordResult(claimed, outbox -> outbox.cancel(claimed.getClaimToken(), resultCode, now));
    }

    @Transactional
    public boolean fail(NotificationOutbox claimed, String resultCode, Instant now) {
        return recordResult(claimed, outbox -> outbox.fail(claimed.getClaimToken(), resultCode, now));
    }

    @Transactional
    public void deleteByTargetDateBefore(LocalDate date) {
        collectionClaimableOutboxRepository.deleteByTargetDateBefore(date);
        collectionReminderOutboxRepository.deleteByTargetDateBefore(date);
        rankDroppedOutboxRepository.deleteByTargetDateBefore(date);
    }

    private NotificationOutboxRepository<? extends NotificationOutbox> repositoryOf(NotificationOutboxType type) {
        return switch (type) {
            case COLLECTION_CLAIMABLE -> collectionClaimableOutboxRepository;
            case COLLECTION_CLAIM_REMINDER -> collectionReminderOutboxRepository;
            case RANK_DROPPED -> rankDroppedOutboxRepository;
        };
    }

    private boolean recordResult(NotificationOutbox claimed, Consumer<NotificationOutbox> transition) {
        Optional<? extends NotificationOutbox> owned = repositoryOf(claimed.type())
                .findByIdAndClaimToken(claimed.getId(), claimed.getClaimToken());
        owned.ifPresent(transition);
        return owned.isPresent();
    }
}
