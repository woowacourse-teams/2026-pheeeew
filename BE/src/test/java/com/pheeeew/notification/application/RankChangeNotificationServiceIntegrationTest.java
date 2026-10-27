package com.pheeeew.notification.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.DeviceDailyPressFixture.개인_프레스를_저장한다;
import static com.pheeeew.notification.fixture.NotificationOutboxFixture.기기의_작업;
import static com.pheeeew.notification.fixture.NotificationOutboxFixture.기기의_작업들;
import static com.pheeeew.notification.fixture.NotificationOutboxFixture.날짜의_회차들;
import static com.pheeeew.notification.fixture.NotificationOutboxFixture.회차의_스냅샷;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.notification.domain.NotificationOutboxType;
import com.pheeeew.notification.domain.NotificationSettings;
import com.pheeeew.notification.domain.PushMessage;
import com.pheeeew.notification.domain.PushSendResult;
import com.pheeeew.notification.domain.repository.DailyPressCountRepository;
import com.pheeeew.notification.domain.repository.DeviceNotificationSettingRepository;
import com.pheeeew.notification.domain.repository.DevicePushTokenRepository;
import com.pheeeew.notification.domain.repository.NotificationOutboxRepository;
import com.pheeeew.notification.domain.repository.NotificationRankRoundRepository;
import com.pheeeew.notification.fixture.NotificationOutboxFixture.작업;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@PostgisDataJpaTest
@Import({RankChangeNotificationService.class, NotificationDispatcher.class, NotificationOutboxRepository.class,
        NotificationRankRoundRepository.class, DailyPressCountRepository.class, DevicePushTokenRepository.class,
        DeviceNotificationSettingRepository.class})
class RankChangeNotificationServiceIntegrationTest {

    private static final ZoneId 서울 = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Instant 오전_10시 = 한국_시각("2026-10-26T10:00:00");
    private static final Instant 오전_10시_5분 = 한국_시각("2026-10-26T10:05:00");
    private static final Instant 오전_10시_10분 = 한국_시각("2026-10-26T10:10:00");

    @Autowired
    private RankChangeNotificationService service;

    @Autowired
    private NotificationDispatcher dispatcher;

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
    void 그날_첫_회차는_5위_이내_스냅샷만_저장하고_작업을_만들지_않는다() {
        // given
        List<Device> 기기들 = 순서대로_누른_기기들(300, 200, 100);

        // when
        비교한다(오전_10시.plusSeconds(30));

        // then
        assertThat(날짜의_회차들(jdbc, TODAY)).containsExactly(오전_10시);
        assertThat(회차의_스냅샷(jdbc, 오전_10시)).isEqualTo(Map.of(
                기기들.get(0).getId(), 1, 기기들.get(1).getId(), 2, 기기들.get(2).getId(), 3));
        assertThat(기기들).allSatisfy(device -> assertThat(기기의_작업들(jdbc, device.getId())).isEmpty());
    }

    @Test
    void 상위_5위_안에서_내려간_기기에만_회차_작업을_만들고_오른_기기에는_만들지_않는다() {
        // given
        List<Device> 기기들 = 순서대로_누른_기기들(300, 200, 100);
        비교한다(오전_10시);
        프레스를_누른다(기기들.get(2), 250);

        // when
        비교한다(오전_10시_5분.plusSeconds(3));
        발송한다(오전_10시_5분.plusSeconds(10));

        // then
        작업 일위_작업 = 기기의_작업(jdbc, 기기들.get(0).getId(), "RANK_DROPPED");
        assertThat(일위_작업.roundAt()).isEqualTo(오전_10시_5분);
        assertThat(일위_작업.expiresAt()).isEqualTo(오전_10시_10분);
        assertThat(일위_작업.targetDate()).isEqualTo(TODAY);
        assertThat(기기의_작업들(jdbc, 기기들.get(2).getId())).isEmpty();
        Map<String, PushMessage> messages = 보낸_메시지들();
        assertThat(messages).containsOnlyKeys("rank-1", "rank-2");
        assertThat(messages.get("rank-1").data()).isEqualTo(Map.of(
                "type", "RANK_DROPPED", "date", "2026-10-26", "previousRank", "1", "currentRank", "2"));
        assertThat(messages.get("rank-2").data()).containsEntry("previousRank", "2").containsEntry("currentRank", "3");
    }

    @Test
    void 오위에서_육위로_밀리면_작업을_만들고_육위에서_오위로_오른_기기는_만들지_않는다() {
        // given
        List<Device> 기기들 = 순서대로_누른_기기들(600, 500, 400, 300, 200, 100, 90);
        비교한다(오전_10시);
        프레스를_누른다(기기들.get(5), 150);

        // when
        비교한다(오전_10시_5분);

        // then
        assertThat(순위_작업들(기기들)).extracting(작업::deviceId, 작업::previousRank, 작업::currentRank)
                .containsExactly(tuple(기기들.get(4).getId(), 5, 6));
    }

