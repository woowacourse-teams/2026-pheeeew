package com.pheeeew.groups.domain.repository.projection;

import java.time.Instant;

public interface GroupMemberProjection {

    String getNickname();

    String getRole();

    Instant getJoinedAt();

    long getId();
}
