package com.pheeeew.groups.domain.repository;

import com.pheeeew.groups.domain.GroupStamp;
import com.pheeeew.groups.domain.repository.projection.GroupStampProjection;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface GroupStampRepository extends JpaRepository<GroupStamp, Long> {

    Optional<GroupStamp> findByGroupId(Long groupId);

    @Query("""
            SELECT g.publicId AS groupPublicId,
                   g.name AS name,
                   stamp.text AS stampText,
                   stamp.textColor AS stampTextColor,
                   stamp.backgroundColor AS stampBackgroundColor,
                   stamp.frame AS stampFrame
            FROM GroupMember member
            JOIN member.group g
            JOIN GroupStamp stamp ON stamp.group = g
            WHERE member.device.id = :deviceId
              AND member.leftAt IS NULL
              AND g.deletedAt IS NULL
            ORDER BY member.createdAt, member.id
            """)
    List<GroupStampProjection> findMyStamps(@Param("deviceId") Long deviceId);

    @Query("SELECT stamp FROM GroupStamp stamp JOIN FETCH stamp.group WHERE stamp.id IN :ids")
    List<GroupStamp> findAllWithGroupByIdIn(List<Long> ids);

    @Query("""
            SELECT stamp FROM GroupStamp stamp JOIN stamp.group g
            WHERE g.publicId = :groupPublicId AND g.deletedAt IS NULL
              AND EXISTS (SELECT member.id FROM GroupMember member
                          WHERE member.group = g AND member.device.id = :deviceId AND member.leftAt IS NULL)
            """)
    Optional<GroupStamp> findAvailableByGroupPublicIdAndDeviceId(UUID groupPublicId, Long deviceId);
}
