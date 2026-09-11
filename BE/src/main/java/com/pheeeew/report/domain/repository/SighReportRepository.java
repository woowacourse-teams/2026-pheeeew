package com.pheeeew.report.domain.repository;

import com.pheeeew.report.domain.SighReport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SighReportRepository extends JpaRepository<SighReport, Long> {

    Optional<SighReport> findBySighIdAndReporterDeviceId(Long sighId, Long reporterDeviceId);
}
