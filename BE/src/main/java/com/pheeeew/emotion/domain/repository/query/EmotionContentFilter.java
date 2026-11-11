package com.pheeeew.emotion.domain.repository.query;

public enum EmotionContentFilter {
    ALL,
    MEMO,
    AUDIO;

    /** 메모와 녹음은 함께 있을 수 없어 녹음 여부만으로 판정한다. */
    public boolean includes(boolean audio) {
        return switch (this) {
            case ALL -> true;
            case MEMO -> !audio;
            case AUDIO -> audio;
        };
    }
}
