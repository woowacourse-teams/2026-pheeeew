package com.pheeeew.groups.domain.repository;

import com.pheeeew.groups.domain.GroupDailyPress;
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
                 VALUES (:groupId, :pressDate, :state, 1, :now, :now)
            ON CONFLICT (group_id, press_date, state) DO UPDATE
               SET press_count = group_daily_presses.press_count + 1,
                   updated_at = :now
            """, nativeQuery = true)
    void increase(
            @Param("groupId") Long groupId,
            @Param("pressDate") LocalDate pressDate,
            @Param("state") String state,
            @Param("now") Instant now
    );

    List<GroupDailyPress> findByGroupIdAndPressDate(Long groupId, LocalDate pressDate);
}
