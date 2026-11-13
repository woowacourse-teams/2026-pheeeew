package com.pheeeew.climb.stairs;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class StairsRepository {
    private final JdbcClient jdbc;
    public StairsRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    /** 한 SQL 문으로 판 저장과 최고 기록 갱신. 동시 제출도 PostgreSQL 행 잠금 아래 비교한다. */
    public void save(UUID guest, LocalDate day, String character, StairsRules.Score score) {
        jdbc.sql("""
                with saved as (
                    insert into climb_stairs_run (guest_id, day, character, height, time_ms)
                    values (:guest, :day, :character, :height, :time)
                    returning guest_id, day, character, height, time_ms, created_at
                )
                insert into climb_stairs_best (guest_id, day, character, height, time_ms, achieved_at)
                select guest_id, day, character, height, time_ms, created_at from saved
                on conflict (guest_id, day) do update set
                    character = excluded.character, height = excluded.height,
                    time_ms = excluded.time_ms, achieved_at = excluded.achieved_at
                where excluded.height > climb_stairs_best.height
                   or (excluded.height = climb_stairs_best.height and excluded.time_ms < climb_stairs_best.time_ms)
                """)
                .param("guest", guest).param("day", Date.valueOf(day)).param("character", character)
                .param("height", score.height()).param("time", score.timeMs()).update();
    }

    public Board ranking(LocalDate day, UUID guest) {
        List<Entry> rows = jdbc.sql("""
                with ranked as (
                    select row_number() over (order by b.height desc, b.time_ms asc, b.achieved_at asc, b.guest_id) as rank,
                           b.guest_id, g.tag, g.nickname, b.character, b.height, b.time_ms
                    from climb_stairs_best b join climb_guest g on g.id = b.guest_id
                    where b.day = :day
                )
                select * from ranked where rank <= 50 or guest_id = :guest order by rank
                """)
                .param("day", Date.valueOf(day)).param("guest", guest == null ? new UUID(0, 0) : guest)
                .query((rs, n) -> new Entry(rs.getInt("rank"), rs.getString("tag"), rs.getString("nickname"),
                        rs.getString("character"), rs.getInt("height"), rs.getInt("time_ms"),
                        rs.getObject("guest_id", UUID.class).equals(guest)))
                .list();
        return new Board(rows.stream().filter(r -> r.rank() <= 50).map(Entry::item).toList(),
                rows.stream().filter(Entry::mine).findFirst().map(Entry::item).orElse(null));
    }

    private record Entry(int rank, String tag, String nickname, String character, int height, int timeMs, boolean mine) {
        Item item() { return new Item(rank, tag, nickname, character, height, timeMs); }
    }
    public record Item(int rank, String tag, String nickname, String character, int height, int timeMs) { }
    public record Board(List<Item> top, Item me) { }
}
