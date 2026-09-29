package com.pheeeew.emotion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AudioTest {

    @Test
    void 녹음_파일_키를_변형하지_않는다() {
        // given
        String objectKey = "recordings/기기/음성 기록.m4a";

        // when
        Audio audio = Audio.builder().objectKey(objectKey).build();

        // then
        assertThat(audio.getObjectKey()).isEqualTo(objectKey);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void 녹음_파일_키는_비어_있을_수_없다(String objectKey) {
        assertThatThrownBy(() -> Audio.builder().objectKey(objectKey).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("녹음 파일 키는 비어 있을 수 없습니다.");
    }
}
