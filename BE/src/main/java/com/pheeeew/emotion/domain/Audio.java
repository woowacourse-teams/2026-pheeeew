package com.pheeeew.emotion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Embeddable
public class Audio {

    @Column(name = "audio_object_key", columnDefinition = "TEXT")
    private String objectKey;

    @Builder
    private Audio(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("녹음 파일 키는 비어 있을 수 없습니다.");
        }
        this.objectKey = objectKey;
    }
}
