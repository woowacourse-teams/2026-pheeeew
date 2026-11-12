package com.pheeeew.climb.infra;

import com.pheeeew.climb.domain.ClimbHallEntry;
import com.pheeeew.climb.domain.ClimbRankEntry;
import com.pheeeew.climb.domain.ClimbRunRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class ClimbRunRepository {

    private static final String COLUMNS = """
            id, guest_id, map_version, "character", best_height, best_at, height, x, falls, play_seconds,
            finished_at, finish_seconds, suspect, started_at, updated_at""";

    private final JdbcClient jdbc;

    public ClimbRunRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void save(ClimbRunRecord run) {
        jdbc.sql("insert into climb_run (" + COLUMNS + """
                        ) values (:id, :guestId, :mapVersion, :character, :best, :bestAt, :height, :x, :falls,
                                  :playSeconds, :finishedAt, :finishSeconds, :suspect, :startedAt, :updatedAt)
                        on conflict (id) do update set
                            "character" = excluded."character", best_height = excluded.best_height,
                            best_at = excluded.best_at, height = excluded.height, x = excluded.x,
                            falls = excluded.falls, play_seconds = excluded.play_seconds,
                            finished_at = excluded.finished_at, finish_seconds = excluded.finish_seconds,
                            suspect = excluded.suspect, updated_at = excluded.updated_at
                        """)
                .param("id", run.id())
                .param("guestId", run.guestId())
                .param("mapVersion", run.mapVersion())
                .param("character", run.character())
                .param("best", run.best())
                .param("bestAt", timestamp(run.bestAt()))
                .param("height", run.height())
                .param("x", run.x())
                .param("falls", run.falls())
                .param("playSeconds", run.playSeconds())
                .param("finishedAt", timestamp(run.finishedAt()))
                .param("finishSeconds", run.finishSeconds())
                .param("suspect", run.suspect())
                .param("startedAt", timestamp(run.startedAt()))
                .param("updatedAt", timestamp(run.updatedAt()))
                .update();
    }

    /** 이어하기 후보: 손님의 가장 최근 등반 중 완주하지 않은 것. */
    public Optional<ClimbRunRecord> findLatestUnfinished(UUID guestId) {
        return jdbc.sql("select " + COLUMNS + """
                         from climb_run where guest_id = :guestId and finished_at is null
                        order by updated_at desc limit 1
                        """)
                .param("guestId", guestId)
                .query(ClimbRunRepository::run)
                .optional();
    }

    public Optional<ClimbRunRecord> findLatestUnfinished(UUID guestId, String mapVersion) {
        return jdbc.sql("select " + COLUMNS + """
                         from climb_run where guest_id = :guestId and map_version = :map and finished_at is null
                        order by updated_at desc limit 1
                        """)
                .param("guestId", guestId)
                .param("map", mapVersion)
                .query(ClimbRunRepository::run)
                .optional();
    }

    /** 순위에 올릴 수 있는 기록: 의심 표시가 없고 한 칸이라도 오른 등반. */
    public List<Ranked> findRankable(Collection<String> mapVersions) {
        if (mapVersions.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        select r.id, r.guest_id, r.map_version, r."character", r.best_height, r.best_at,
                               r.finish_seconds, g.tag, g.nickname
                        from climb_run r join climb_guest g on g.id = r.guest_id
                        where not r.suspect and r.best_at is not null and r.map_version in (:maps)
                        """)
                .param("maps", mapVersions)
                .query((rs, row) -> new Ranked(rs.getString("map_version"), ClimbRankEntry.of(
                        rs.getObject("id", UUID.class), rs.getObject("guest_id", UUID.class), rs.getString("tag"),
                        rs.getString("nickname"), rs.getString("character"), rs.getInt("best_height"),
                        rs.getTimestamp("best_at").toInstant(), rs.getObject("finish_seconds", Integer.class))))
                .list();
    }

    /**
     * 손님·맵마다 첫 완주(의심 제외)를 명예의 전당에 옮긴다. 이미 있으면 그대로 둔다. 마이그레이션 V20261112_91 과 같은 규칙이다.
     * 완주마다, 그리고 1분마다 부른다. 배포 중 옛 서버가 받은 완주도 이렇게 뒤늦게 들어온다(순서는 finished_at 이라 그대로다).
     */
    public void syncHall() {
        jdbc.sql("""
                        insert into climb_hall (run_id, guest_id, map_version, tag, nickname, "character", finish_seconds,
                                                falls, finished_at)
                        select distinct on (r.guest_id, r.map_version)
                               r.id, r.guest_id, r.map_version, g.tag, g.nickname, r."character", r.finish_seconds,
                               r.falls, r.finished_at
                        from climb_run r join climb_guest g on g.id = r.guest_id
                        where r.finished_at is not null and r.finish_seconds is not null and not r.suspect
                        order by r.guest_id, r.map_version, r.finished_at
                        on conflict do nothing
                        """)
                .update();
    }

    /** 먼저 도착한 순서. */
    public List<ClimbHallEntry> findHall(String mapVersion, int limit) {
        return jdbc.sql("""
                        select tag, nickname, "character", finish_seconds, falls, finished_at from climb_hall
                        where map_version = :map order by finished_at, run_id limit :limit
                        """)
                .param("map", mapVersion)
                .param("limit", limit)
                .query((rs, row) -> new ClimbHallEntry(rs.getString("tag"), rs.getString("nickname"),
                        rs.getString("character"), rs.getInt("finish_seconds"), rs.getInt("falls"),
                        rs.getTimestamp("finished_at").toInstant()))
                .list();
    }

    public record Ranked(String mapVersion, ClimbRankEntry entry) {
    }

    private static ClimbRunRecord run(ResultSet rs, int row) throws SQLException {
        return ClimbRunRecord.of(rs.getObject("id", UUID.class), rs.getObject("guest_id", UUID.class),
                rs.getString("map_version"), rs.getString("character"), rs.getInt("best_height"),
                instant(rs.getTimestamp("best_at")), rs.getInt("height"), rs.getInt("x"), rs.getInt("falls"),
                rs.getInt("play_seconds"), instant(rs.getTimestamp("finished_at")),
                rs.getObject("finish_seconds", Integer.class), rs.getBoolean("suspect"),
                instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("updated_at")));
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
