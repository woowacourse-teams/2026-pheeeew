package com.pheeeew.groups.domain.repository;

import com.pheeeew.groups.domain.GroupStamp;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupStampRepository extends JpaRepository<GroupStamp, Long> {

    Optional<GroupStamp> findByGroupId(Long groupId);
}
