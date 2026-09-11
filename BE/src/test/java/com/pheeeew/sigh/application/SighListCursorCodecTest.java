package com.pheeeew.sigh.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.sigh.application.dto.SighListCursor;
import com.pheeeew.sigh.application.dto.SighSearchBounds;
import com.pheeeew.sigh.exception.SighErrorCode;
import com.pheeeew.sigh.exception.SighException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SighListCursorCodecTest {

    @Test
    void 커서를_인코딩하고_디코딩하면_검색_조건을_복원한다() {
        // given
        SighListCursor cursor = SighListCursor.of(
                SighSearchBounds.of(126.9, 37.5, 127.1, 37.6),
                Instant.parse("2026-09-03T03:00:00.123456Z"),
                Instant.parse("2026-09-01T12:00:00.654321Z"),
                42L
        );

        // when
        String encoded = SighListCursorCodec.encode(cursor);
        SighListCursor decoded = SighListCursorCodec.decode(encoded);
        String payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);

        // then
        assertThat(payload).isEqualTo(
                "1|126.9|37.5|127.1|37.6"
                        + "|2026-09-03T03:00:00.123456Z|2026-09-01T12:00:00.654321Z|42"
        );
        assertThat(decoded).isEqualTo(cursor);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-cursor"})
    void 커서_형식이_올바르지_않으면_거부한다(String encoded) {
        // given / when / then
        잘못된_커서임을_검증한다(encoded);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2|126.9|37.5|127.1|37.6|2026-09-03T03:00:00Z|2026-09-01T12:00:00Z|42",
            "1|126.9|37.5|127.1|37.6|2026-09-03T03:00:00Z|2026-09-01T12:00:00Z",
            "1|126.9|37.5|126.9|37.6|2026-09-03T03:00:00Z|2026-09-01T12:00:00Z|42",
            "1|126.9|37.5|127.1|37.6|2026-09-01T12:00:00Z|2026-09-03T03:00:00Z|42",
            "1|126.9|37.5|127.1|37.6|2026-09-03T03:00:00Z|2026-09-01T12:00:00Z|0"
    })
    void 사용할_수_없는_내용을_가진_커서는_거부한다(String payload) {
        // given
        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        // when / then
        잘못된_커서임을_검증한다(encoded);
    }

    private void 잘못된_커서임을_검증한다(String encoded) {
        assertThatThrownBy(() -> SighListCursorCodec.decode(encoded))
                .isInstanceOf(SighException.class)
                .extracting(exception -> ((SighException) exception).getErrorCode())
                .isEqualTo(SighErrorCode.SIGH_INVALID_CURSOR);
    }
}
