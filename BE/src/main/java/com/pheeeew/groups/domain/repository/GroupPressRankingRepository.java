package com.pheeeew.groups.domain.repository;

import com.pheeeew.emotion.domain.DeviceRegionDailyPress;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.groups.domain.repository.projection.GroupScoreProjection;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface GroupPressRankingRepository extends Repository<DeviceRegionDailyPress, Long> {

    @Query("""
            SELECT g.publicId AS groupPublicId,
                   g.name AS name,
                   s.text AS stampText,
                   s.textColor AS stampTextColor,
                   s.backgroundColor AS stampBackgroundColor,
                   s.frame AS stampFrame,
                   SUM(press.pressCount) AS score
            FROM DeviceRegionDailyPress press
            JOIN GroupMember member ON member.device.id = press.deviceId AND member.leftAt IS NULL
            JOIN member.group g
            JOIN GroupStamp s ON s.group = g
            WHERE g.deletedAt IS NULL
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
            FROM DeviceRegionDailyPress press
            JOIN GroupMember member ON member.device.id = press.deviceId AND member.leftAt IS NULL
            JOIN member.group g
            JOIN GroupStamp s ON s.group = g
            WHERE g.deletedAt IS NULL
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
            FROM DeviceRegionDailyPress press
            JOIN GroupMember member ON member.device.id = press.deviceId AND member.leftAt IS NULL
            JOIN member.group g
            WHERE g.deletedAt IS NULL
              AND press.pressDate < :startDate
            """)
    boolean existsPressBefore(@Param("startDate") LocalDate startDate);
}
