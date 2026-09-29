package com.pheeeew.groups.presentation.dto;

import com.pheeeew.groups.application.dto.GroupRankingItem;
import com.pheeeew.groups.application.dto.GroupRankingResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GroupRankingResponse(
        int weeksAgo,
        Instant startAt,
        Instant endAt,
        boolean hasPrevious,
        List<Item> items
) {

    public static GroupRankingResponse from(GroupRankingResult result) {
        return new GroupRankingResponse(
                result.weeksAgo(),
                result.startAt(),
                result.endAt(),
                result.hasPrevious(),
                result.items().stream().map(Item::from).toList()
        );
    }

    public record Item(int rank, UUID groupId, String name, GroupStampResponse stamp, long score) {

        public static Item from(GroupRankingItem item) {
            return new Item(
                    item.rank(),
                    item.groupPublicId(),
                    item.name(),
                    GroupStampResponse.from(item.stamp()),
                    item.score()
            );
        }
    }
}
