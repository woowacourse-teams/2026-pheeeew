package com.pheeeew.report.domain.repository;

import com.pheeeew.report.domain.EmotionReport;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EmotionReportRepository extends JpaRepository<EmotionReport, Long> {

    Optional<EmotionReport> findByEmotionIdAndReporterDeviceId(Long emotionId, Long reporterDeviceId);

    @Modifying
    @Query("""
            UPDATE Emotion emotion
               SET emotion.deletedAt = :now
             WHERE emotion.deletedAt IS NULL
               AND emotion.id IN (
                   SELECT emotionReport.emotionId
                     FROM EmotionReport emotionReport
                    GROUP BY emotionReport.emotionId
                   HAVING COUNT(emotionReport.id) >= :threshold
               )
            """)
    int deleteReportedOverThreshold(long threshold, Instant now);
}
