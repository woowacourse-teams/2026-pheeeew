package com.pheeeew.groups.presentation.dto;

import com.pheeeew.groups.application.dto.GroupMemberResult;
import com.pheeeew.groups.domain.GroupRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record GroupMemberResponse(
        @Schema(description = "닉네임을 정하지 않은 기기는 서버가 익명 으로 채웁니다.", example = "한숨러")
        String nickname,
        GroupRole role,
        @Schema(description = "이 멤버가 그룹에 들어온 시각입니다. 목록 정렬 기준입니다.", example = "2026-10-11T09:00:00Z")
        Instant joinedAt
) {
    public static GroupMemberResponse from(GroupMemberResult result) {
        return new GroupMemberResponse(result.nickname(), result.role(), result.joinedAt());
    }
}
