package com.pheeeew.emotion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmotionContentTest {

    @Test
    void 메모와_녹음이_모두_없어도_생성한다() {
        EmotionContent content = EmotionContent.builder().build();

        assertThat(content.getMemo()).isNull();
        assertThat(content.getAudio()).isNull();
    }

    @Test
    void 메모의_앞뒤_공백을_제거한다() {
        EmotionContent content = EmotionContent.builder().memo("  오늘은 힘들었다  ").build();

        assertThat(content.getMemo()).isEqualTo("오늘은 힘들었다");
        assertThat(content.getAudio()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void 비어_있는_메모는_내용_없음으로_처리하며_녹음과_함께_전달할_수_있다(String memo) {
        // given
        Audio audio = Audio.builder().objectKey("recordings/voice.m4a").build();

        // when
        EmotionContent empty = EmotionContent.builder().memo(memo).build();
        EmotionContent recorded = EmotionContent.builder().memo(memo).audio(audio).build();

        // then
        assertThat(empty.getMemo()).isNull();
        assertThat(empty.getAudio()).isNull();
        assertThat(recorded.getMemo()).isNull();
        assertThat(recorded.getAudio()).isEqualTo(audio);
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 메모는_유니코드_코드포인트_기준_200자까지_허용한다(String character) {
        String memo = character.repeat(200);

        EmotionContent content = EmotionContent.builder().memo("  " + memo + "  ").build();

        assertThat(content.getMemo()).isEqualTo(memo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 메모는_200자를_초과할_수_없다(String character) {
        assertThatThrownBy(() -> EmotionContent.builder().memo(character.repeat(201)).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("메모는 200자를 초과할 수 없습니다.");
    }

    @Test
    void 메모와_녹음을_함께_등록할_수_없다() {
        Audio audio = Audio.builder().objectKey("recordings/voice.m4a").build();

        assertThatThrownBy(() -> EmotionContent.builder().memo("메모").audio(audio).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("메모와 녹음은 함께 등록할 수 없습니다.");
    }
}
