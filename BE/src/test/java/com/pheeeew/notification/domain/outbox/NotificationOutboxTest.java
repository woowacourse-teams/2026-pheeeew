package com.pheeeew.notification.domain.outbox;

import static com.pheeeew.notification.fixture.NotificationOutboxFixture.리마인더_작업;
import static com.pheeeew.notification.fixture.NotificationOutboxFixture.발급_가능_작업;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationOutboxTest {

    private static final ZoneId 서울 = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Instant 생성 = 한국_시각("2026-10-26T23:00:00");
    private static final Instant 자정 = 한국_시각("2026-10-27T00:00:00");
    private static final Duration 일분 = Duration.ofMinutes(1);
    private static final long 기기 = 1L;

    @Test
    void 처음_만든_작업은_선점_없이_대기한다() {
        // given / when
        CollectionReminderOutbox outbox = 대기_작업();

        // then
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
        assertThat(outbox.getAttemptCount()).isZero();
        assertThat(outbox.getClaimToken()).isNull();
        assertThat(outbox.getClaimExpiresAt()).isNull();
        assertThat(outbox.getResultCode()).isNull();
        assertThat(outbox.getFinishedAt()).isNull();
    }

    @Test
    void 오후_11시_이후_발견해_예정_시각이_자정_뒤인_발급_가능_작업도_만들_수_있다() {
        // given
        Instant 발견 = 한국_시각("2026-10-26T23:30:00");

        // when
        CollectionClaimableOutbox outbox = 발급_가능_작업(기기, TODAY, 발견, 발견.plus(Duration.ofHours(1)), 자정);

        // then
        assertThat(outbox.getAvailableAt()).isAfter(outbox.getExpiresAt());
    }

    @Test
    void 예정_시각이_발견_시각보다_앞서거나_만료_시각이_발견_시각보다_뒤가_아니면_만들지_않는다() {
        // when / then
        assertThatThrownBy(() -> 발급_가능_작업(기기, TODAY, 생성, 생성.minusMillis(1), 자정))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> 발급_가능_작업(기기, TODAY, 생성, 생성, 생성))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> 발급_가능_작업(0L, TODAY, 생성, 생성, 자정))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> 발급_가능_작업(null, TODAY, 생성, 생성, 자정))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 예정_시각이_된_작업을_선점하면_토큰과_선점_기한을_남기고_시도_횟수를_올린다() {
        // given
        CollectionReminderOutbox outbox = 대기_작업();
        UUID 선점 = UUID.randomUUID();

        // when
        outbox.claim(선점, 생성.plus(일분), 생성);

        // then
        assertThat(outbox.getClaimToken()).isEqualTo(선점);
        assertThat(outbox.getClaimExpiresAt()).isEqualTo(생성.plus(일분));
        assertThat(outbox.getAttemptCount()).isOne();
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
    }

    @Test
    void 예정_시각_전이나_만료_시각부터는_선점할_수_없다() {
        // given
        CollectionReminderOutbox outbox = 대기_작업();

        // when / then
        assertThatThrownBy(() -> outbox.claim(UUID.randomUUID(), 생성.plus(일분), 생성.minusMillis(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> outbox.claim(UUID.randomUUID(), 자정.plus(일분), 자정))
                .isInstanceOf(IllegalStateException.class);
        assertThat(outbox.getAttemptCount()).isZero();
        assertThat(outbox.getClaimToken()).isNull();
    }

    @Test
    void 선점_기한이_남은_작업은_다시_선점할_수_없고_기한과_같은_시각부터_다시_선점한다() {
        // given
        CollectionReminderOutbox outbox = 대기_작업();
        UUID 처음_선점 = UUID.randomUUID();
        outbox.claim(처음_선점, 생성.plus(일분), 생성);
        UUID 다시_선점 = UUID.randomUUID();
        Instant 기한 = 생성.plus(일분);

        // when
        assertThatThrownBy(() -> outbox.claim(다시_선점, 기한.plus(일분), 기한.minusMillis(1)))
                .isInstanceOf(IllegalStateException.class);
        outbox.claim(다시_선점, 기한.plus(일분), 기한);

        // then
        assertThat(outbox.getClaimToken()).isEqualTo(다시_선점);
        assertThat(outbox.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void 선점_기한이_현재_시각보다_뒤가_아니거나_토큰이_없으면_선점하지_않는다() {
        // given
        CollectionReminderOutbox outbox = 대기_작업();

        // when / then
        assertThatThrownBy(() -> outbox.claim(UUID.randomUUID(), 생성, 생성))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> outbox.claim(null, 생성.plus(일분), 생성))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(outbox.getAttemptCount()).isZero();
    }

    @Test
    void 선점한_토큰으로_발송_완료를_기록하면_SENT로_닫고_선점을_푼다() {
        // given
        CollectionReminderOutbox outbox = 대기_작업();
        UUID 선점 = 선점한다(outbox);

        // when
        outbox.markSent(선점, 생성.plusSeconds(5));

        // then
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.SENT);
        assertThat(outbox.getResultCode()).isNull();
        assertThat(outbox.getFinishedAt()).isEqualTo(생성.plusSeconds(5));
        assertThat(outbox.getClaimToken()).isNull();
        assertThat(outbox.getClaimExpiresAt()).isNull();
    }

    @Test
    void 선점한_토큰으로_취소하면_CANCELED와_사유로_닫고_선점을_푼다() {
        // given
        CollectionReminderOutbox outbox = 대기_작업();
        UUID 선점 = 선점한다(outbox);

        // when
        outbox.cancel(선점, "NO_TOKEN", 생성.plusSeconds(5));

        // then
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.CANCELED);
        assertThat(outbox.getResultCode()).isEqualTo("NO_TOKEN");
        assertThat(outbox.getFinishedAt()).isEqualTo(생성.plusSeconds(5));
        assertThat(outbox.getClaimToken()).isNull();
        assertThat(outbox.getClaimExpiresAt()).isNull();
    }

    @Test
    void 선점한_토큰으로_실패를_기록하면_FAILED와_사유로_닫고_선점을_푼다() {
        // given
        CollectionReminderOutbox outbox = 대기_작업();
        UUID 선점 = 선점한다(outbox);

        // when
        outbox.fail(선점, "REJECTED", 생성.plusSeconds(5));

        // then
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.FAILED);
        assertThat(outbox.getResultCode()).isEqualTo("REJECTED");
        assertThat(outbox.getFinishedAt()).isEqualTo(생성.plusSeconds(5));
        assertThat(outbox.getClaimToken()).isNull();
        assertThat(outbox.getClaimExpiresAt()).isNull();
    }

    @Test
    void 다른_토큰이나_빈_토큰으로는_결과를_기록할_수_없고_작업은_그대로다() {
        // given
        CollectionReminderOutbox outbox = 대기_작업();
        UUID 선점 = 선점한다(outbox);
        UUID 남의_선점 = UUID.randomUUID();
        Instant 기록_시각 = 생성.plusSeconds(5);

        // when / then
        assertThatThrownBy(() -> outbox.markSent(남의_선점, 기록_시각)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> outbox.cancel(남의_선점, "NO_TOKEN", 기록_시각))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> outbox.fail(남의_선점, "REJECTED", 기록_시각)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> outbox.scheduleRetry(남의_선점, 기록_시각.plus(일분), 기록_시각))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> outbox.markSent(null, 기록_시각)).isInstanceOf(IllegalStateException.class);
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
        assertThat(outbox.getClaimToken()).isEqualTo(선점);
        assertThat(outbox.getAvailableAt()).isEqualTo(생성);
        assertThat(outbox.getResultCode()).isNull();
        assertThat(outbox.getFinishedAt()).isNull();
    }

    @Test
    void 선점하지_않은_작업에는_결과를_기록할_수_없다() {
        // given
        CollectionReminderOutbox outbox = 대기_작업();

        // when / then
        assertThatThrownBy(() -> outbox.markSent(UUID.randomUUID(), 생성)).isInstanceOf(IllegalStateException.class);
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
    }

    @Test
    void 닫힌_작업은_같은_토큰으로도_다시_기록하거나_선점할_수_없다() {
        // given
        CollectionReminderOutbox outbox = 대기_작업();
        UUID 선점 = 선점한다(outbox);
        outbox.fail(선점, "REJECTED", 생성.plusSeconds(5));

        // when / then
        assertThatThrownBy(() -> outbox.markSent(선점, 생성.plusSeconds(6))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> outbox.claim(UUID.randomUUID(), 생성.plus(Duration.ofMinutes(10)),
                생성.plus(Duration.ofMinutes(9)))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> outbox.expire("EXPIRED", 자정)).isInstanceOf(IllegalStateException.class);
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.FAILED);
        assertThat(outbox.getResultCode()).isEqualTo("REJECTED");
        assertThat(outbox.getAttemptCount()).isOne();
    }

    @Test
    void 취소와_실패는_결과_코드가_없으면_기록하지_않는다() {
        // given
        CollectionReminderOutbox outbox = 대기_작업();
        UUID 선점 = 선점한다(outbox);

        // when / then
        assertThatThrownBy(() -> outbox.cancel(선점, " ", 생성)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> outbox.fail(선점, null, 생성)).isInstanceOf(IllegalArgumentException.class);
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
        assertThat(outbox.getClaimToken()).isEqualTo(선점);
    }

    @Test
    void 재예약하면_대기를_유지한_채_RETRYABLE과_새_예정_시각을_남기고_선점을_푼다() {
        // given
        CollectionReminderOutbox outbox = 대기_작업();
        UUID 선점 = 선점한다(outbox);
        Instant 실패_시각 = 생성.plusSeconds(5);

        // when
        outbox.scheduleRetry(선점, 실패_시각.plus(일분), 실패_시각);

        // then
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
        assertThat(outbox.getResultCode()).isEqualTo("RETRYABLE");
        assertThat(outbox.getAvailableAt()).isEqualTo(실패_시각.plus(일분));
        assertThat(outbox.getClaimToken()).isNull();
        assertThat(outbox.getClaimExpiresAt()).isNull();
        assertThat(outbox.getFinishedAt()).isNull();
        assertThat(outbox.getAttemptCount()).isOne();
    }

    @Test
    void 재예약한_작업은_새_예정_시각부터_다시_선점한다() {
        // given
        CollectionReminderOutbox outbox = 대기_작업();
        UUID 선점 = 선점한다(outbox);
        Instant 다음_예정 = 생성.plus(Duration.ofMinutes(5));
        outbox.scheduleRetry(선점, 다음_예정, 생성.plusSeconds(5));

        // when
        assertThatThrownBy(() -> outbox.claim(UUID.randomUUID(), 다음_예정.plus(일분), 다음_예정.minusMillis(1)))
                .isInstanceOf(IllegalStateException.class);
        outbox.claim(UUID.randomUUID(), 다음_예정.plus(일분), 다음_예정);

        // then
        assertThat(outbox.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void 다음_발송_시각이_현재_시각보다_뒤가_아니거나_만료_시각과_같으면_재예약하지_않는다() {
        // given
        CollectionReminderOutbox outbox = 대기_작업();
        UUID 선점 = 선점한다(outbox);
        Instant 실패_시각 = 생성.plusSeconds(5);

        // when / then
        assertThatThrownBy(() -> outbox.scheduleRetry(선점, 실패_시각, 실패_시각))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> outbox.scheduleRetry(선점, 자정, 실패_시각))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(outbox.getClaimToken()).isEqualTo(선점);
        assertThat(outbox.getAvailableAt()).isEqualTo(생성);
        assertThat(outbox.getResultCode()).isNull();
    }

    @Test
    void 만료_시각이_된_대기_작업은_만료_사유로_닫는다() {
        // given
        CollectionReminderOutbox outbox = 대기_작업();

        // when
        outbox.expire("EXPIRED", 자정);

        // then
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.CANCELED);
        assertThat(outbox.getResultCode()).isEqualTo("EXPIRED");
        assertThat(outbox.getFinishedAt()).isEqualTo(자정);
    }

    @Test
    void 만료_시각_전이거나_선점_기한이_남은_작업은_만료로_닫지_않는다() {
        // given
        CollectionReminderOutbox 대기 = 대기_작업();
        CollectionReminderOutbox 발송_중 = 대기_작업();
        UUID 선점 = UUID.randomUUID();
        발송_중.claim(선점, 자정.plusSeconds(30), 자정.minusSeconds(30));

        // when / then
        assertThatThrownBy(() -> 대기.expire("EXPIRED", 자정.minusMillis(1))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> 발송_중.expire("EXPIRED", 자정.plusSeconds(10)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(대기.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
        assertThat(발송_중.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
        assertThat(발송_중.getClaimToken()).isEqualTo(선점);
    }

    @Test
    void 선점_기한이_지난_작업은_만료_시각이_지나면_만료로_닫는다() {
        // given
        CollectionReminderOutbox outbox = 대기_작업();
        outbox.claim(UUID.randomUUID(), 자정.plusSeconds(30), 자정.minusSeconds(30));

        // when
        outbox.expire("EXPIRED", 자정.plusSeconds(30));

        // then
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.CANCELED);
        assertThat(outbox.getClaimToken()).isNull();
        assertThat(outbox.getClaimExpiresAt()).isNull();
    }

    private CollectionReminderOutbox 대기_작업() {
        return 리마인더_작업(기기, TODAY, 생성, 자정);
    }

    private UUID 선점한다(NotificationOutbox outbox) {
        UUID 선점 = UUID.randomUUID();
        outbox.claim(선점, 생성.plus(일분), 생성);
        return 선점;
    }

    private static Instant 한국_시각(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(서울).toInstant();
    }
}