    @Test
    void 육위에서_칠위로_밀리는_것처럼_5위_밖에서만_내려가면_작업을_만들지_않는다() {
        // given
        List<Device> 기기들 = 순서대로_누른_기기들(600, 500, 400, 300, 200, 100, 90);
        비교한다(오전_10시);
        프레스를_누른다(기기들.get(6), 20);

        // when
        비교한다(오전_10시_5분);

        // then
        assertThat(순위_작업들(기기들)).isEmpty();
    }

    @Test
    void 처음_누른_기기가_5위_안에_들어와도_그_기기가_아니라_밀려난_기기에_작업을_만든다() {
        // given
        Device 기존_기기 = 토큰이_있는_기기("existing");
        프레스를_누른다(기존_기기, 100);
        Device 새_기기 = 토큰이_있는_기기("newcomer");
        비교한다(오전_10시);
        프레스를_누른다(새_기기, 150);

        // when
        비교한다(오전_10시_5분);

        // then
        assertThat(순위_작업들(List.of(기존_기기, 새_기기))).extracting(작업::deviceId, 작업::previousRank, 작업::currentRank)
                .containsExactly(tuple(기존_기기.getId(), 1, 2));
    }

    @Test
    void 동점은_공동_순위로_보고_공동_1위가_함께_2위로_밀리면_모두에게_작업을_만든다() {
        // given
        List<Device> 기기들 = 순서대로_누른_기기들(100, 100, 50);
        비교한다(오전_10시);
        프레스를_누른다(기기들.get(2), 50);
        비교한다(오전_10시_5분);
        assertThat(순위_작업들(기기들)).isEmpty();
        Device 새_기기 = 토큰이_있는_기기("newcomer");
        프레스를_누른다(새_기기, 150);

        // when
        비교한다(오전_10시_10분);

        // then
        assertThat(순위_작업들(기기들)).extracting(작업::previousRank, 작업::currentRank)
                .containsExactly(tuple(1, 2), tuple(1, 2), tuple(1, 2));
    }

    @Test
    void 공동_5위가_여러_명이면_스냅샷에_모두_저장하고_그_아래는_저장하지_않는다() {
        // given
        List<Device> 기기들 = 순서대로_누른_기기들(600, 500, 400, 300, 200, 200, 200, 100);

        // when
        비교한다(오전_10시);

        // then
        Map<Long, Integer> snapshot = 회차의_스냅샷(jdbc, 오전_10시);
        assertThat(snapshot).hasSize(7);
        assertThat(snapshot).containsEntry(기기들.get(4).getId(), 5).containsEntry(기기들.get(6).getId(), 5)
                .doesNotContainKey(기기들.get(7).getId());
    }

    @Test
    void 연속으로_내려가면_회차마다_그_회차의_이전_순위와_현재_순위로_작업을_만든다() {
        // given
        List<Device> 기기들 = 순서대로_누른_기기들(300, 200, 100);
        Device 이위 = 기기들.get(1);
        비교한다(오전_10시.plusSeconds(7));
        프레스를_누른다(기기들.get(2), 150);
        비교한다(오전_10시_5분.plusSeconds(12));
        Device 새_기기 = 토큰이_있는_기기("newcomer");
        프레스를_누른다(새_기기, 260);

        // when
        비교한다(오전_10시_10분.plusSeconds(4));

        // then
        assertThat(기기의_작업들(jdbc, 이위.getId()))
                .extracting(작업::roundAt, 작업::previousRank, 작업::currentRank)
                .containsExactly(tuple(오전_10시_5분, 2, 3), tuple(오전_10시_10분, 3, 4));
    }

    @Test
    void 회차를_놓치면_같은_날_마지막_회차의_스냅샷과_비교한다() {
        // given
        List<Device> 기기들 = 순서대로_누른_기기들(300, 200, 100);
        비교한다(오전_10시);
        프레스를_누른다(기기들.get(2), 250);
        Instant 반 = 한국_시각("2026-10-26T10:30:00");

        // when
        비교한다(반);

        // then
        assertThat(날짜의_회차들(jdbc, TODAY)).containsExactly(오전_10시, 반);
        assertThat(순위_작업들(기기들)).extracting(작업::roundAt, 작업::previousRank, 작업::currentRank)
                .containsExactly(tuple(반, 1, 2), tuple(반, 2, 3));
    }

