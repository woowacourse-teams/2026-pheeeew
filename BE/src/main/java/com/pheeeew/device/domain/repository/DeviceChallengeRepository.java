package com.pheeeew.device.domain.repository;

import com.pheeeew.device.domain.DeviceChallenge;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DeviceChallengeRepository extends JpaRepository<DeviceChallenge, Long> {

    @Modifying
    @Query("""
            UPDATE DeviceChallenge deviceChallenge
               SET deviceChallenge.attemptCount = deviceChallenge.attemptCount + 1,
                   deviceChallenge.updatedAt = :now
             WHERE deviceChallenge.challenge = :challenge
               AND deviceChallenge.consumedAt IS NULL
               AND deviceChallenge.expiresAt > :now
               AND deviceChallenge.attemptCount < :maxAttempts
            """)
    int consumeAttempt(String challenge, Instant now, int maxAttempts);

    @Modifying
    @Query("""
            UPDATE DeviceChallenge deviceChallenge
               SET deviceChallenge.consumedAt = :now,
                   deviceChallenge.updatedAt = :now
             WHERE deviceChallenge.challenge = :challenge
               AND deviceChallenge.consumedAt IS NULL
               AND deviceChallenge.expiresAt > :now
            """)
    int consume(String challenge, Instant now);

    @Modifying
    @Query("DELETE FROM DeviceChallenge deviceChallenge WHERE deviceChallenge.expiresAt <= :now")
    int deleteExpired(Instant now);
}
