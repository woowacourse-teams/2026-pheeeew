package com.pheeeew.notification.application;

import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_카드_빌더;
import static com.pheeeew.collection.fixture.DeviceDailyStampFixture.일별_스탬프를_저장한다;
import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.DeviceDailyPressFixture.개인_프레스를_저장한다;
import static com.pheeeew.notification.fixture.NotificationOutboxFixture.기기의_작업;
import static com.pheeeew.notification.fixture.NotificationOutboxFixture.기기의_작업들;
import static com.pheeeew.notification.fixture.NotificationOutboxFixture.알림_데이터를_지운다;
import static com.pheeeew.notification.fixture.NotificationSettingFixture.기기_수신_설정;
import static com.pheeeew.notification.fixture.PushTokenFixture.기기_등록_값;
import static com.pheeeew.notification.fixture.PushTokenFixture.기본_등록_값;
import static com.pheeeew.notification.fixture.PushTokenFixture.새_등록_값;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pheeeew.collection.domain.repository.CollectionCardRepository;
import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.notification.domain.NotificationSettings;
import com.pheeeew.notification.domain.PushMessage;
import com.pheeeew.notification.domain.PushSendResult;
import com.pheeeew.notification.domain.outbox.NotificationOutboxType;
import com.pheeeew.notification.domain.repository.DeviceNotificationSettingRepository;
import com.pheeeew.notification.domain.repository.DevicePushTokenRepository;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({CollectionNotificationService.class, NotificationDispatcher.class, NotificationOutboxService.class,
        NotificationSettingService.class, DevicePushTokenRegistrationService.class, CollectionProgressRepository.class})
class CollectionNotificationServiceIntegrationTest {

    private static final ZoneId 서울 = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Instant 오전_10시 = 한국_시각("2026-10-26T10:00:00");
    private static final Duration 한_시간 = Duration.ofHours(1);

    @Autowired
    private CollectionNotificationService service;

    @Autowired
    private NotificationDispatcher dispatcher;

    @Autowired
    private NotificationSettingService notificationSettingService;

    @Autowired
    private DeviceRepository devices;

    @Autowired
    private CollectionCardRepository cards;

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

