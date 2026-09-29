package com.pheeeew.groups.domain.repository;

import com.pheeeew.groups.domain.Group;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<Group, Long> {

    Optional<Group> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    Optional<Group> findByInviteCodeAndDeletedAtIsNull(String inviteCode);

    boolean existsByNameAndDeletedAtIsNull(String name);

    boolean existsByInviteCode(String inviteCode);
}
