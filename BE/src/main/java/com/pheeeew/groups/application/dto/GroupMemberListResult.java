package com.pheeeew.groups.application.dto;

import java.util.List;

public record GroupMemberListResult(List<GroupMemberResult> items, boolean hasNext, String nextCursor) {
    public static GroupMemberListResult of(
            List<GroupMemberResult> items,
            boolean hasNext,
            String nextCursor
    ) {
        return new GroupMemberListResult(items, hasNext, nextCursor);
    }
}
