package com.pheeeew.notification.domain.outbox;

import static com.pheeeew.notification.fixture.NotificationOutboxFixture.순위_작업;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.notification.domain.PushMessage;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RankDroppedOutboxTest {

    private static final ZoneId 서울 = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Instant 회차 = LocalDateTime.parse("2026-10-26T10:05:00").atZone(서울).toInstant();
    private static final Instant 만료 = 회차.plus(Duration.ofMinutes(5));

    @ParameterizedTest
    @CsvSource({"2, 2", "3, 2", "0, 1"})
    void 이전_순위보다_낮아지지_않았거나_이전_순위가_없으면_순위_하락_작업을_만들지_않는다(int 이전_순위, int 현재_순위) {
        // when / then
        assertThatThrownBy(() -> 순위_작업(1L, TODAY, 회차, 이전_순위, 현재_순위, 회차, 만료))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 회차가_없으면_순위_하락_작업을_만들지_않는다() {
        // when / then
        assertThatThrownBy(() -> 순위_작업(1L, TODAY, null, 1, 2, 회차, 만료))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 순위_하락_메시지는_이전_순위와_현재_순위를_담는다() {
        // given
        RankDroppedOutbox outbox = 순위_작업(1L, TODAY, 회차, 5, 6, 회차, 만료);

        // when
        PushMessage message = outbox.message();

        // then
        assertThat(outbox.type()).isEqualTo(NotificationOutboxType.RANK_DROPPED);
        assertThat(message.title()).isEqualTo("앗, 프레스 순위가 내려갔어요👀");
        assertThat(message.body()).isEqualTo("오늘 내 순위 5위 → 6위!\n쌓인 마음으로 순위를 올리러 갈까요?");
        assertThat(message.data()).isEqualTo(Map.of(
                "type", "RANK_DROPPED", "date", "2026-10-26", "previousRank", "5", "currentRank", "6"));
    }
}
