package com.pheeeew.notification.domain.repository;

import com.pheeeew.notification.domain.NotificationRankRound;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRankRoundRepository extends JpaRepository<NotificationRankRound, Instant> {

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO notification_rank_rounds (round_at, target_date, created_at, updated_at)
                 VALUES (:#{#round.roundAt}, :#{#round.targetDate}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int saveIfAbsent(@Param("round") NotificationRankRound round);

    Optional<NotificationRankRound> findFirstByTargetDateAndRoundAtBeforeOrderByRoundAtDesc(
            LocalDate targetDate,
            Instant roundAt
    );

    @Modifying
    @Query("DELETE FROM NotificationRankRound r WHERE r.targetDate < :date")
    int deleteByTargetDateBefore(@Param("date") LocalDate date);
}
