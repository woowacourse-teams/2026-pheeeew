package com.pheeeew.notification.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.notification.domain.NotificationOutboxType.COLLECTION_CLAIMABLE;
import static com.pheeeew.notification.domain.NotificationOutboxType.COLLECTION_CLAIM_REMINDER;
import static com.pheeeew.notification.fixture.NotificationOutboxFixture.기기의_작업;
import static com.pheeeew.notification.fixture.NotificationOutboxFixture.기기의_작업들;
import static com.pheeeew.notification.fixture.PushTokenFixture.기본_등록_값;
import static com.pheeeew.notification.fixture.PushTokenFixture.새_등록_값;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.notification.domain.NotificationOutbox;
import com.pheeeew.notification.domain.NotificationOutboxType;
import com.pheeeew.notification.domain.NotificationSettings;
import com.pheeeew.notification.domain.PushMessage;
import com.pheeeew.notification.domain.PushSendResult;
import com.pheeeew.notification.domain.repository.DeviceNotificationSettingRepository;
import com.pheeeew.notification.domain.repository.DevicePushTokenRepository;
import com.pheeeew.notification.domain.repository.NotificationOutboxRepository;
import com.pheeeew.notification.fixture.NotificationOutboxFixture.작업;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@PostgisDataJpaTest
@Import({NotificationDispatcher.class, NotificationOutboxRepository.class, DevicePushTokenRepository.class,
        DeviceNotificationSettingRepository.class})
class NotificationDispatcherIntegrationTest {

    private static final ZoneId 서울 = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Instant 오늘_자정 = 한국_시각("2026-10-27T00:00:00");
    private static final Duration 일분 = Duration.ofMinutes(1);

    @Autowired
    private NotificationDispatcher dispatcher;

    @Autowired
    private NotificationOutboxRepository outboxes;

    @Autowired
    private DeviceRepository devices;

    @Autowired
    private DevicePushTokenRepository pushTokens;

    @Autowired
    private DeviceNotificationSettingRepository settings;

    @Autowired
    private JdbcClient jdbc;