    private final List<Long> 만든_기기들 = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(서울);
        when(pushSender.isAvailable()).thenReturn(true);
        when(pushSender.send(anyString(), any())).thenReturn(PushSendResult.SENT);
    }

    @AfterEach
    void tearDown() {
        알림_데이터를_지운다(jdbc, List.of(TODAY, TODAY.plusDays(1)), 만든_기기들);
    }

    @Test
    void 발급_가능을_처음_확인하고_1시간이_지나야_한_번만_보낸다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        발급_가능하게_만든다(device, EmotionState.ANGRY);
        탐색하고_발송한다(오전_10시);
        탐색하고_발송한다(오전_10시.plus(Duration.ofMinutes(5)));
        탐색하고_발송한다(오전_10시.plus(한_시간).minusMillis(1));
        verify(pushSender, never()).send(anyString(), any());

        // when
        탐색하고_발송한다(오전_10시.plus(한_시간));
        탐색하고_발송한다(오전_10시.plus(Duration.ofHours(3)));

        // then
        PushMessage message = 보낸_메시지(기본_등록_값);
        assertThat(message.data()).isEqualTo(Map.of("type", "COLLECTION_CLAIMABLE", "date", "2026-10-26"));
        assertThat(기기의_작업들(jdbc, device.getId())).hasSize(1);
    }

    @Test
    void 다시_탐색해도_최초_발견_시각과_예정_시각을_덮어쓰지_않는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        발급_가능하게_만든다(device, EmotionState.ANGRY);

        // when
        탐색한다(오전_10시);
        탐색한다(오전_10시.plus(Duration.ofMinutes(5)));
        탐색한다(오전_10시.plus(Duration.ofMinutes(10)));

        // then
        List<작업> 작업들 = 기기의_작업들(jdbc, device.getId());
        assertThat(작업들).hasSize(1);
        작업 claimable = 작업들.getFirst();
        assertThat(claimable.type()).isEqualTo("COLLECTION_CLAIMABLE");
        assertThat(claimable.targetDate()).isEqualTo(TODAY);
        assertThat(claimable.discoveredAt()).isEqualTo(오전_10시);
        assertThat(claimable.availableAt()).isEqualTo(오전_10시.plus(한_시간));
        assertThat(claimable.expiresAt()).isEqualTo(한국_시각("2026-10-27T00:00:00"));
        assertThat(claimable.status()).isEqualTo("PENDING");
    }

    @Test
    void 한_시간_안에_카드를_받으면_보내지_않고_CARD_CLAIMED로_닫는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        발급_가능하게_만든다(device, EmotionState.ANGRY);
        탐색한다(오전_10시);
        카드를_받는다(device, EmotionState.ANGRY);

        // when
        탐색하고_발송한다(오전_10시.plus(한_시간));

        // then
        verify(pushSender, never()).send(anyString(), any());
        작업 canceled = 기기의_작업(jdbc, device.getId(), "COLLECTION_CLAIMABLE");
        assertThat(canceled.status()).isEqualTo("CANCELED");
        assertThat(canceled.resultCode()).isEqualTo("CARD_CLAIMED");
    }

    @Test
    void 카드를_받은_뒤_다른_감정의_조건을_채워도_다시_보내지_않는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        발급_가능하게_만든다(device, EmotionState.ANGRY);
        탐색한다(오전_10시);
        카드를_받는다(device, EmotionState.ANGRY);
        탐색하고_발송한다(오전_10시.plus(Duration.ofMinutes(30)));
        발급_가능하게_만든다(device, EmotionState.EXHAUSTED);

        // when
        탐색하고_발송한다(오전_10시.plus(Duration.ofHours(5)));
        탐색하고_발송한다(오전_10시.plus(Duration.ofHours(6)));
        리마인더를_만들고_발송한다(한국_시각("2026-10-26T23:00:00"));

        // then
        verify(pushSender, never()).send(anyString(), any());
        assertThat(기기의_작업들(jdbc, device.getId())).hasSize(1);
    }

    @Test
    void 발송_직전에_수신_설정이_꺼져_있으면_NOTIFICATION_DISABLED로_닫고_다시_켜도_그날은_다시_만들지_않는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        발급_가능하게_만든다(device, EmotionState.ANGRY);
        탐색한다(오전_10시);
        settings.save(기기_수신_설정(device.getId(), NotificationSettings.of(false, true)));
        탐색하고_발송한다(오전_10시.plus(한_시간));

        // when
        수신_설정을_바꾼다(device, NotificationSettings.of(true, true));
        탐색하고_발송한다(오전_10시.plus(Duration.ofMinutes(75)));
        탐색하고_발송한다(오전_10시.plus(Duration.ofHours(3)));

        // then
        verify(pushSender, never()).send(anyString(), any());
        List<작업> 작업들 = 기기의_작업들(jdbc, device.getId());
        assertThat(작업들).hasSize(1);
        assertThat(작업들.getFirst().status()).isEqualTo("CANCELED");
        assertThat(작업들.getFirst().resultCode()).isEqualTo("NOTIFICATION_DISABLED");
    }

    @Test
    void 발송_직전에_토큰이_없으면_NO_TOKEN으로_닫고_다시_등록해도_그날은_다시_만들지_않는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        발급_가능하게_만든다(device, EmotionState.ANGRY);
        탐색한다(오전_10시);
        pushTokens.deleteByDeviceId(device.getId());
        탐색하고_발송한다(오전_10시.plus(한_시간));

        // when
        pushTokens.save(기기_등록_값(device.getId(), 새_등록_값));
        탐색하고_발송한다(오전_10시.plus(Duration.ofMinutes(75)));
        탐색하고_발송한다(오전_10시.plus(Duration.ofHours(3)));

        // then
        verify(pushSender, never()).send(anyString(), any());
        List<작업> 작업들 = 기기의_작업들(jdbc, device.getId());
        assertThat(작업들).hasSize(1);
        assertThat(작업들.getFirst().resultCode()).isEqualTo("NO_TOKEN");
    }

    @Test
    void 자정_전에_보내지_못한_작업은_만료되고_다음_날_새로_발견하면_그날_날짜로_보낸다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        발급_가능하게_만든다(device, EmotionState.ANGRY);
        일별_스탬프를_저장한다(jdbc, device.getId(), TODAY.plusDays(1), EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), TODAY.plusDays(1), EmotionState.ANGRY, 100);
        탐색한다(한국_시각("2026-10-26T23:30:00"));

        // when
        탐색하고_발송한다(한국_시각("2026-10-27T00:30:00"));
        verify(pushSender, never()).send(anyString(), any());
        탐색하고_발송한다(한국_시각("2026-10-27T01:30:00"));

        // then
        assertThat(보낸_메시지(기본_등록_값).data()).containsEntry("date", "2026-10-27");
        assertThat(기기의_작업들(jdbc, device.getId())).extracting(작업::targetDate, 작업::status, 작업::resultCode)
                .containsExactly(
                        tuple(TODAY, "CANCELED", "EXPIRED"),
                        tuple(TODAY.plusDays(1), "SENT", null));
    }

    @Test
    void 전날_알림을_받은_기기도_다음_날_발급_가능해지면_다시_받는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        발급_가능하게_만든다(device, EmotionState.ANGRY);
        일별_스탬프를_저장한다(jdbc, device.getId(), TODAY.plusDays(1), EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), TODAY.plusDays(1), EmotionState.ANGRY, 100);
        탐색하고_발송한다(오전_10시);
        탐색하고_발송한다(오전_10시.plus(한_시간));

        // when
        탐색하고_발송한다(오전_10시.plus(Duration.ofHours(24)));
        탐색하고_발송한다(오전_10시.plus(Duration.ofHours(25)));

        // then
        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender, times(2)).send(eq(기본_등록_값), captor.capture());
        assertThat(captor.getAllValues()).extracting(message -> message.data().get("date"))
                .containsExactly("2026-10-26", "2026-10-27");
    }

    @Test
    void 예정_시각이_23시_직전이면_보내고_23시_정각이면_보내지_않고_리마인더가_대신한다() {
        // given
        Device 직전_기기 = 토큰이_있는_기기(기본_등록_값);
        Device 정각_기기 = 토큰이_있는_기기(새_등록_값);
        발급_가능하게_만든다(직전_기기, EmotionState.ANGRY);
        탐색한다(한국_시각("2026-10-26T21:59:59.999"));
        발급_가능하게_만든다(정각_기기, EmotionState.ANGRY);
        탐색한다(한국_시각("2026-10-26T22:00:00"));

        // when
        발송한다(한국_시각("2026-10-26T22:59:59.999"));
        리마인더를_만들고_발송한다(한국_시각("2026-10-26T23:00:00"));

        // then
        ArgumentCaptor<PushMessage> 정각_기기_메시지 = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender, times(1)).send(eq(새_등록_값), 정각_기기_메시지.capture());
        assertThat(정각_기기_메시지.getValue().data()).containsEntry("type", "COLLECTION_CLAIM_REMINDER");
        작업 superseded = 기기의_작업(jdbc, 정각_기기.getId(), "COLLECTION_CLAIMABLE");
        assertThat(superseded.status()).isEqualTo("CANCELED");
        assertThat(superseded.resultCode()).isEqualTo("SUPERSEDED");
        assertThat(기기의_작업(jdbc, 직전_기기.getId(), "COLLECTION_CLAIMABLE").status()).isEqualTo("SENT");
        verify(pushSender, times(2)).send(eq(기본_등록_값), any());
    }

    @Test
    void 리마인더_생성을_놓친_날에는_23시_전에_발견한_기기도_발급_가능이_SUPERSEDED로_닫혀_알림을_받지_못한다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        발급_가능하게_만든다(device, EmotionState.ANGRY);
        탐색한다(한국_시각("2026-10-26T22:10:00"));

        // when
        탐색하고_발송한다(한국_시각("2026-10-26T23:20:00"));
        탐색하고_발송한다(한국_시각("2026-10-26T23:59:59"));

        // then
        verify(pushSender, never()).send(anyString(), any());
        assertThat(기기의_작업들(jdbc, device.getId())).extracting(작업::type, 작업::status, 작업::resultCode)
                .containsExactly(tuple("COLLECTION_CLAIMABLE", "CANCELED", "SUPERSEDED"));
    }

    @Test
    void 오후_11시_이후_처음_발급_가능해진_기기는_그날_어떤_알림도_받지_못하고_자정에_만료된다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        리마인더를_만들고_발송한다(한국_시각("2026-10-26T23:00:00"));
        발급_가능하게_만든다(device, EmotionState.ANGRY);

        // when
        탐색하고_발송한다(한국_시각("2026-10-26T23:10:00"));
        탐색하고_발송한다(한국_시각("2026-10-26T23:59:59.999"));
        발송한다(한국_시각("2026-10-27T00:00:10"));

        // then
        verify(pushSender, never()).send(anyString(), any());
        assertThat(기기의_작업들(jdbc, device.getId())).extracting(작업::type, 작업::status, 작업::resultCode)
                .containsExactly(tuple("COLLECTION_CLAIMABLE", "CANCELED", "EXPIRED"));
    }

    @Test
    void 리마인더는_합산_조건을_채우고_오늘_카드가_없는_기기에만_보낸다() {
        // given
        Device 받지_않은_기기 = 토큰이_있는_기기(기본_등록_값);
        발급_가능하게_만든다(받지_않은_기기, EmotionState.ANGRY);
        Device 이미_받은_기기 = 토큰이_있는_기기(새_등록_값);
        발급_가능하게_만든다(이미_받은_기기, EmotionState.ANGRY);
        카드를_받는다(이미_받은_기기, EmotionState.EXHAUSTED);
        Device 조건이_모자란_기기 = 토큰이_있는_기기("insufficient-token");
        일별_스탬프를_저장한다(jdbc, 조건이_모자란_기기.getId(), TODAY, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, 조건이_모자란_기기.getId(), TODAY, EmotionState.ANGRY, 99);
        Device 감정이_다른_기기 = 토큰이_있는_기기("other-state-token");
        일별_스탬프를_저장한다(jdbc, 감정이_다른_기기.getId(), TODAY, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, 감정이_다른_기기.getId(), TODAY, EmotionState.EXHAUSTED, 100);
        Device 날짜가_다른_기기 = 토큰이_있는_기기("other-date-token");
        일별_스탬프를_저장한다(jdbc, 날짜가_다른_기기.getId(), TODAY.minusDays(1), EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, 날짜가_다른_기기.getId(), TODAY, EmotionState.ANGRY, 100);

        // when
        리마인더를_만들고_발송한다(한국_시각("2026-10-26T23:00:00"));

        // then
        PushMessage message = 보낸_메시지(기본_등록_값);
        assertThat(message.data()).isEqualTo(Map.of("type", "COLLECTION_CLAIM_REMINDER", "date", "2026-10-26"));
        verify(pushSender).send(eq("other-state-token"), any());
        verify(pushSender, times(2)).send(anyString(), any());
        assertThat(기기의_작업들(jdbc, 이미_받은_기기.getId())).isEmpty();
        assertThat(기기의_작업들(jdbc, 조건이_모자란_기기.getId())).isEmpty();
        assertThat(기기의_작업들(jdbc, 날짜가_다른_기기.getId())).isEmpty();
    }

    @Test
    void 리마인더를_두_번_만들어도_기기마다_작업은_하나이고_한_번만_보낸다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        발급_가능하게_만든다(device, EmotionState.ANGRY);
        리마인더를_만들고_발송한다(한국_시각("2026-10-26T23:00:00"));

        // when
        리마인더를_만들고_발송한다(한국_시각("2026-10-26T23:00:05"));

        // then
        verify(pushSender, times(1)).send(anyString(), any());
        assertThat(기기의_작업들(jdbc, device.getId())).hasSize(1);
    }

    @Test
    void 발송기를_쓸_수_없으면_작업을_만들지_않는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        발급_가능하게_만든다(device, EmotionState.ANGRY);
        when(pushSender.isAvailable()).thenReturn(false);
        when(clock.instant()).thenReturn(한국_시각("2026-10-26T23:00:00"));

        // when
        service.saveClaimableOutbox();
        service.saveReminderOutbox();

        // then
        assertThat(기기의_작업들(jdbc, device.getId())).isEmpty();
    }

    private Device 토큰이_있는_기기(String token) {
        Device device = devices.saveAndFlush(기본_기기_빌더().build());
        만든_기기들.add(device.getId());
        pushTokens.save(기기_등록_값(device.getId(), token));
        return device;
    }

    private void 수신_설정을_바꾼다(Device device, NotificationSettings 새_설정) {
        notificationSettingService.updateSettings(
                device.getPublicId(), 새_설정.collectionClaimableEnabled(), 새_설정.rankChangedEnabled());
    }

    private void 발급_가능하게_만든다(Device device, EmotionState state) {
        일별_스탬프를_저장한다(jdbc, device.getId(), TODAY, state, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), TODAY, state, 100);
    }

    private void 카드를_받는다(Device device, EmotionState state) {
        cards.saveAndFlush(기본_카드_빌더().deviceId(device.getId()).cardDate(TODAY).state(state).build());
    }

    private void 탐색한다(Instant 지금) {
        when(clock.instant()).thenReturn(지금);
        service.saveClaimableOutbox();
    }

    private void 발송한다(Instant 지금) {
        when(clock.instant()).thenReturn(지금);
        모든_유형을_발송한다();
    }

    private void 탐색하고_발송한다(Instant 지금) {
        탐색한다(지금);
        모든_유형을_발송한다();
    }

    private void 리마인더를_만들고_발송한다(Instant 지금) {
        when(clock.instant()).thenReturn(지금);
        service.saveReminderOutbox();
        모든_유형을_발송한다();
    }

    private void 모든_유형을_발송한다() {
        for (NotificationOutboxType type : NotificationOutboxType.values()) {
            dispatcher.dispatch(type);
        }
    }

    private PushMessage 보낸_메시지(String token) {
        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender).send(eq(token), captor.capture());
        return captor.getValue();
    }

    private static Instant 한국_시각(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(서울).toInstant();
    }
}
