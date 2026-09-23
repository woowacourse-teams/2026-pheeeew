package com.pheeeew.groups.domain.repository;

import com.pheeeew.groups.domain.Group;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<Group, Long> {
}
