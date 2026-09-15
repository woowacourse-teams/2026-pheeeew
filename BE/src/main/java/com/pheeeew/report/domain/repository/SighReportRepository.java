package com.pheeeew.report.domain.repository;

import com.pheeeew.report.domain.SighReport;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SighReportRepository extends JpaRepository<SighReport, Long> {

    Optional<SighReport> findBySighIdAndReporterDeviceId(Long sighId, Long reporterDeviceId);

    @Modifying
    @Query("""
            UPDATE Sigh sigh
               SET sigh.deletedAt = :now
             WHERE sigh.deletedAt IS NULL
               AND sigh.id IN (
                   SELECT sighReport.sighId
                     FROM SighReport sighReport
                    GROUP BY sighReport.sighId
                   HAVING COUNT(sighReport.id) >= :threshold
               )
            """)
    int deleteReportedOverThreshold(long threshold, Instant now);
}
