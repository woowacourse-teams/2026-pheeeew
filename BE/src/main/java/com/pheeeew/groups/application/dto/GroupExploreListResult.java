package com.pheeeew.groups.application.dto;

import java.util.List;

public record GroupExploreListResult(List<GroupExploreResult> items, boolean hasNext, String nextCursor) {
    public static GroupExploreListResult of(
            List<GroupExploreResult> items,
            boolean hasNext,
            String nextCursor
    ) {
        return new GroupExploreListResult(items, hasNext, nextCursor);
    }
}
