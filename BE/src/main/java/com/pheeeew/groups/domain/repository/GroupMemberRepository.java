package com.pheeeew.groups.domain.repository;

import com.pheeeew.groups.domain.GroupMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    Optional<GroupMember> findByGroupIdAndDeviceIdAndLeftAtIsNull(Long groupId, Long deviceId);

    List<GroupMember> findByDeviceIdAndLeftAtIsNull(Long deviceId);

    long countByGroupIdAndLeftAtIsNull(Long groupId);
}
