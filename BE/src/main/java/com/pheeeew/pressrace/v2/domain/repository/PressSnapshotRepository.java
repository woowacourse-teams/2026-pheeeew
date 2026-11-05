package com.pheeeew.pressrace.v2.domain.repository;

import com.pheeeew.pressrace.v2.domain.PressParticipant;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 프레스 입력이 기록한 press_daily_scores 를 읽기만 한다. */
@RequiredArgsConstructor
@Repository
public class PressSnapshotRepository {

    private final JdbcClient jdbc;

    /** last_applied_at 과 같은 DB 시계로 활동 창을 판정한다. */
    public Instant findNow() {
        return jdbc.sql("SELECT clock_timestamp()")
                .query(OffsetDateTime.class)
                .single()
                .toInstant();
    }

    public List<PressParticipant> findParticipants(LocalDate pressDate) {
        return jdbc.sql("""
                        SELECT d.public_id AS device_public_id, s.public_id, d.nickname, s.total, s.last_applied_at
                        FROM press_daily_scores s
                        JOIN devices d ON d.id = s.device_id
                        WHERE s.press_date = :pressDate
                        """)
                .param("pressDate", pressDate)
                .query((row, index) -> PressParticipant.of(
                        row.getObject("device_public_id", UUID.class),
                        row.getObject("public_id", UUID.class),
                        row.getString("nickname"),
                        row.getLong("total"),
                        row.getObject("last_applied_at", OffsetDateTime.class).toInstant()
                ))
                .list();
    }
}
