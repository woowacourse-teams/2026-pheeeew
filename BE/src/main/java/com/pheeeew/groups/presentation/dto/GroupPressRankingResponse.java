package com.pheeeew.groups.presentation.dto;

import com.pheeeew.groups.application.dto.GroupPressRankingItem;
import com.pheeeew.groups.application.dto.GroupPressRankingResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GroupPressRankingResponse(
        int weeksAgo,
        Instant startAt,
        Instant endAt,
        boolean hasPrevious,
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

    public record Item(
            int rank,
            UUID groupId,
            String name,
            GroupStampResponse stamp,
            long score,
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
