package com.pheeeew.climb.application;

import com.pheeeew.climb.domain.ClimbErrorCode;
import com.pheeeew.climb.domain.ClimbException;

public enum ClimbBoard {

    ONLINE, TODAY, ALL;

    public static ClimbBoard from(String value) {
        for (ClimbBoard board : values()) {
            if (board.name().equalsIgnoreCase(value)) {
                return board;
            }
        }
        throw new ClimbException(ClimbErrorCode.INVALID_BOARD);
    }
}
