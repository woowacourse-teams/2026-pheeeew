package com.pheeeew.block.application.dto;

import java.util.List;

public record BlockListResult(List<BlockResult> items, boolean hasNext, String nextCursor) {

    public static BlockListResult of(List<BlockResult> items, boolean hasNext, String nextCursor) {
        return new BlockListResult(items, hasNext, nextCursor);
    }
}