    @MockitoBean
    private PushSender pushSender;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(서울);
        when(pushSender.isAvailable()).thenReturn(true);
        when(pushSender.send(anyString(), any())).thenReturn(PushSendResult.SENT);
    }

    @Test
    void 메모리_상태_없이_작업_행만으로_예정_시각에_한_번_보내고_SENT로_기록한다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        Instant 발견 = 한국_시각("2026-10-26T10:00:00");
        발급_가능_작업을_만든다(device, 발견);
        발송한다(발견.plus(Duration.ofMinutes(60)).minusMillis(1));
        verify(pushSender, never()).send(anyString(), any());

        // when
        발송한다(발견.plus(Duration.ofMinutes(60)));
        발송한다(발견.plus(Duration.ofMinutes(120)));

        // then
        verify(pushSender, times(1)).send(eq(기본_등록_값), any());
        작업 sent = 기기의_작업(jdbc, device.getId(), COLLECTION_CLAIMABLE.name());
        assertThat(sent.status()).isEqualTo("SENT");
        assertThat(sent.resultCode()).isNull();
        assertThat(sent.attemptCount()).isOne();
        assertThat(sent.claimToken()).isNull();
        assertThat(sent.finishedAt()).isEqualTo(발견.plus(Duration.ofMinutes(60)));
    }

    @Test
    void 선점하고_결과를_남기지_못한_작업은_선점_기한_1분이_지나야_다시_선점해_보낸다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        Instant 선점_시각 = 한국_시각("2026-10-26T23:00:00");
        리마인더_작업을_만든다(device, 선점_시각);
        UUID 멈춘_서버의_선점 = UUID.randomUUID();
        NotificationOutbox 멈춘_서버의_작업 = outboxes.claim(선점_시각, 멈춘_서버의_선점, 선점_시각.plus(일분)).orElseThrow();
        발송한다(선점_시각.plus(Duration.ofSeconds(59)));
        verify(pushSender, never()).send(anyString(), any());

        // when
        발송한다(선점_시각.plus(일분));
        boolean 늦은_기록 = outboxes.markSent(
                멈춘_서버의_작업.type(), 멈춘_서버의_작업.id(), 멈춘_서버의_선점, 선점_시각.plus(Duration.ofMinutes(2)));

        // then
        verify(pushSender, times(1)).send(eq(기본_등록_값), any());
        작업 sent = 기기의_작업(jdbc, device.getId(), COLLECTION_CLAIM_REMINDER.name());
        assertThat(sent.status()).isEqualTo("SENT");
        assertThat(sent.attemptCount()).isEqualTo(2);
        assertThat(sent.finishedAt()).isEqualTo(선점_시각.plus(일분));
        assertThat(늦은_기록).isFalse();
    }

    @Test
    void 선점을_잃은_늦은_발송기의_결과는_새_선점자의_작업을_바꾸지_못한다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        Instant 처음_선점 = 한국_시각("2026-10-26T23:00:00");
        리마인더_작업을_만든다(device, 처음_선점);
        UUID 옛_선점 = UUID.randomUUID();
        NotificationOutbox 옛_작업 = outboxes.claim(처음_선점, 옛_선점, 처음_선점.plus(일분)).orElseThrow();
        Instant 다시_선점 = 처음_선점.plus(일분);
        UUID 새_선점 = UUID.randomUUID();
        outboxes.claim(다시_선점, 새_선점, 다시_선점.plus(일분)).orElseThrow();

        // when
        boolean 옛_실패_기록 = outboxes.fail(옛_작업.type(), 옛_작업.id(), 옛_선점, "REJECTED", 다시_선점.plusSeconds(1));
        boolean 옛_재예약 = outboxes.retry(
                옛_작업.type(), 옛_작업.id(), 옛_선점, 다시_선점.plusSeconds(30), 다시_선점.plusSeconds(1));
        boolean 옛_취소 = outboxes.cancel(옛_작업.type(), 옛_작업.id(), 옛_선점, "NO_TARGET", 다시_선점.plusSeconds(1));
        작업 진행_중 = 기기의_작업(jdbc, device.getId(), COLLECTION_CLAIM_REMINDER.name());
        boolean 새_발송_기록 = outboxes.markSent(옛_작업.type(), 옛_작업.id(), 새_선점, 다시_선점.plusSeconds(2));

        // then
        assertThat(List.of(옛_실패_기록, 옛_재예약, 옛_취소)).containsOnly(false);
        assertThat(진행_중.status()).isEqualTo("PENDING");
        assertThat(진행_중.claimToken()).isEqualTo(새_선점);
        assertThat(진행_중.resultCode()).isNull();
        assertThat(새_발송_기록).isTrue();
        assertThat(기기의_작업(jdbc, device.getId(), COLLECTION_CLAIM_REMINDER.name()).status()).isEqualTo("SENT");
    }

    @Test
    void 순위_작업이_일시_실패하면_1분_뒤와_그_뒤_2분_뒤에만_다시_보낸다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        Instant 회차 = 한국_시각("2026-10-26T10:00:00");
        순위_작업을_만든다(device, 회차, 2, 3);
        when(pushSender.send(anyString(), any()))
                .thenReturn(PushSendResult.RETRYABLE, PushSendResult.RETRYABLE, PushSendResult.SENT);

        // when
        발송한다(회차);
        작업 첫_실패 = 기기의_작업(jdbc, device.getId(), "RANK_DROPPED");
        발송한다(회차.plus(일분).minusMillis(1));
        발송한다(회차.plus(일분));
        작업 두번째_실패 = 기기의_작업(jdbc, device.getId(), "RANK_DROPPED");
        발송한다(회차.plus(Duration.ofMinutes(3)).minusMillis(1));
        발송한다(회차.plus(Duration.ofMinutes(3)));

        // then
        verify(pushSender, times(3)).send(eq(기본_등록_값), any());
        assertThat(첫_실패.status()).isEqualTo("PENDING");
        assertThat(첫_실패.resultCode()).isEqualTo("RETRYABLE");
        assertThat(첫_실패.claimToken()).isNull();
        assertThat(첫_실패.availableAt()).isEqualTo(회차.plus(일분));
        assertThat(두번째_실패.availableAt()).isEqualTo(회차.plus(Duration.ofMinutes(3)));
        작업 sent = 기기의_작업(jdbc, device.getId(), "RANK_DROPPED");
        assertThat(sent.status()).isEqualTo("SENT");
        assertThat(sent.resultCode()).isNull();
        assertThat(sent.attemptCount()).isEqualTo(3);
    }

    @Test
    void 순위_작업은_세_번_모두_일시_실패하면_FAILED_RETRY_EXHAUSTED로_끝나고_네_번째는_보내지_않는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        Instant 회차 = 한국_시각("2026-10-26T10:00:00");
        순위_작업을_만든다(device, 회차, 2, 3);
        when(pushSender.send(anyString(), any())).thenReturn(PushSendResult.RETRYABLE);

        // when
        발송한다(회차);
        발송한다(회차.plus(일분));
        발송한다(회차.plus(Duration.ofMinutes(3)));
        발송한다(회차.plus(Duration.ofMinutes(4)));

        // then
        verify(pushSender, times(3)).send(anyString(), any());
        작업 failed = 기기의_작업(jdbc, device.getId(), "RANK_DROPPED");
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.resultCode()).isEqualTo("RETRY_EXHAUSTED");
        assertThat(failed.finishedAt()).isEqualTo(회차.plus(Duration.ofMinutes(3)));
    }

    @Test
    void 컬렉션_작업은_1분_5분_15분_간격으로_네_번_시도한_뒤_FAILED로_끝난다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        Instant 첫_시도 = 한국_시각("2026-10-26T23:00:00");
        리마인더_작업을_만든다(device, 첫_시도);
        when(pushSender.send(anyString(), any())).thenReturn(PushSendResult.RETRYABLE);
        List<Instant> 예정_시각들 = List.of(첫_시도, 첫_시도.plus(일분), 첫_시도.plus(Duration.ofMinutes(6)),
                첫_시도.plus(Duration.ofMinutes(21)));

        // when
        List<Long> 예정_직전_발송_수 = new ArrayList<>();
        for (Instant 예정 : 예정_시각들) {
            발송한다(예정.minusMillis(1));
            예정_직전_발송_수.add(보낸_횟수());
            발송한다(예정);
        }
        발송한다(첫_시도.plus(Duration.ofMinutes(50)));

        // then
        assertThat(예정_직전_발송_수).containsExactly(0L, 1L, 2L, 3L);
        verify(pushSender, times(4)).send(anyString(), any());
        작업 failed = 기기의_작업(jdbc, device.getId(), COLLECTION_CLAIM_REMINDER.name());
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.resultCode()).isEqualTo("RETRY_EXHAUSTED");
        assertThat(failed.attemptCount()).isEqualTo(4);
    }

    @Test
    void 다음_재시도_시각이_자정과_같으면_재예약하지_않고_FAILED로_끝낸다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        리마인더_작업을_만든다(device, 한국_시각("2026-10-26T23:00:00"));
        when(pushSender.send(anyString(), any())).thenReturn(PushSendResult.RETRYABLE);

        // when
        발송한다(오늘_자정.minus(일분));

        // then
        verify(pushSender, times(1)).send(anyString(), any());
        작업 failed = 기기의_작업(jdbc, device.getId(), COLLECTION_CLAIM_REMINDER.name());
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.resultCode()).isEqualTo("RETRY_EXHAUSTED");
    }

    @Test
    void 발송이_예외를_던지면_일시_실패로_재예약하고_같은_실행의_다음_작업은_다음_실행에서_보낸다() {
        // given
        Device 실패할_기기 = 토큰이_있는_기기(기본_등록_값);
        Device 기다릴_기기 = 토큰이_있는_기기(새_등록_값);
        Instant 지금 = 한국_시각("2026-10-26T23:00:00");
        리마인더_작업을_만든다(실패할_기기, 지금);
        리마인더_작업을_만든다(기다릴_기기, 지금);
        when(pushSender.send(eq(기본_등록_값), any())).thenThrow(new IllegalStateException("FCM 장애"));

        // when
        발송한다(지금);
        verify(pushSender, never()).send(eq(새_등록_값), any());
        발송한다(지금.plusSeconds(10));

        // then
        verify(pushSender, times(1)).send(eq(새_등록_값), any());
        작업 retrying = 기기의_작업(jdbc, 실패할_기기.getId(), COLLECTION_CLAIM_REMINDER.name());
        assertThat(retrying.status()).isEqualTo("PENDING");
        assertThat(retrying.resultCode()).isEqualTo("RETRYABLE");
        assertThat(retrying.availableAt()).isEqualTo(지금.plus(일분));
        assertThat(기기의_작업(jdbc, 기다릴_기기.getId(), COLLECTION_CLAIM_REMINDER.name()).status()).isEqualTo("SENT");
    }

    @Test
    void 영구_거절은_다시_보내지_않고_FAILED_REJECTED로_끝내며_토큰은_지우지_않는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        Instant 지금 = 한국_시각("2026-10-26T23:00:00");
        리마인더_작업을_만든다(device, 지금);
        when(pushSender.send(anyString(), any())).thenReturn(PushSendResult.REJECTED);

        // when
        발송한다(지금);
        발송한다(지금.plus(Duration.ofMinutes(30)));

        // then
        verify(pushSender, times(1)).send(anyString(), any());
        작업 failed = 기기의_작업(jdbc, device.getId(), COLLECTION_CLAIM_REMINDER.name());
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.resultCode()).isEqualTo("REJECTED");
        assertThat(등록_값들(device)).containsExactly(기본_등록_값);
    }

    @Test
    void 무효_등록_값이면_보낸_토큰을_지우고_FAILED_UNREGISTERED로_끝낸다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        Instant 지금 = 한국_시각("2026-10-26T23:00:00");
        리마인더_작업을_만든다(device, 지금);
        when(pushSender.send(anyString(), any())).thenReturn(PushSendResult.UNREGISTERED);

        // when
        발송한다(지금);

        // then
        assertThat(등록_값들(device)).isEmpty();
        작업 failed = 기기의_작업(jdbc, device.getId(), COLLECTION_CLAIM_REMINDER.name());
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.resultCode()).isEqualTo("UNREGISTERED");
    }

    @Test
    void 발송_중에_새_토큰이_등록되면_무효_응답을_받아도_새_토큰은_남는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        Instant 지금 = 한국_시각("2026-10-26T23:00:00");
        리마인더_작업을_만든다(device, 지금);
        doAnswer(invocation -> {
            pushTokens.save(device.getId(), 새_등록_값, 지금);
            return PushSendResult.UNREGISTERED;
        }).when(pushSender).send(eq(기본_등록_값), any());

        // when
        발송한다(지금);

        // then
        assertThat(등록_값들(device)).containsExactly(새_등록_값);
        assertThat(기기의_작업(jdbc, device.getId(), COLLECTION_CLAIM_REMINDER.name()).resultCode())
                .isEqualTo("UNREGISTERED");
    }

    @Test
    void 일시_실패로_재시도하다_23시_이후에_다시_선점된_발급_가능_알림은_SUPERSEDED로_닫는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        발급_가능_작업을_만든다(device, 한국_시각("2026-10-26T21:50:00"));
        when(pushSender.send(anyString(), any())).thenReturn(PushSendResult.RETRYABLE, PushSendResult.SENT);
        Instant 첫_시도 = 한국_시각("2026-10-26T22:59:30");
        발송한다(첫_시도);

        // when
        발송한다(첫_시도.plus(일분));

        // then
        verify(pushSender, times(1)).send(anyString(), any());
        작업 canceled = 기기의_작업(jdbc, device.getId(), COLLECTION_CLAIMABLE.name());
        assertThat(canceled.status()).isEqualTo("CANCELED");
        assertThat(canceled.resultCode()).isEqualTo("SUPERSEDED");
        assertThat(canceled.attemptCount()).isEqualTo(2);
    }

    @Test
    void 리마인더와_순위_하락은_23시_이후에_선점해도_보낸다() {
        // given
        Device 리마인더_기기 = 토큰이_있는_기기(기본_등록_값);
        Device 순위_기기 = 토큰이_있는_기기(새_등록_값);
        리마인더_작업을_만든다(리마인더_기기, 한국_시각("2026-10-26T23:00:00"));
        순위_작업을_만든다(순위_기기, 한국_시각("2026-10-26T23:10:00"), 1, 2);

        // when
        발송한다(한국_시각("2026-10-26T23:10:00"));

        // then
        verify(pushSender).send(eq(기본_등록_값), any());
        verify(pushSender).send(eq(새_등록_값), any());
        assertThat(기기의_작업(jdbc, 리마인더_기기.getId(), COLLECTION_CLAIM_REMINDER.name()).status()).isEqualTo("SENT");
        assertThat(기기의_작업(jdbc, 순위_기기.getId(), "RANK_DROPPED").status()).isEqualTo("SENT");
    }

    @Test
    void 자정이_지난_작업은_보내지_않고_EXPIRED로_닫는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        발급_가능_작업을_만든다(device, 한국_시각("2026-10-26T23:30:00"));

        // when
        발송한다(한국_시각("2026-10-27T00:30:00"));

        // then
        verify(pushSender, never()).send(anyString(), any());
        작업 expired = 기기의_작업(jdbc, device.getId(), COLLECTION_CLAIMABLE.name());
        assertThat(expired.status()).isEqualTo("CANCELED");
        assertThat(expired.resultCode()).isEqualTo("EXPIRED");
        assertThat(expired.attemptCount()).isZero();
    }

    @Test
    void 만료_쓸기_직후_자정이_지나도_선점_조건이_지난_날짜_작업을_막는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        리마인더_작업을_만든다(device, 한국_시각("2026-10-26T23:00:00"));
        when(clock.instant()).thenReturn(오늘_자정.minusMillis(1), 오늘_자정);

        // when
        dispatcher.dispatch();

        // then
        verify(pushSender, never()).send(anyString(), any());
        작업 pending = 기기의_작업(jdbc, device.getId(), COLLECTION_CLAIM_REMINDER.name());
        assertThat(pending.status()).isEqualTo("PENDING");
        assertThat(pending.attemptCount()).isZero();
    }

    @Test
    void 자정_직전에_선점했어도_발송_직전에_자정이_지났으면_보내지_않고_EXPIRED로_닫는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        리마인더_작업을_만든다(device, 한국_시각("2026-10-26T23:00:00"));
        when(clock.instant()).thenReturn(오늘_자정.minusMillis(1), 오늘_자정.minusMillis(1), 오늘_자정);

        // when
        dispatcher.dispatch();

        // then
        verify(pushSender, never()).send(anyString(), any());
        작업 expired = 기기의_작업(jdbc, device.getId(), COLLECTION_CLAIM_REMINDER.name());
        assertThat(expired.status()).isEqualTo("CANCELED");
        assertThat(expired.resultCode()).isEqualTo("EXPIRED");
        assertThat(expired.attemptCount()).isOne();
    }

    @Test
    void 함께_보낼_수_있는_작업은_만료가_이른_것부터_선점한다() {
        // given
        Device 컬렉션_기기 = 토큰이_있는_기기("collection");
        Device 순위_기기 = 토큰이_있는_기기("rank");
        발급_가능_작업을_만든다(컬렉션_기기, 한국_시각("2026-10-26T08:59:00"));
        순위_작업을_만든다(순위_기기, 한국_시각("2026-10-26T10:00:00"), 2, 3);

        // when
        발송한다(한국_시각("2026-10-26T10:00:10"));

        // then
        InOrder 순서 = inOrder(pushSender);
        순서.verify(pushSender).send(eq("rank"), any());
        순서.verify(pushSender).send(eq("collection"), any());
    }

    @Test
    void 순위_작업은_회차_5분이_지나면_보내지_않고_EXPIRED로_닫는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        Instant 회차 = 한국_시각("2026-10-26T10:00:00");
        순위_작업을_만든다(device, 회차, 2, 3);

        // when
        발송한다(회차.plus(Duration.ofMinutes(5)));

        // then
        verify(pushSender, never()).send(anyString(), any());
        작업 expired = 기기의_작업(jdbc, device.getId(), "RANK_DROPPED");
        assertThat(expired.status()).isEqualTo("CANCELED");
        assertThat(expired.resultCode()).isEqualTo("EXPIRED");
    }

    @Test
    void 선점_기한이_남은_작업은_자정이_지나도_만료_쓸기가_건드리지_않아_발송_결과가_기록된다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        리마인더_작업을_만든다(device, 한국_시각("2026-10-26T23:00:00"));
        Instant 선점_시각 = 오늘_자정.minusSeconds(30);
        UUID 선점 = UUID.randomUUID();
        NotificationOutbox 발송_중 = outboxes.claim(선점_시각, 선점, 선점_시각.plus(일분)).orElseThrow();

        // when
        발송한다(오늘_자정.plusSeconds(10));
        boolean 기록 = outboxes.markSent(발송_중.type(), 발송_중.id(), 선점, 오늘_자정.plusSeconds(10));

        // then
        verify(pushSender, never()).send(anyString(), any());
        assertThat(기록).isTrue();
        assertThat(기기의_작업(jdbc, device.getId(), COLLECTION_CLAIM_REMINDER.name()).status()).isEqualTo("SENT");
    }

    @Test
    void 선점_후_중단이_반복돼_시도_횟수를_넘긴_작업은_보내지_않고_FAILED_RETRY_EXHAUSTED로_닫는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        Instant 회차 = 한국_시각("2026-10-26T10:00:00");
        순위_작업을_만든다(device, 회차, 2, 3);
        for (int 중단 = 0; 중단 < NotificationOutboxType.RANK_DROPPED.maxAttempts(); 중단++) {
            Instant 선점_시각 = 회차.plus(일분.multipliedBy(중단));
            outboxes.claim(선점_시각, UUID.randomUUID(), 선점_시각.plus(일분)).orElseThrow();
        }

        // when
        발송한다(회차.plus(Duration.ofMinutes(3)));

        // then
        verify(pushSender, never()).send(anyString(), any());
        작업 failed = 기기의_작업(jdbc, device.getId(), "RANK_DROPPED");
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.resultCode()).isEqualTo("RETRY_EXHAUSTED");
        assertThat(failed.attemptCount()).isEqualTo(4);
    }

    @Test
    void 발송_직전에는_작업_유형에_맞는_수신_설정만_본다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        settings.save(device.getId(), NotificationSettings.of(false, true), 한국_시각("2026-10-26T09:00:00"));
        리마인더_작업을_만든다(device, 한국_시각("2026-10-26T23:00:00"));
        순위_작업을_만든다(device, 한국_시각("2026-10-26T23:00:00"), 1, 2);

        // when
        발송한다(한국_시각("2026-10-26T23:00:00"));

        // then
        verify(pushSender, times(1)).send(anyString(), any());
        작업 reminder = 기기의_작업(jdbc, device.getId(), COLLECTION_CLAIM_REMINDER.name());
        assertThat(reminder.status()).isEqualTo("CANCELED");
        assertThat(reminder.resultCode()).isEqualTo("NO_TARGET");
        assertThat(기기의_작업(jdbc, device.getId(), "RANK_DROPPED").status()).isEqualTo("SENT");
    }

    @Test
    void 발송기를_쓸_수_없으면_선점도_발송도_하지_않는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        리마인더_작업을_만든다(device, 한국_시각("2026-10-26T23:00:00"));
        when(pushSender.isAvailable()).thenReturn(false);

        // when
        발송한다(한국_시각("2026-10-26T23:00:00"));

        // then
        verify(pushSender, never()).send(anyString(), any());
        작업 pending = 기기의_작업(jdbc, device.getId(), COLLECTION_CLAIM_REMINDER.name());
        assertThat(pending.status()).isEqualTo("PENDING");
        assertThat(pending.attemptCount()).isZero();
    }

    @Test
    void 세_유형의_메시지는_기존_알림_계약과_같다() {
        // given
        Device 발급_가능_기기 = 토큰이_있는_기기("claimable");
        Device 리마인더_기기 = 토큰이_있는_기기("reminder");
        Device 순위_기기 = 토큰이_있는_기기("rank");
        Instant 지금 = 한국_시각("2026-10-26T22:00:00");
        발급_가능_작업을_만든다(발급_가능_기기, 지금.minus(Duration.ofHours(1)));
        리마인더_작업을_만든다(리마인더_기기, 지금);
        순위_작업을_만든다(순위_기기, 지금, 1, 2);

        // when
        발송한다(지금);

        // then
        PushMessage claimable = 보낸_메시지("claimable");
        assertThat(claimable.title()).isEqualTo("컬렉션 발급 조건 달성!");
        assertThat(claimable.body()).isEqualTo("오늘의 감정 카드를 받으세요.\n카드 받으러 가기👉");
        assertThat(claimable.data()).isEqualTo(Map.of("type", "COLLECTION_CLAIMABLE", "date", "2026-10-26"));
        PushMessage reminder = 보낸_메시지("reminder");
        assertThat(reminder.title()).isEqualTo("아직 받지 않은 카드가 있어요");
        assertThat(reminder.body()).isEqualTo("오늘의 카드는 자정이 지나면 받을 수 없어요.\n카드 받으러 가기👉");
        assertThat(reminder.data()).isEqualTo(Map.of("type", "COLLECTION_CLAIM_REMINDER", "date", "2026-10-26"));
        PushMessage rank = 보낸_메시지("rank");
        assertThat(rank.title()).isEqualTo("앗, 프레스 순위가 내려갔어요👀");
        assertThat(rank.body()).isEqualTo("오늘 내 순위 1위 → 2위!\n쌓인 마음으로 순위를 올리러 갈까요?");
        assertThat(rank.data()).isEqualTo(Map.of(
                "type", "RANK_DROPPED", "date", "2026-10-26", "previousRank", "1", "currentRank", "2"));
    }

    @Test
    void 보관_기간_7일이_지난_작업만_지운다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        for (int 며칠_전 = 7; 며칠_전 <= 8; 며칠_전++) {
            Instant 그날 = 한국_시각("2026-10-26T23:00:00").minus(Duration.ofDays(며칠_전));
            outboxes.saveCollection(COLLECTION_CLAIM_REMINDER, device.getId(), TODAY.minusDays(며칠_전), 그날, 그날,
                    오늘_자정.minus(Duration.ofDays(며칠_전)));
        }
        when(clock.instant()).thenReturn(한국_시각("2026-10-26T04:00:00"));

        // when
        dispatcher.deleteOldOutbox();

        // then
        assertThat(기기의_작업들(jdbc, device.getId())).extracting(작업::targetDate)
                .containsExactly(TODAY.minusDays(7));
    }

    private Device 토큰이_있는_기기(String token) {
        Device device = devices.saveAndFlush(기본_기기_빌더().build());
        pushTokens.save(device.getId(), token, 한국_시각("2026-10-26T09:00:00"));
        return device;
    }

    private void 발급_가능_작업을_만든다(Device device, Instant 발견) {
        outboxes.saveCollection(COLLECTION_CLAIMABLE, device.getId(), TODAY, 발견, 발견.plus(Duration.ofHours(1)),
                오늘_자정);
    }

    private void 리마인더_작업을_만든다(Device device, Instant 생성) {
        outboxes.saveCollection(COLLECTION_CLAIM_REMINDER, device.getId(), TODAY, 생성, 생성, 오늘_자정);
    }

    private void 순위_작업을_만든다(Device device, Instant 회차, int 이전_순위, int 현재_순위) {
        outboxes.saveRank(device.getId(), TODAY, 회차, 이전_순위, 현재_순위, 회차, 회차.plus(Duration.ofMinutes(5)));
    }

    private void 발송한다(Instant 지금) {
        when(clock.instant()).thenReturn(지금);
        dispatcher.dispatch();
    }

    private long 보낸_횟수() {
        return Mockito.mockingDetails(pushSender).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("send"))
                .count();
    }

    private PushMessage 보낸_메시지(String token) {
        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender).send(eq(token), captor.capture());
        return captor.getValue();
    }

    private List<String> 등록_값들(Device device) {
        return jdbc.sql("SELECT token FROM device_push_tokens WHERE device_id = :deviceId")
                .param("deviceId", device.getId())
                .query(String.class)
                .list();
    }

    private static Instant 한국_시각(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(서울).toInstant();
    }
}
