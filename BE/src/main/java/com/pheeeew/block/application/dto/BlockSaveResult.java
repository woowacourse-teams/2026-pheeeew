package com.pheeeew.block.application.dto;

public record BlockSaveResult(BlockResult block, boolean created) {

    public static BlockSaveResult of(BlockResult block, boolean created) {
        return new BlockSaveResult(block, created);
    }
}
