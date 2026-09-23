package com.pheeeew.groups.application.dto;

import com.pheeeew.groups.domain.GroupStamp;
import com.pheeeew.groups.domain.StampFrame;

public record GroupStampResult(String text, String textColor, String backgroundColor, StampFrame frame) {

    public static GroupStampResult from(GroupStamp stamp) {
        return new GroupStampResult(
                stamp.getText(),
                stamp.getTextColor(),
                stamp.getBackgroundColor(),
                stamp.getFrame()
        );
    }
}
