package com.pheeeew.climb.stairs;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

public class StairsRepository {
    private final JdbcClient jdbc;
    public StairsRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    /** 한 SQL 문으로 판 저장과 최고 기록 갱신. 동시 제출도 PostgreSQL 행 잠금 아래 비교한다. */
    public void save(String nickname, LocalDate day, String character, StairsRules.Score score) {
        jdbc.sql("""
                with saved as (
                    insert into climb_stairs_run (nickname, day, character, height, time_ms)
                    values (:nickname, :day, :character, :height, :time)
                    returning id, nickname, day, character, height, time_ms, created_at
                )
                insert into climb_stairs_best (nickname, day, character, height, time_ms, achieved_at, run_id)
                select nickname, day, character, height, time_ms, created_at, id from saved
                on conflict (nickname, day) do update set
                    character = excluded.character, height = excluded.height,
                    time_ms = excluded.time_ms, achieved_at = excluded.achieved_at, run_id = excluded.run_id
                where excluded.height > climb_stairs_best.height
                   or (excluded.height = climb_stairs_best.height and excluded.time_ms < climb_stairs_best.time_ms)
                """)
                .param("nickname", nickname).param("day", Date.valueOf(day)).param("character", character)
                .param("height", score.height()).param("time", score.timeMs()).update();
    }

    private static final String RANKED = """
            with ranked as (
                select row_number() over (order by height desc, time_ms, achieved_at, run_id) as rank,
                       nickname, character, height, time_ms
                from climb_stairs_best where day = :day
            )
            """;

    public Board ranking(LocalDate day) {
        return new Board(jdbc.sql(RANKED + "select * from ranked where rank <= 50 order by rank")
                .param("day", Date.valueOf(day)).query(StairsRepository::item).list());
    }

    public Item best(LocalDate day, String nickname) {
        return jdbc.sql(RANKED + "select * from ranked where nickname = :nickname")
                .param("day", Date.valueOf(day)).param("nickname", nickname)
                .query(StairsRepository::item).single();
    }

    private static Item item(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Item(rs.getInt("rank"), rs.getString("nickname"), rs.getString("character"),
                rs.getInt("height"), rs.getInt("time_ms"));
    }
    public record Item(int rank, String nickname, String character, int height, int timeMs) { }
    public record Board(List<Item> top) { }
}
