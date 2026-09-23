package com.pheeeew.report.domain.repository;

import com.pheeeew.report.domain.SighReport;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SighReportRepository extends JpaRepository<SighReport, Long> {

    Optional<SighReport> findBySighIdAndReporterDeviceId(Long emotionId, Long reporterDeviceId);

    @Modifying
    @Query("""
            UPDATE Sigh emotion
               SET emotion.deletedAt = :now
             WHERE emotion.deletedAt IS NULL
               AND emotion.id IN (
                   SELECT emotionReport.sighId
                     FROM SighReport emotionReport
                    GROUP BY emotionReport.sighId
                   HAVING COUNT(emotionReport.id) >= :threshold
               )
            """)
    int deleteReportedOverThreshold(long threshold, Instant now);
}
