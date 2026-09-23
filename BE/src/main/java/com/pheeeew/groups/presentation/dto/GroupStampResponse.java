package com.pheeeew.groups.presentation.dto;

import com.pheeeew.groups.application.dto.GroupStampResult;
import com.pheeeew.groups.domain.StampFrame;

public record GroupStampResponse(String text, String textColor, String backgroundColor, StampFrame frame) {

    public static GroupStampResponse from(GroupStampResult result) {
        return new GroupStampResponse(
                result.text(),
                result.textColor(),
                result.backgroundColor(),
                result.frame()
        );
    }
}