    @Test
    void 크론이_회차_경계보다_조금_일찍_돌아도_그_경계의_회차로_비교한다() {
        // given
        List<Device> 기기들 = 순서대로_누른_기기들(300, 200, 100);
        비교한다(오전_10시);
        프레스를_누른다(기기들.get(2), 250);

        // when
        비교한다(오전_10시_5분.minusMillis(5));

        // then
        assertThat(날짜의_회차들(jdbc, TODAY)).containsExactly(오전_10시, 오전_10시_5분);
        assertThat(순위_작업들(기기들)).extracting(작업::roundAt, 작업::previousRank, 작업::currentRank)
                .containsExactly(tuple(오전_10시_5분, 1, 2), tuple(오전_10시_5분, 2, 3));
    }

    @Test
    void 날짜가_바뀐_첫_회차는_자정의_집계_초기화를_하락으로_보지_않고_기준만_잡는다() {
        // given
        Device 일위 = 토큰이_있는_기기("first");
        Device 이위 = 토큰이_있는_기기("second");
        프레스를_누른다(일위, 200);
        프레스를_누른다(이위, 100);
        비교한다(한국_시각("2026-10-26T23:55:00"));
        개인_프레스를_저장한다(jdbc, 이위.getId(), TODAY.plusDays(1), EmotionState.ANGRY, 10);
        비교한다(한국_시각("2026-10-27T00:00:00"));
        assertThat(순위_작업들(List.of(일위, 이위))).isEmpty();
        개인_프레스를_저장한다(jdbc, 일위.getId(), TODAY.plusDays(1), EmotionState.ANGRY, 20);

        // when
        비교한다(한국_시각("2026-10-27T00:05:00"));

        // then
        assertThat(순위_작업들(List.of(일위, 이위))).extracting(작업::deviceId, 작업::targetDate, 작업::previousRank,
                        작업::currentRank)
                .containsExactly(tuple(이위.getId(), TODAY.plusDays(1), 1, 2));
    }

    @Test
    void 같은_회차를_다시_비교하면_스냅샷과_작업을_바꾸지_않는다() {
        // given
        List<Device> 기기들 = 순서대로_누른_기기들(300, 200, 100);
        비교한다(오전_10시);
        Map<Long, Integer> 처음_스냅샷 = 회차의_스냅샷(jdbc, 오전_10시);
        프레스를_누른다(기기들.get(2), 250);

        // when
        비교한다(오전_10시.plusSeconds(240));

        // then
        assertThat(날짜의_회차들(jdbc, TODAY)).containsExactly(오전_10시);
        assertThat(회차의_스냅샷(jdbc, 오전_10시)).isEqualTo(처음_스냅샷);
        assertThat(순위_작업들(기기들)).isEmpty();
    }

    @Test
    void 하락_작업을_만든_뒤_순위를_회복해도_취소하지_않고_보낸다() {
        // given
        List<Device> 기기들 = 순서대로_누른_기기들(200, 100);
        비교한다(오전_10시);
        프레스를_누른다(기기들.get(1), 150);
        비교한다(오전_10시_5분);
        프레스를_누른다(기기들.get(0), 500);

        // when
        발송한다(오전_10시_5분.plusSeconds(20));

        // then
        Map<String, PushMessage> messages = 보낸_메시지들();
        assertThat(messages).containsOnlyKeys("rank-1");
        assertThat(messages.get("rank-1").data()).containsEntry("previousRank", "1").containsEntry("currentRank", "2");
    }

    @Test
    void 수신_설정을_끈_기기와_토큰이_없는_기기는_발송_직전에_사유를_나눠_닫고_프레스가_없는_기기는_작업이_없다() {
        // given
        Device 설정을_끈_기기 = 토큰이_있는_기기("off");
        settings.save(설정을_끈_기기.getId(), NotificationSettings.of(true, false), 오전_10시);
        Device 토큰이_없는_기기 = devices.saveAndFlush(기본_기기_빌더().build());
        Device 받을_기기 = 토큰이_있는_기기("on");
        Device 프레스가_없는_기기 = 토큰이_있는_기기("no-press");
        List.of(설정을_끈_기기, 토큰이_없는_기기, 받을_기기).forEach(device -> 프레스를_누른다(device, 100));
        Device 추월할_기기 = 토큰이_있는_기기("overtaker");
        비교한다(오전_10시);
        프레스를_누른다(추월할_기기, 200);
        비교한다(오전_10시_5분);

        // when
        발송한다(오전_10시_5분.plusSeconds(10));

        // then
        assertThat(보낸_메시지들()).containsOnlyKeys("on");
        작업 설정을_끈_기기의_작업 = 기기의_작업(jdbc, 설정을_끈_기기.getId(), "RANK_DROPPED");
        assertThat(설정을_끈_기기의_작업.status()).isEqualTo("CANCELED");
        assertThat(설정을_끈_기기의_작업.resultCode()).isEqualTo("NOTIFICATION_DISABLED");
        작업 토큰이_없는_기기의_작업 = 기기의_작업(jdbc, 토큰이_없는_기기.getId(), "RANK_DROPPED");
        assertThat(토큰이_없는_기기의_작업.status()).isEqualTo("CANCELED");
        assertThat(토큰이_없는_기기의_작업.resultCode()).isEqualTo("NO_TOKEN");
        assertThat(기기의_작업들(jdbc, 프레스가_없는_기기.getId())).isEmpty();
    }

