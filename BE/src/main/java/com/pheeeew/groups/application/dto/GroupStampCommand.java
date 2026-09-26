package com.pheeeew.groups.application.dto;

import com.pheeeew.groups.domain.StampFrame;

public record GroupStampCommand(String text, String textColor, String backgroundColor, StampFrame frame) {

    public static GroupStampCommand of(String text, String textColor, String backgroundColor, StampFrame frame) {
        return new GroupStampCommand(text, textColor, backgroundColor, frame);
    }
}
