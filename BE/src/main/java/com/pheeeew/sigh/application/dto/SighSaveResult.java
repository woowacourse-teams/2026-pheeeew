package com.pheeeew.sigh.application.dto;

public record SighSaveResult(SighResult sigh, boolean created) {

    public static SighSaveResult of(SighResult sigh, boolean created) {
        return new SighSaveResult(sigh, created);
    }
}
