package com.pheeeew.groups.presentation.dto;

import com.pheeeew.groups.application.dto.GroupExploreResult;
import com.pheeeew.groups.domain.GroupJoinType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record GroupExploreResponse(
        @Schema(description = "그룹 공개 식별자", example = "5f2b1c84-9d0e-4a13-b6c7-2e8f0a4d7b19")
        UUID groupId,
        @Schema(example = "한숨모임")
        String name,
        @Schema(description = "없으면 null 입니다.", nullable = true, example = "퇴근하고 한숨 쉬는 모임")
        String description,
        GroupStampResponse stamp,
        @Schema(description = "지금 속해 있는 멤버 수입니다. 나간 멤버는 세지 않습니다.", example = "7")
        long memberCount,
        @Schema(
                description = "가입 방식. PUBLIC 이면 POST /api/v2/groups/{groupId}/members/me 로 코드 없이 가입하고, "
                        + "PRIVATE 이면 POST /api/v2/groups/join 에 inviteCode 를 담아 가입합니다. "
                        + "비공개 그룹도 이 목록에 나옵니다.",
                example = "PUBLIC"
        )
        GroupJoinType joinType,
        @Schema(
                description = "요청한 기기가 이미 이 그룹에 속해 있는지입니다. 가입하기 버튼을 가리는 데 씁니다. "
                        + "나간 그룹은 false 입니다.",
                example = "false"
        )
        boolean joined
) {
    public static GroupExploreResponse from(GroupExploreResult result) {
        return new GroupExploreResponse(
                result.publicId(),
                result.name(),
                result.description(),
                GroupStampResponse.from(result.stamp()),
                result.memberCount(),
                result.joinType(),
                result.joined()
        );
    }
}
