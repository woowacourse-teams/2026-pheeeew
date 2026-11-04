package com.pheeeew.notification.domain.repository;

import com.pheeeew.notification.domain.NotificationRankSnapshot;
import com.pheeeew.notification.domain.NotificationRankSnapshotId;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRankSnapshotRepository
        extends JpaRepository<NotificationRankSnapshot, NotificationRankSnapshotId> {

    List<NotificationRankSnapshot> findByRoundAt(Instant roundAt);
}
