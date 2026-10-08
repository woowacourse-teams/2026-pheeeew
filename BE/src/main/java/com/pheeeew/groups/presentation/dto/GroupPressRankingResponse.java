package com.pheeeew.groups.presentation.dto;

import com.pheeeew.groups.application.dto.GroupPressRankingItem;
import com.pheeeew.groups.application.dto.GroupPressRankingResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GroupPressRankingResponse(
        @Schema(description = "요청한 몇 주 전. 0 이 이번 주입니다.", example = "0")
        int weeksAgo,
        @Schema(description = "집계 구간 시작. 월요일 00:00 KST 입니다.", example = "2026-10-05T15:00:00Z")
        Instant startAt,
        @Schema(description = "집계 구간 끝. 이 시각은 포함하지 않습니다.", example = "2026-10-12T15:00:00Z")
        Instant endAt,
        @Schema(
                description = "이 구간보다 이전에 누른 기록이 있는지. false 면 더 과거를 조회할 필요가 없습니다.",
                example = "true"
        )
        boolean hasPrevious,
        @Schema(description = "점수 내림차순. 그 주에 한 번도 누르지 않은 그룹은 담기지 않습니다.")
        List<Item> items
) {
    public static GroupPressRankingResponse from(GroupPressRankingResult result) {
        return new GroupPressRankingResponse(
                result.weeksAgo(),
                result.startAt(),
                result.endAt(),
                result.hasPrevious(),
                result.items().stream().map(Item::from).toList()
        );
    }

    @Schema(name = "GroupPressRankingItem", description = "프레스 랭킹 항목")
    public record Item(
            @Schema(description = "동점은 공동 순위입니다. 1, 2, 2, 4 로 매깁니다.", example = "1")
            int rank,
            @Schema(description = "그룹 공개 식별자", example = "5f2b1c84-9d0e-4a13-b6c7-2e8f0a4d7b19")
            UUID groupId,
            @Schema(example = "한숨모임")
            String name,
            GroupStampResponse stamp,
            @Schema(
                    description = "이 랭킹의 점수. 어느 원천을 세는지는 엔드포인트마다 다릅니다. "
                            + "/press-rankings 는 그룹 화면 감정 버튼 수, "
                            + "/press-rankings/members 는 현재 멤버들의 개인 감정 프레스 합입니다. "
                            + "두 값은 섞이지 않으며 같은 그룹이라도 다릅니다.",
                    example = "1204"
            )
            long score,
            @Schema(description = "요청한 기기가 이 그룹의 멤버인지. 나간 그룹은 false 입니다.", example = "true")
            boolean mine
    ) {
        public static Item from(GroupPressRankingItem item) {
            return new Item(
                    item.rank(),
                    item.groupPublicId(),
                    item.name(),
                    GroupStampResponse.from(item.stamp()),
                    item.score(),
                    item.mine()
            );
        }
    }
}
