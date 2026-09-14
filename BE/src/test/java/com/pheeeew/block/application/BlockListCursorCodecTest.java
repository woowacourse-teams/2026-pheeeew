package com.pheeeew.block.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.block.exception.BlockErrorCode;
import com.pheeeew.block.exception.BlockException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BlockListCursorCodecTest {

    @Test
    void 커서를_인코딩하고_디코딩하면_마지막_차단_식별자를_복원한다() {
        // given
        long lastId = 42L;

        // when
        String encoded = BlockListCursorCodec.encode(lastId);
        long decoded = BlockListCursorCodec.decodeOrInitial(encoded);
        String payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);

        // then
        assertThat(payload).isEqualTo("1|42");
        assertThat(decoded).isEqualTo(lastId);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 커서가_없으면_첫_페이지로_읽는다(String encoded) {
        // given / when
        long decoded = BlockListCursorCodec.decodeOrInitial(encoded);

        // then
        assertThat(decoded).isEqualTo(Long.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2|42", "1", "1|42|7", "1|영", "1|0", "1|-1"})
    void 사용할_수_없는_내용을_가진_커서는_거부한다(String payload) {
        // given
        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        // when / then
        잘못된_커서임을_검증한다(encoded);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-cursor", "!!!"})
    void 커서_형식이_올바르지_않으면_거부한다(String encoded) {
        // given / when / then
        잘못된_커서임을_검증한다(encoded);
    }

    @Test
    void 너무_긴_커서는_디코딩하지_않고_거부한다() {
        // given
        String encoded = "A".repeat(129);

        // when / then
        잘못된_커서임을_검증한다(encoded);
    }

    private void 잘못된_커서임을_검증한다(String encoded) {
        assertThatThrownBy(() -> BlockListCursorCodec.decodeOrInitial(encoded))
                .isInstanceOf(BlockException.class)
                .hasMessage("차단 목록 커서를 사용할 수 없습니다.")
                .extracting(throwable -> ((BlockException) throwable).getErrorCode())
                .isEqualTo(BlockErrorCode.BLOCK_INVALID_CURSOR);
    }
}
