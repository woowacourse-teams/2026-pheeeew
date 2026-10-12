package com.pheeeew.groups.domain.repository;

import com.pheeeew.groups.domain.GroupMember;
import com.pheeeew.groups.domain.repository.projection.GroupListProjection;
import com.pheeeew.groups.domain.repository.projection.GroupMemberProjection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    Optional<GroupMember> findByGroupIdAndDeviceIdAndLeftAtIsNull(Long groupId, Long deviceId);

    @Query("""
            SELECT g.publicId AS groupPublicId,
                   g.name AS name,
                   g.description AS description,
                   g.inviteCode AS inviteCode,
                   g.joinType AS joinType,
                   member.role AS role,
                   COUNT(activeMember.id) AS memberCount,
                   stamp.text AS stampText,
                   stamp.textColor AS stampTextColor,
                   stamp.backgroundColor AS stampBackgroundColor,
                   stamp.frame AS stampFrame
            FROM GroupMember member
            JOIN member.group g
            LEFT JOIN GroupStamp stamp ON stamp.group = g
            JOIN GroupMember activeMember ON activeMember.group = g AND activeMember.leftAt IS NULL
            WHERE member.device.id = :deviceId
              AND member.leftAt IS NULL
              AND g.deletedAt IS NULL
            GROUP BY g.publicId, g.name, g.description, g.inviteCode, g.joinType, member.role,
                     stamp.text, stamp.textColor, stamp.backgroundColor, stamp.frame,
                     member.createdAt, member.id
            ORDER BY member.createdAt, member.id
            """)
    List<GroupListProjection> findMine(@Param("deviceId") Long deviceId);

    @Query("""
            SELECT g.publicId
            FROM GroupMember member
            JOIN member.group g
            WHERE member.device.publicId = :devicePublicId
              AND member.leftAt IS NULL
              AND g.deletedAt IS NULL
            """)
    List<UUID> findMyGroupPublicIds(@Param("devicePublicId") UUID devicePublicId);

    @Query(
            value = """
                    SELECT
                        COALESCE(device.nickname, '익명') AS nickname,
                        member.role AS role,
                        member.created_at AS "joinedAt",
                        member.id AS id
                    FROM group_members member
                    JOIN devices device ON device.id = member.device_id
                    WHERE member.group_id = :groupId
                      AND member.left_at IS NULL
                      AND (member.created_at, member.id) > (:lastCreatedAt, :lastId)
                    ORDER BY member.created_at, member.id
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<GroupMemberProjection> findMemberPage(
            @Param("groupId") Long groupId,
            @Param("lastCreatedAt") Instant lastCreatedAt,
            @Param("lastId") long lastId,
            @Param("limit") int limit
    );

    long countByGroupIdAndLeftAtIsNull(Long groupId);
}
