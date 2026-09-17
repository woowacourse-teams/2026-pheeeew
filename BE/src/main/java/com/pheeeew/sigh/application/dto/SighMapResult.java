package com.pheeeew.sigh.application.dto;

import java.util.List;

public record SighMapResult(List<SighMapItem> sighs, boolean truncated) {

    public static SighMapResult of(List<SighMapItem> sighs, boolean truncated) {
        return new SighMapResult(sighs, truncated);
    }
}
