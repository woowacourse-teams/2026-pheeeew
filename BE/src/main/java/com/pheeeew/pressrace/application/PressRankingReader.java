package com.pheeeew.pressrace.application;

import com.pheeeew.pressrace.domain.PressRanking;
import com.pheeeew.pressrace.exception.PressRaceException;
import com.pheeeew.pressrace.infra.PressRaceRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PressRankingReader {

    private final JdbcClient jdbc;
    private final PressRaceService service;
    private final TransactionTemplate transaction;

    public PressRankingReader(JdbcClient jdbc, PressRaceService service, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.service = service;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.transaction.setReadOnly(true);
    }

    public PressRanking read() {
        Observation observation = transaction.execute(status -> {
            var control = jdbc.sql("SELECT *, clock_timestamp() AS observed_at FROM press_race_control WHERE id = TRUE")
                    .query().singleRow();
            Instant now = PressRaceRepository.instant(control.get("observed_at"));
            LocalDate date = LocalDate.ofInstant(now, ZoneId.of("Asia/Seoul"));
            if (!service.serverGeneration().equals(control.get("active_server_generation"))) {
                throw new PressRaceException("SERVER_REPLACED");
            }
            if (!Boolean.TRUE.equals(control.get("enabled")) || control.get("activation_date") == null
                    || date.isBefore(((java.sql.Date) control.get("activation_date")).toLocalDate())) {
                throw new PressRaceException("RACE_DISABLED");
            }
            List<PressRanking.Participant> participants = jdbc.sql("""
                    SELECT d.public_id AS device_id, s.public_id, COALESCE(d.nickname, '익명') AS nickname,
                           s.total, s.last_applied_at
                    FROM press_daily_scores s JOIN devices d ON d.id = s.device_id
                    WHERE s.press_date = :date
                    """).param("date", date).query((row, index) -> PressRanking.Participant.of(
                            row.getObject("device_id", UUID.class), row.getObject("public_id", UUID.class),
                            row.getString("nickname"), row.getLong("total"),
                            row.getObject("last_applied_at", OffsetDateTime.class).toInstant())).list();
            return Observation.of(date, now, participants);
        });
        // 정렬·개인화·송신 동안 DB 관찰 트랜잭션을 유지하지 않는다.
        return PressRanking.of(observation.date(), observation.now(), observation.participants());
    }

    private record Observation(LocalDate date, Instant now, List<PressRanking.Participant> participants) {
        private static Observation of(LocalDate date, Instant now, List<PressRanking.Participant> participants) {
            return new Observation(date, now, participants);
        }
    }
}
