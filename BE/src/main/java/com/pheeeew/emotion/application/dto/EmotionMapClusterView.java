package com.pheeeew.emotion.application.dto;

import com.pheeeew.emotion.domain.EmotionState;

/** cell은 H3 셀 번호의 16진 문자열이고 좌표는 셀 중심이다. 핀이 늘어도 표시 위치가 흔들리지 않는다. */
public record EmotionMapClusterView(String cell, double longitude, double latitude, long count,
                                    EmotionState representativeState) {

    public static EmotionMapClusterView of(String cell, double longitude, double latitude, long count,
            EmotionState representativeState) {
        return new EmotionMapClusterView(cell, longitude, latitude, count, representativeState);
    }
}
