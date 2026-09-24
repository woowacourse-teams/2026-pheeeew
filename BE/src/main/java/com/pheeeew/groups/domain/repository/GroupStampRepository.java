package com.pheeeew.groups.domain.repository;

import com.pheeeew.groups.domain.GroupStamp;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupStampRepository extends JpaRepository<GroupStamp, Long> {

    Optional<GroupStamp> findByGroupId(Long groupId);

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
