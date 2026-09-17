package com.pheeeew.sigh.application.dto;

import com.pheeeew.sigh.application.like.dto.SighLikeResult;

public record SighSaveResult(SighResult sigh, boolean created, SighLikeResult like) {

    public static SighSaveResult of(SighResult sigh, boolean created, SighLikeResult like) {
        return new SighSaveResult(sigh, created, like);
    }
}
