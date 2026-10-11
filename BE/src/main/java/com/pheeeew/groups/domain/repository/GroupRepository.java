package com.pheeeew.groups.domain.repository;

import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.repository.projection.GroupExploreProjection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRepository extends JpaRepository<Group, Long> {

    Optional<Group> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    Optional<Group> findByInviteCodeAndDeletedAtIsNull(String inviteCode);

    boolean existsByNameAndDeletedAtIsNull(String name);

    boolean existsByInviteCode(String inviteCode);

    @Query(
            value = """
                    SELECT
                        g.public_id AS "groupPublicId",
                        g.name AS name,
                        g.description AS description,
                        g.join_type AS "joinType",
                        g.created_at AS "createdAt",
                        g.id AS id,
                        stamp.text AS "stampText",
                        stamp.text_color AS "stampTextColor",
                        stamp.background_color AS "stampBackgroundColor",
                        stamp.frame AS "stampFrame",
                        (SELECT COUNT(*)
                           FROM group_members active
                          WHERE active.group_id = g.id
                            AND active.left_at IS NULL) AS "memberCount",
                        EXISTS (SELECT 1
                                  FROM group_members mine
                                  JOIN devices device ON device.id = mine.device_id
                                 WHERE mine.group_id = g.id
                                   AND mine.left_at IS NULL
                                   AND device.public_id = CAST(:devicePublicId AS UUID)) AS joined
                    FROM groups g
                    JOIN group_stamps stamp ON stamp.group_id = g.id
                    WHERE g.deleted_at IS NULL
                      AND (g.created_at, g.id) < (:lastCreatedAt, :lastId)
                      AND (CAST(:keyword AS TEXT) IS NULL
                           OR POSITION(LOWER(CAST(:keyword AS TEXT)) IN LOWER(g.name)) > 0)
                    ORDER BY g.created_at DESC, g.id DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<GroupExploreProjection> findExplorePage(
            @Param("devicePublicId") UUID devicePublicId,
            @Param("keyword") String keyword,
            @Param("lastCreatedAt") Instant lastCreatedAt,
            @Param("lastId") long lastId,
            @Param("limit") int limit
    );
}
