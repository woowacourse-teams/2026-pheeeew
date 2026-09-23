package com.pheeeew.groups.domain.repository;

import com.pheeeew.groups.domain.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
}
