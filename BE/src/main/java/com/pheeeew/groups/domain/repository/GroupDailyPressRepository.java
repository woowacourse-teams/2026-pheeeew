package com.pheeeew.groups.domain.repository;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.groups.domain.GroupDailyPress;
import com.pheeeew.groups.domain.repository.projection.GroupPressSumProjection;
import com.pheeeew.groups.domain.repository.projection.GroupScoreProjection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupDailyPressRepository extends JpaRepository<GroupDailyPress, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO group_daily_presses (group_id, press_date, state, press_count, created_at, updated_at)
                 VALUES (:groupId, :pressDate, :state, :delta, :now, :now)
            ON CONFLICT (group_id, press_date, state) DO UPDATE
               SET press_count = group_daily_presses.press_count + :delta,
                   updated_at = :now
            """, nativeQuery = true)
    void increase(
            @Param("groupId") Long groupId,
            @Param("pressDate") LocalDate pressDate,
            @Param("state") String state,
            @Param("delta") int delta,
            @Param("now") Instant now
    );

    List<GroupDailyPress> findByGroupIdAndPressDate(Long groupId, LocalDate pressDate);

    @Query("""
            SELECT g.publicId AS groupPublicId,
                   g.name AS name,
                   s.text AS stampText,
                   s.textColor AS stampTextColor,
                   s.backgroundColor AS stampBackgroundColor,
                   s.frame AS stampFrame,
                   SUM(press.pressCount) AS score
            FROM GroupDailyPress press, GroupStamp s
            JOIN s.group g
            WHERE g.id = press.groupId
              AND g.deletedAt IS NULL
              AND press.pressDate >= :startDate
              AND press.pressDate < :endDate
            GROUP BY g.publicId, g.name, s.text, s.textColor, s.backgroundColor, s.frame
            ORDER BY SUM(press.pressCount) DESC, g.name
            """)
    List<GroupScoreProjection> findPressScores(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT g.publicId AS groupPublicId,
                   g.name AS name,
                   s.text AS stampText,
                   s.textColor AS stampTextColor,
                   s.backgroundColor AS stampBackgroundColor,
                   s.frame AS stampFrame,
                   SUM(press.pressCount) AS score
            FROM GroupDailyPress press, GroupStamp s
            JOIN s.group g
            WHERE g.id = press.groupId
              AND g.deletedAt IS NULL
              AND press.state = :state
              AND press.pressDate >= :startDate
              AND press.pressDate < :endDate
            GROUP BY g.publicId, g.name, s.text, s.textColor, s.backgroundColor, s.frame
            ORDER BY SUM(press.pressCount) DESC, g.name
            """)
    List<GroupScoreProjection> findPressScoresByState(
            @Param("state") EmotionState state,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT COUNT(press.id) > 0
            FROM GroupDailyPress press, GroupStamp s
            JOIN s.group g
            WHERE g.id = press.groupId
              AND g.deletedAt IS NULL
              AND press.pressDate < :startDate
            """)
    boolean existsPressBefore(@Param("startDate") LocalDate startDate);

    @Query("""
            SELECT press.state AS state, SUM(press.pressCount) AS pressCount
            FROM GroupDailyPress press
            WHERE press.groupId = :groupId
              AND press.pressDate >= :startDate
              AND press.pressDate < :endDate
            GROUP BY press.state
            """)
    List<GroupPressSumProjection> sumByGroupIdAndPressDateBetween(
            @Param("groupId") Long groupId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
