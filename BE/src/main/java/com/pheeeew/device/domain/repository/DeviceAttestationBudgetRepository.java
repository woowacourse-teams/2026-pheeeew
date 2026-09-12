package com.pheeeew.device.domain.repository;

import com.pheeeew.device.domain.DeviceAttestationBudget;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DeviceAttestationBudgetRepository extends JpaRepository<DeviceAttestationBudget, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO device_attestation_budgets (budget_date, call_count, created_at, updated_at)
                 VALUES (:budgetDate, 1, :now, :now)
            ON CONFLICT (budget_date) DO UPDATE
                    SET call_count = device_attestation_budgets.call_count + 1,
                        updated_at = :now
                  WHERE device_attestation_budgets.call_count < :dailyCallBudget
            """, nativeQuery = true)
    int increaseCallCount(LocalDate budgetDate, int dailyCallBudget, Instant now);
}
