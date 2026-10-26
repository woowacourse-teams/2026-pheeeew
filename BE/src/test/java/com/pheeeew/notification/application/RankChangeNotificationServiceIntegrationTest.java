package com.pheeeew.notification.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.DeviceDailyPressFixture.개인_프레스를_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.notification.domain.NotificationSettings;
import com.pheeeew.notification.domain.PushMessage;
import com.pheeeew.notification.domain.PushSendResult;
import com.pheeeew.notification.domain.repository.DailyPressCountRepository;
import com.pheeeew.notification.domain.repository.DeviceNotificationSettingRepository;
import com.pheeeew.notification.domain.repository.DevicePushTokenRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@PostgisDataJpaTest
@Import({DailyPressCountRepository.class, DevicePushTokenRepository.class,
        DeviceNotificationSettingRepository.class})
class RankChangeNotificationServiceIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Instant 오전_10시 = Instant.parse("2026-10-26T01:00:00Z");
    private static final Instant 오전_11시 = Instant.parse("2026-10-26T02:00:00Z");

    @Autowired
    private DailyPressCountRepository dailyPressCounts;

    @Autowired
    private DeviceRepository devices;

    @Autowired
    private DevicePushTokenRepository pushTokens;

    @Autowired
    private DeviceNotificationSettingRepository settings;

    @Autowired
    private JdbcClient jdbc;

    private final PushSender pushSender = mock(PushSender.class);
    private final Clock clock = mock(Clock.class);
    private RankChangeNotificationService service;

    @BeforeEach
    void setUp() {
        service = new RankChangeNotificationService(dailyPressCounts, pushTokens, pushSender, clock);
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(pushSender.isAvailable()).thenReturn(true);
        when(pushSender.send(anyString(), any())).thenReturn(PushSendResult.SENT);
    }

    @Test
    void 그날_첫_확인은_기준만_잡고_보내지_않는다() {
        // given
        Device device = 토큰이_있는_기기("first");
        프레스를_누른다(device, 100);
        지금은(오전_10시);

        // when
        service.notifyRankChanged();

        // then
        verify(pushSender, never()).send(anyString(), any());
    }

    @Test
    void 상위_5위_안에서_내려간_기기에만_이전_순위와_현재_순위를_보내고_오른_기기에는_보내지_않는다() {
        // given
        Device 일위 = 토큰이_있는_기기("first");
        Device 이위 = 토큰이_있는_기기("second");
        Device 삼위 = 토큰이_있는_기기("third");
        프레스를_누른다(일위, 300);
        프레스를_누른다(이위, 200);
        프레스를_누른다(삼위, 100);
        지금은(오전_10시);
        service.notifyRankChanged();
        프레스를_누른다(삼위, 250);

        // when
        지금은(오전_11시);
        service.notifyRankChanged();

        // then
        Map<String, PushMessage> messages = 보낸_메시지들();
        assertThat(messages).containsOnlyKeys("first", "second");
        assertThat(messages.get("first").data()).isEqualTo(Map.of(
                "type", "RANK_DROPPED", "date", "2026-10-26", "previousRank", "1", "currentRank", "2"));
        assertThat(messages.get("first").body()).isEqualTo("오늘 프레스 순위가 1위에서 2위로 내려갔어요.");
        assertThat(messages.get("second").data()).containsEntry("previousRank", "2").containsEntry("currentRank", "3");
    }

    @Test
    void 오위에서_육위로_밀리면_보내고_육위에서_오위로_오르면_보내지_않는다() {
        // given
        List<Device> 기기들 = 순서대로_누른_기기들(600, 500, 400, 300, 200, 100, 90);
        지금은(오전_10시);
        service.notifyRankChanged();
        프레스를_누른다(기기들.get(5), 150);

        // when
        지금은(오전_11시);
        service.notifyRankChanged();

        // then
        Map<String, PushMessage> messages = 보낸_메시지들();
        assertThat(messages).containsOnlyKeys("rank-5");
        assertThat(messages.get("rank-5").data()).containsEntry("previousRank", "5").containsEntry("currentRank", "6");
    }

    @Test
    void 육위에서_칠위로_밀리는_것처럼_5위_밖에서만_내려가면_보내지_않는다() {
        // given
        List<Device> 기기들 = 순서대로_누른_기기들(600, 500, 400, 300, 200, 100, 90);
        지금은(오전_10시);
        service.notifyRankChanged();
        프레스를_누른다(기기들.get(6), 20);

        // when
        지금은(오전_11시);
        service.notifyRankChanged();

        // then
        assertThat(보낸_메시지들()).isEmpty();
    }

    @Test
    void 처음_누른_기기가_5위_안에_들어와도_그_기기에는_보내지_않고_밀려난_기기에_보낸다() {
        // given
        Device 기존_기기 = 토큰이_있는_기기("existing");
        프레스를_누른다(기존_기기, 100);
        Device 새_기기 = 토큰이_있는_기기("newcomer");
        지금은(오전_10시);
        service.notifyRankChanged();
        프레스를_누른다(새_기기, 150);

        // when
        지금은(오전_11시);
        service.notifyRankChanged();

        // then
        Map<String, PushMessage> messages = 보낸_메시지들();
        assertThat(messages).containsOnlyKeys("existing");
        assertThat(messages.get("existing").data()).containsEntry("previousRank", "1").containsEntry("currentRank", "2");
    }

    @Test
    void 동점은_같은_순위로_본다() {
        // given
        Device 첫_기기 = 토큰이_있는_기기("first");
        Device 둘째_기기 = 토큰이_있는_기기("second");
        Device 셋째_기기 = 토큰이_있는_기기("third");
        프레스를_누른다(첫_기기, 100);
        프레스를_누른다(둘째_기기, 100);
        프레스를_누른다(셋째_기기, 50);
        지금은(오전_10시);
        service.notifyRankChanged();
        프레스를_누른다(셋째_기기, 50);

        // when
        지금은(오전_11시);
        service.notifyRankChanged();
        Device 새_기기 = 토큰이_있는_기기("newcomer");
        프레스를_누른다(새_기기, 150);
        지금은(Instant.parse("2026-10-26T03:00:00Z"));
        service.notifyRankChanged();

        // then
        Map<String, PushMessage> messages = 보낸_메시지들();
        assertThat(messages).containsOnlyKeys("first", "second", "third");
        assertThat(messages.values()).allSatisfy(message -> assertThat(message.data())
                .containsEntry("previousRank", "1").containsEntry("currentRank", "2"));
    }

    @Test
    void 날짜가_바뀌면_자정의_집계_초기화는_변동으로_보지_않는다() {
        // given
        Device 일위 = 토큰이_있는_기기("first");
        Device 이위 = 토큰이_있는_기기("second");
        프레스를_누른다(일위, 200);
        프레스를_누른다(이위, 100);
        지금은(Instant.parse("2026-10-26T14:00:00Z"));
        service.notifyRankChanged();
        개인_프레스를_저장한다(jdbc, 이위.getId(), TODAY.plusDays(1), EmotionState.ANGRY, 10);

        // when
        지금은(Instant.parse("2026-10-26T15:00:00Z"));
        service.notifyRankChanged();
        verify(pushSender, never()).send(anyString(), any());
        개인_프레스를_저장한다(jdbc, 일위.getId(), TODAY.plusDays(1), EmotionState.ANGRY, 20);
        지금은(Instant.parse("2026-10-26T16:00:00Z"));
        service.notifyRankChanged();

        // then
        Map<String, PushMessage> messages = 보낸_메시지들();
        assertThat(messages).containsOnlyKeys("second");
        assertThat(messages.get("second").data()).containsEntry("date", "2026-10-27")
                .containsEntry("previousRank", "1").containsEntry("currentRank", "2");
    }

    @Test
    void 수신_설정을_끈_기기와_토큰이_없는_기기와_프레스가_없는_기기에는_보내지_않는다() {
        // given
        Device 설정을_끈_기기 = 토큰이_있는_기기("off");
        settings.save(설정을_끈_기기.getId(), NotificationSettings.of(true, false), 오전_10시);
        Device 토큰이_없는_기기 = devices.saveAndFlush(기본_기기_빌더().build());
        Device 받을_기기 = 토큰이_있는_기기("on");
        토큰이_있는_기기("no-press");
        List.of(설정을_끈_기기, 토큰이_없는_기기, 받을_기기).forEach(device -> 프레스를_누른다(device, 100));
        Device 추월할_기기 = 토큰이_있는_기기("overtaker");
        지금은(오전_10시);
        service.notifyRankChanged();
        프레스를_누른다(추월할_기기, 200);

        // when
        지금은(오전_11시);
        service.notifyRankChanged();

        // then
        assertThat(보낸_메시지들()).containsOnlyKeys("on");
    }

    @Test
    void FCM이_무효_등록_값이라고_하면_그_값을_지운다() {
        // given
        Device 일위 = 토큰이_있는_기기("first");
        Device 이위 = 토큰이_있는_기기("second");
        프레스를_누른다(일위, 200);
        프레스를_누른다(이위, 100);
        지금은(오전_10시);
        service.notifyRankChanged();
        프레스를_누른다(이위, 200);
        when(pushSender.send(eq("first"), any())).thenReturn(PushSendResult.UNREGISTERED);

        // when
        지금은(오전_11시);
        service.notifyRankChanged();

        // then
        assertThat(등록_값_수(일위)).isZero();
        assertThat(등록_값_수(이위)).isOne();
    }

    @Test
    void 같은_날_순위가_그대로면_다음_비교에서_다시_보내지_않는다() {
        // given
        Device 일위 = 토큰이_있는_기기("first");
        Device 이위 = 토큰이_있는_기기("second");
        프레스를_누른다(일위, 200);
        프레스를_누른다(이위, 100);
        지금은(오전_10시);
        service.notifyRankChanged();
        프레스를_누른다(이위, 200);
        지금은(오전_11시);
        service.notifyRankChanged();

        // when
        지금은(Instant.parse("2026-10-26T03:00:00Z"));
        service.notifyRankChanged();

        // then
        assertThat(보낸_메시지들()).containsOnlyKeys("first");
    }

    @Test
    void 순위는_여러_감정의_프레스를_합해서_매긴다() {
        // given
        Device 여러_감정_기기 = 토큰이_있는_기기("many");
        Device 한_감정_기기 = 토큰이_있는_기기("single");
        개인_프레스를_저장한다(jdbc, 여러_감정_기기.getId(), TODAY, EmotionState.ANGRY, 60);
        개인_프레스를_저장한다(jdbc, 한_감정_기기.getId(), TODAY, EmotionState.ANGRY, 100);
        지금은(오전_10시);
        service.notifyRankChanged();
        개인_프레스를_저장한다(jdbc, 여러_감정_기기.getId(), TODAY, EmotionState.EXHAUSTED, 60);

        // when
        지금은(오전_11시);
        service.notifyRankChanged();

        // then
        Map<String, PushMessage> messages = 보낸_메시지들();
        assertThat(messages).containsOnlyKeys("single");
        assertThat(messages.get("single").data()).containsEntry("previousRank", "1").containsEntry("currentRank", "2");
    }

    @Test
    void 한_기기에_보내다_실패해도_다른_기기에는_보낸다() {
        // given
        List<Device> 기기들 = 순서대로_누른_기기들(300, 200, 100);
        지금은(오전_10시);
        service.notifyRankChanged();
        프레스를_누른다(기기들.get(2), 300);
        when(pushSender.send(eq("rank-1"), any())).thenThrow(new IllegalStateException("FCM 장애"));

        // when
        지금은(오전_11시);
        service.notifyRankChanged();

        // then
        verify(pushSender).send(eq("rank-2"), any());
    }

    @Test
    void 발송기를_쓸_수_없으면_보내지_않는다() {
        // given
        Device 일위 = 토큰이_있는_기기("first");
        Device 이위 = 토큰이_있는_기기("second");
        프레스를_누른다(일위, 200);
        프레스를_누른다(이위, 100);
        지금은(오전_10시);
        service.notifyRankChanged();
        프레스를_누른다(이위, 200);
        when(pushSender.isAvailable()).thenReturn(false);

        // when
        지금은(오전_11시);
        service.notifyRankChanged();

        // then
        verify(pushSender, never()).send(anyString(), any());
    }

    private Device 토큰이_있는_기기(String token) {
        Device device = devices.saveAndFlush(기본_기기_빌더().build());
        pushTokens.save(device.getId(), token, 오전_10시);
        return device;
    }

    private List<Device> 순서대로_누른_기기들(long... counts) {
        List<Device> 기기들 = IntStream.rangeClosed(1, counts.length)
                .mapToObj(rank -> 토큰이_있는_기기("rank-" + rank))
                .toList();
        IntStream.range(0, counts.length).forEach(index -> 프레스를_누른다(기기들.get(index), counts[index]));
        return 기기들;
    }

    private void 프레스를_누른다(Device device, long count) {
        개인_프레스를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, count);
    }

    private void 지금은(Instant instant) {
        when(clock.instant()).thenReturn(instant);
    }

    private Map<String, PushMessage> 보낸_메시지들() {
        ArgumentCaptor<String> tokens = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PushMessage> messages = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender, atLeast(0)).send(tokens.capture(), messages.capture());
        Map<String, PushMessage> sent = new HashMap<>();
        for (int index = 0; index < tokens.getAllValues().size(); index++) {
            sent.put(tokens.getAllValues().get(index), messages.getAllValues().get(index));
        }
        assertThat(sent).hasSameSizeAs(tokens.getAllValues());
        return sent;
    }

    private long 등록_값_수(Device device) {
        return jdbc.sql("SELECT COUNT(*) FROM device_push_tokens WHERE device_id = :deviceId")
                .param("deviceId", device.getId())
                .query(Long.class)
                .single();
    }
}
