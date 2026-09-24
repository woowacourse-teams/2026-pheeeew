package com.pheeeew.emotion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Embeddable
public class EmotionContent {

    private static final int MAX_MEMO_LENGTH = 200;

    @Column(length = MAX_MEMO_LENGTH, updatable = false)
    private String memo;

    @Embedded
    private Audio audio;

    @Builder
    private EmotionContent(String memo, Audio audio) {
        this.memo = normalizeMemo(memo);
        if (this.memo != null && audio != null) {
            throw new IllegalArgumentException("메모와 녹음은 함께 등록할 수 없습니다.");
        }
        this.audio = audio;
    }

    private String normalizeMemo(String memo) {
        if (memo == null) {
            return null;
        }

        String normalizedMemo = memo.strip();
        if (normalizedMemo.isEmpty()) {
            return null;
        }
        if (normalizedMemo.codePointCount(0, normalizedMemo.length()) > MAX_MEMO_LENGTH) {
            throw new IllegalArgumentException("메모는 200자를 초과할 수 없습니다.");
        }

        return normalizedMemo;
    }
}