    @Test
    void 같은_날_순위가_그대로면_다음_회차에서_다시_작업을_만들지_않는다() {
        // given
        List<Device> 기기들 = 순서대로_누른_기기들(200, 100);
        비교한다(오전_10시);
        프레스를_누른다(기기들.get(1), 200);
        비교한다(오전_10시_5분);

        // when
        비교한다(오전_10시_10분);

        // then
        assertThat(순위_작업들(기기들)).extracting(작업::deviceId, 작업::roundAt)
                .containsExactly(tuple(기기들.get(0).getId(), 오전_10시_5분));
    }

    @Test
    void 순위는_여러_감정의_프레스를_합해서_매긴다() {
        // given
        Device 여러_감정_기기 = 토큰이_있는_기기("many");
        Device 한_감정_기기 = 토큰이_있는_기기("single");
        개인_프레스를_저장한다(jdbc, 여러_감정_기기.getId(), TODAY, EmotionState.ANGRY, 60);
        개인_프레스를_저장한다(jdbc, 한_감정_기기.getId(), TODAY, EmotionState.ANGRY, 100);
        비교한다(오전_10시);
        개인_프레스를_저장한다(jdbc, 여러_감정_기기.getId(), TODAY, EmotionState.EXHAUSTED, 60);

        // when
        비교한다(오전_10시_5분);

        // then
        assertThat(순위_작업들(List.of(여러_감정_기기, 한_감정_기기)))
                .extracting(작업::deviceId, 작업::previousRank, 작업::currentRank)
                .containsExactly(tuple(한_감정_기기.getId(), 1, 2));
    }

    @Test
    void 발송기를_쓸_수_없으면_회차를_남기지_않는다() {
        // given
        순서대로_누른_기기들(200, 100);
        when(pushSender.isAvailable()).thenReturn(false);

        // when
        비교한다(오전_10시);

        // then
        assertThat(날짜의_회차들(jdbc, TODAY)).isEmpty();
    }

    @Test
    void 보관_기간_7일이_지난_회차와_스냅샷만_지운다() {
        // given
        Device device = 토큰이_있는_기기("old");
        for (LocalDate 날짜 : List.of(TODAY.minusDays(8), TODAY.minusDays(7))) {
            개인_프레스를_저장한다(jdbc, device.getId(), 날짜, EmotionState.ANGRY, 10);
            비교한다(날짜.atTime(10, 0).atZone(서울).toInstant());
        }
        when(clock.instant()).thenReturn(한국_시각("2026-10-26T04:00:00"));

        // when
        service.deleteOldRounds();

        // then
        Instant 여드레_전_회차 = TODAY.minusDays(8).atTime(10, 0).atZone(서울).toInstant();
        Instant 이레_전_회차 = TODAY.minusDays(7).atTime(10, 0).atZone(서울).toInstant();
        assertThat(날짜의_회차들(jdbc, TODAY.minusDays(8))).isEmpty();
        assertThat(회차의_스냅샷(jdbc, 여드레_전_회차)).isEmpty();
        assertThat(날짜의_회차들(jdbc, TODAY.minusDays(7))).containsExactly(이레_전_회차);
        assertThat(회차의_스냅샷(jdbc, 이레_전_회차)).containsEntry(device.getId(), 1);
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

    private void 비교한다(Instant 지금) {
        when(clock.instant()).thenReturn(지금);
        service.compareRanks();
    }

    private void 발송한다(Instant 지금) {
        when(clock.instant()).thenReturn(지금);
        for (NotificationOutboxType type : NotificationOutboxType.values()) {
            dispatcher.dispatch(type);
        }
    }

    private List<작업> 순위_작업들(List<Device> 기기들) {
        return 기기들.stream()
                .flatMap(device -> 기기의_작업들(jdbc, device.getId()).stream())
                .filter(job -> job.type().equals("RANK_DROPPED"))
                .toList();
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

    private static Instant 한국_시각(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(서울).toInstant();
    }
}
