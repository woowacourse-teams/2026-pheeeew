package com.pheeeew.notification.application;

import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_카드_빌더;
import static com.pheeeew.collection.fixture.DeviceDailyStampFixture.일별_스탬프를_저장한다;
import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.DeviceDailyPressFixture.개인_프레스를_저장한다;
import static com.pheeeew.notification.fixture.PushTokenFixture.기본_등록_값;
import static com.pheeeew.notification.fixture.PushTokenFixture.새_등록_값;
import static org.assertj.core.api.Assertions.assertThat;
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
import com.pheeeew.notification.domain.repository.DeviceNotificationSettingRepository;
import com.pheeeew.notification.domain.repository.DevicePushTokenRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@PostgisDataJpaTest
@Import({CollectionNotificationService.class, CollectionProgressRepository.class, DevicePushTokenRepository.class,
        DeviceNotificationSettingRepository.class})
class CollectionNotificationServiceIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Instant 오전_10시 = Instant.parse("2026-10-26T01:00:00Z");

    @Autowired
    private CollectionNotificationService service;

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

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(pushSender.isAvailable()).thenReturn(true);
        when(pushSender.send(anyString(), any())).thenReturn(PushSendResult.SENT);
    }

    @Test
    void 발급_가능을_처음_확인하고_1시간이_지나야_한_번만_보낸다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        발급_가능하게_만든다(device, EmotionState.ANGRY);
        지금은(오전_10시);
        service.notifyClaimable();
        지금은(오전_10시.plusSeconds(59 * 60));
        service.notifyClaimable();
        verify(pushSender, never()).send(anyString(), any());

        // when
        지금은(오전_10시.plusSeconds(60 * 60));
        service.notifyClaimable();
        지금은(오전_10시.plusSeconds(3 * 60 * 60));
        service.notifyClaimable();

        // then
        PushMessage message = 보낸_메시지(기본_등록_값);
        assertThat(message.data()).isEqualTo(Map.of("type", "COLLECTION_CLAIMABLE", "date", "2026-10-26"));
    }

    @Test
    void 한_시간_안에_카드를_받으면_보내지_않는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        발급_가능하게_만든다(device, EmotionState.ANGRY);
        지금은(오전_10시);
        service.notifyClaimable();
        카드를_받는다(device, EmotionState.ANGRY);

        // when
        지금은(오전_10시.plusSeconds(60 * 60));
        service.notifyClaimable();

        // then
        verify(pushSender, never()).send(anyString(), any());
    }

    @Test
    void 카드를_받은_뒤_다른_감정의_조건을_채워도_다시_보내지_않는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        발급_가능하게_만든다(device, EmotionState.ANGRY);
        지금은(오전_10시);
        service.notifyClaimable();
        카드를_받는다(device, EmotionState.ANGRY);
        지금은(오전_10시.plusSeconds(30 * 60));
        service.notifyClaimable();
        발급_가능하게_만든다(device, EmotionState.EXHAUSTED);
        지금은(오전_10시.plusSeconds(5 * 60 * 60));
        service.notifyClaimable();

        // when
        지금은(오전_10시.plusSeconds(6 * 60 * 60));
        service.notifyClaimable();
        service.remindClaimable();

        // then
        verify(pushSender, never()).send(anyString(), any());
    }

    @Test
    void 날짜가_바뀌면_전날_발급_가능_기록을_버리고_새로_센다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        발급_가능하게_만든다(device, EmotionState.ANGRY);
        일별_스탬프를_저장한다(jdbc, device.getId(), TODAY.plusDays(1), EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), TODAY.plusDays(1), EmotionState.ANGRY, 100);
        지금은(Instant.parse("2026-10-26T14:30:00Z"));
        service.notifyClaimable();

        // when
        지금은(Instant.parse("2026-10-26T15:30:00Z"));
        service.notifyClaimable();
        verify(pushSender, never()).send(anyString(), any());
        지금은(Instant.parse("2026-10-26T16:30:00Z"));
        service.notifyClaimable();

        // then
        assertThat(보낸_메시지(기본_등록_값).data()).containsEntry("date", "2026-10-27");
    }

    @Test
    void 전날_알림을_받은_기기도_다음_날_발급_가능해지면_다시_받는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        발급_가능하게_만든다(device, EmotionState.ANGRY);
        일별_스탬프를_저장한다(jdbc, device.getId(), TODAY.plusDays(1), EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), TODAY.plusDays(1), EmotionState.ANGRY, 100);
        지금은(오전_10시);
        service.notifyClaimable();
        지금은(오전_10시.plusSeconds(60 * 60));
        service.notifyClaimable();

        // when
        지금은(오전_10시.plusSeconds(24 * 60 * 60));
        service.notifyClaimable();
        지금은(오전_10시.plusSeconds(25 * 60 * 60));
        service.notifyClaimable();

        // then
        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender, times(2)).send(eq(기본_등록_값), captor.capture());
        assertThat(captor.getAllValues()).extracting(message -> message.data().get("date"))
                .containsExactly("2026-10-26", "2026-10-27");
    }

    @Test
    void 수신_설정을_끈_기기와_토큰이_없는_기기에는_보내지_않는다() {
        // given
        Device 설정을_끈_기기 = 토큰이_있는_기기(기본_등록_값);
        settings.save(설정을_끈_기기.getId(), NotificationSettings.of(false, true), 오전_10시);
        Device 토큰이_없는_기기 = devices.saveAndFlush(기본_기기_빌더().build());
        Device 받을_기기 = 토큰이_있는_기기(새_등록_값);
        List.of(설정을_끈_기기, 토큰이_없는_기기, 받을_기기)
                .forEach(device -> 발급_가능하게_만든다(device, EmotionState.ANGRY));
        지금은(오전_10시);
        service.notifyClaimable();

        // when
        지금은(오전_10시.plusSeconds(60 * 60));
        service.notifyClaimable();

        // then
        verify(pushSender).send(eq(새_등록_값), any());
        verify(pushSender, times(1)).send(anyString(), any());
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
        지금은(Instant.parse("2026-10-26T14:00:00Z"));

        // when
        service.remindClaimable();

        // then
        PushMessage message = 보낸_메시지(기본_등록_값);
        assertThat(message.data()).isEqualTo(Map.of("type", "COLLECTION_CLAIM_REMINDER", "date", "2026-10-26"));
        verify(pushSender).send(eq("other-state-token"), any());
        verify(pushSender, times(2)).send(anyString(), any());
    }

    @Test
    void FCM이_무효_등록_값이라고_하면_그_값을_지운다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        발급_가능하게_만든다(device, EmotionState.ANGRY);
        when(pushSender.send(eq(기본_등록_값), any())).thenReturn(PushSendResult.UNREGISTERED);
        지금은(Instant.parse("2026-10-26T14:00:00Z"));

        // when
        service.remindClaimable();

        // then
        assertThat(등록_값_수(device)).isZero();
    }

    @Test
    void 한_기기에_보내다_실패해도_다른_기기에는_보낸다() {
        // given
        Device 실패할_기기 = 토큰이_있는_기기(기본_등록_값);
        Device 받을_기기 = 토큰이_있는_기기(새_등록_값);
        발급_가능하게_만든다(실패할_기기, EmotionState.ANGRY);
        발급_가능하게_만든다(받을_기기, EmotionState.ANGRY);
        when(pushSender.send(eq(기본_등록_값), any())).thenThrow(new IllegalStateException("FCM 장애"));
        지금은(Instant.parse("2026-10-26T14:00:00Z"));

        // when
        service.remindClaimable();

        // then
        verify(pushSender).send(eq(새_등록_값), any());
        assertThat(등록_값_수(실패할_기기)).isOne();
    }

    @Test
    void 발송기를_쓸_수_없으면_보내지_않는다() {
        // given
        Device device = 토큰이_있는_기기(기본_등록_값);
        발급_가능하게_만든다(device, EmotionState.ANGRY);
        when(pushSender.isAvailable()).thenReturn(false);
        지금은(Instant.parse("2026-10-26T14:00:00Z"));

        // when
        service.remindClaimable();

        // then
        verify(pushSender, never()).send(anyString(), any());
    }

    private Device 토큰이_있는_기기(String token) {
        Device device = devices.saveAndFlush(기본_기기_빌더().build());
        pushTokens.save(device.getId(), token, 오전_10시);
        return device;
    }

    private void 발급_가능하게_만든다(Device device, EmotionState state) {
        일별_스탬프를_저장한다(jdbc, device.getId(), TODAY, state, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), TODAY, state, 100);
    }

    private void 카드를_받는다(Device device, EmotionState state) {
        cards.saveAndFlush(기본_카드_빌더().deviceId(device.getId()).cardDate(TODAY).state(state).build());
    }

    private void 지금은(Instant instant) {
        when(clock.instant()).thenReturn(instant);
    }

    private PushMessage 보낸_메시지(String token) {
        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender).send(eq(token), captor.capture());
        return captor.getValue();
    }

    private long 등록_값_수(Device device) {
        return jdbc.sql("SELECT COUNT(*) FROM device_push_tokens WHERE device_id = :deviceId")
                .param("deviceId", device.getId())
                .query(Long.class)
                .single();
    }
}
