package com.pheeeew.climb.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ClimbAdminSessionsTest {

    private static final Instant T0 = Instant.parse("2026-11-12T00:00:00Z");
    private static final String KEY = "test-admin-key-1234";

    @Test
    void 맞는_키면_토큰을_주고_그_토큰은_여덟_시간_동안만_유효하다() {
        // given
        ClimbAdminSessions sessions = new ClimbAdminSessions(KEY);

        // when
        String token = sessions.open(KEY, "1.2.3.4", T0);

        // then
        assertThat(token).isNotBlank().isNotEqualTo(KEY);
        assertThat(sessions.open(KEY, "1.2.3.4", T0)).isNotEqualTo(token);
        assertThat(sessions.valid(token, T0.plus(Duration.ofHours(8)).minusSeconds(1))).isTrue();
        assertThat(sessions.valid(token, T0.plus(Duration.ofHours(8)))).isFalse();
    }

    @Test
    void 틀린_키는_길이가_달라도_비어_있어도_거절한다() {
        // given
        ClimbAdminSessions sessions = new ClimbAdminSessions(KEY);

        // when then
        for (String wrong : new String[] {"test-admin-key-1235", "test", KEY + "x", "", null}) {
            assertThat(catchThrowableOfType(ClimbException.class, () -> sessions.open(wrong, "ip-" + wrong, T0))
                    .code()).isEqualTo(ClimbErrorCode.ADMIN_KEY_MISMATCH);
        }
    }

    @Test
    void 틀린_시도가_분당_다섯_번을_넘으면_맞는_키도_막고_일_분_뒤에_풀린다() {
        // given
        ClimbAdminSessions sessions = new ClimbAdminSessions(KEY);
        for (int attempt = 0; attempt < 5; attempt++) {
            catchThrowableOfType(ClimbException.class, () -> sessions.open("wrong", "1.2.3.4", T0));
        }

        // when
        ClimbException limited = catchThrowableOfType(ClimbException.class,
                () -> sessions.open(KEY, "1.2.3.4", T0.plusSeconds(30)));

        // then
        assertThat(limited.code()).isEqualTo(ClimbErrorCode.ADMIN_ATTEMPTS_LIMITED);
        assertThat(sessions.open(KEY, "5.6.7.8", T0.plusSeconds(30))).isNotBlank();
        assertThat(sessions.open(KEY, "1.2.3.4", T0.plusSeconds(60))).isNotBlank();
    }

    @Test
    void 맞는_키의_시도는_한도에_세지_않는다() {
        // given
        ClimbAdminSessions sessions = new ClimbAdminSessions(KEY);

        // when
        for (int attempt = 0; attempt < 10; attempt++) {
            sessions.open(KEY, "1.2.3.4", T0);
        }

        // then
        assertThat(catchThrowableOfType(ClimbException.class, () -> sessions.open("wrong", "1.2.3.4", T0)).code())
                .isEqualTo(ClimbErrorCode.ADMIN_KEY_MISMATCH);
    }

    @Test
    void 닫은_토큰과_모르는_토큰은_유효하지_않다() {
        // given
        ClimbAdminSessions sessions = new ClimbAdminSessions(KEY);
        String token = sessions.open(KEY, "1.2.3.4", T0);

        // when
        sessions.close(token);

        // then
        assertThat(sessions.valid(token, T0)).isFalse();
        assertThat(sessions.valid("unknown", T0)).isFalse();
        assertThat(sessions.valid(null, T0)).isFalse();
    }

    @Test
    void 키가_비어_있으면_꺼져_있고_어떤_키도_받지_않는다() {
        // given
        ClimbAdminSessions blank = new ClimbAdminSessions("");
        ClimbAdminSessions missing = new ClimbAdminSessions(null);

        // then
        assertThat(blank.enabled()).isFalse();
        assertThat(missing.enabled()).isFalse();
        assertThat(new ClimbAdminSessions(KEY).enabled()).isTrue();
        assertThat(catchThrowableOfType(ClimbException.class, () -> blank.open("", "1.2.3.4", T0)).code())
                .isEqualTo(ClimbErrorCode.ADMIN_DISABLED);
    }
}
