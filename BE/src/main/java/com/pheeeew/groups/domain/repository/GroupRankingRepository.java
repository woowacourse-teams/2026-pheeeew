package com.pheeeew.groups.domain.repository;

import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.groups.domain.repository.projection.GroupScoreProjection;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface GroupRankingRepository extends Repository<Emotion, Long> {

    @Query("""
            SELECT g.publicId AS groupPublicId,
                   g.name AS name,
                   s.text AS stampText,
                   s.textColor AS stampTextColor,
                   s.backgroundColor AS stampBackgroundColor,
                   s.frame AS stampFrame,
                   COUNT(emotion.id) AS score
            FROM Emotion emotion
            JOIN emotion.groupStamp s
            JOIN s.group g
            WHERE emotion.deletedAt IS NULL
              AND g.deletedAt IS NULL
              AND emotion.createdAt >= :startAt
              AND emotion.createdAt < :endAt
            GROUP BY g.publicId, g.name, s.text, s.textColor, s.backgroundColor, s.frame
            ORDER BY COUNT(emotion.id) DESC, g.name
            """)
    List<GroupScoreProjection> findGroupScores(@Param("startAt") Instant startAt, @Param("endAt") Instant endAt);

    @Query("""
            SELECT COUNT(emotion.id) > 0
            FROM Emotion emotion
            JOIN emotion.groupStamp s
            JOIN s.group g
            WHERE emotion.deletedAt IS NULL
              AND g.deletedAt IS NULL
              AND emotion.createdAt < :startAt
            """)
    boolean existsBefore(@Param("startAt") Instant startAt);

}
