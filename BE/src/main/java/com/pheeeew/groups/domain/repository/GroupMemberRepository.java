package com.pheeeew.groups.domain.repository;

import com.pheeeew.groups.domain.GroupMember;
import com.pheeeew.groups.domain.repository.projection.GroupListProjection;
import java.util.List;
import java.util.Optional;
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
            GROUP BY g.publicId, g.name, g.description, g.inviteCode, member.role,
                     stamp.text, stamp.textColor, stamp.backgroundColor, stamp.frame,
                     member.createdAt, member.id
            ORDER BY member.createdAt, member.id
            """)
    List<GroupListProjection> findMine(@Param("deviceId") Long deviceId);

    long countByGroupIdAndLeftAtIsNull(Long groupId);
}
