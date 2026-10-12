package com.pheeeew.groups.application.dto;

import com.pheeeew.groups.domain.GroupRole;
import com.pheeeew.groups.domain.repository.projection.GroupMemberProjection;
import java.time.Instant;

public record GroupMemberResult(String nickname, GroupRole role, Instant joinedAt, long id) {
    public static GroupMemberResult from(GroupMemberProjection projection) {
        return new GroupMemberResult(
                projection.getNickname(),
                GroupRole.valueOf(projection.getRole()),
                projection.getJoinedAt(),
                projection.getId()
        );
    }
}
