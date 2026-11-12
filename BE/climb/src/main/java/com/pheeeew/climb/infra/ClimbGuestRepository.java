package com.pheeeew.climb.infra;

import com.pheeeew.climb.domain.ClimbGuest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class ClimbGuestRepository {

    private final JdbcClient jdbc;

    public ClimbGuestRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void save(ClimbGuest guest, byte[] tokenHash) {
        jdbc.sql("""
                        insert into climb_guest (id, tag, nickname, token_hash, created_at, last_seen_at)
                        values (:id, :tag, :nickname, :tokenHash, :createdAt, :lastSeenAt)
                        """)
                .param("id", guest.id())
                .param("tag", guest.tag())
                .param("nickname", guest.nickname())
                .param("tokenHash", tokenHash)
                .param("createdAt", Timestamp.from(guest.createdAt()))
                .param("lastSeenAt", Timestamp.from(guest.lastSeenAt()))
                .update();
    }

    public Optional<ClimbGuest> findByTokenHash(byte[] tokenHash) {
        return jdbc.sql("select id, tag, nickname, created_at, last_seen_at from climb_guest where token_hash = :hash")
                .param("hash", tokenHash)
                .query(ClimbGuestRepository::guest)
                .optional();
    }

    public void updateNickname(UUID id, String nickname) {
        jdbc.sql("update climb_guest set nickname = :nickname where id = :id")
                .param("id", id)
                .param("nickname", nickname)
                .update();
    }

    public void updateLastSeenAt(UUID id, Instant lastSeenAt) {
        jdbc.sql("update climb_guest set last_seen_at = :lastSeenAt where id = :id")
                .param("id", id)
                .param("lastSeenAt", Timestamp.from(lastSeenAt))
                .update();
    }

    /** 마지막 접속이 before 보다 오래된 손님과 그 기록을 지우고, 지운 손님 ID 를 돌려준다. */
    public List<UUID> deleteInactive(Instant before) {
        Timestamp cutoff = Timestamp.from(before);
        jdbc.sql("""
                        delete from climb_run
                        where guest_id in (select id from climb_guest where last_seen_at < :cutoff)
                        """)
                .param("cutoff", cutoff)
                .update();
        return jdbc.sql("delete from climb_guest where last_seen_at < :cutoff returning id")
                .param("cutoff", cutoff)
                .query(UUID.class)
                .list();
    }

    private static ClimbGuest guest(ResultSet rs, int row) throws SQLException {
        return ClimbGuest.of(rs.getObject("id", UUID.class), rs.getString("tag"), rs.getString("nickname"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("last_seen_at").toInstant());
    }
}
