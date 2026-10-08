package com.pheeeew.groups.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.DeviceDailyPressFixture.개인_프레스를_저장한다;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionEmojiRepository;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.groups.application.dto.GroupPressRankingItem;
import com.pheeeew.groups.application.dto.GroupPressRankingResult;
import com.pheeeew.groups.application.dto.GroupRankingItem;
import com.pheeeew.groups.application.dto.GroupRankingResult;
import com.pheeeew.groups.application.dto.GroupResult;
import com.pheeeew.groups.application.dto.GroupStampCommand;
import com.pheeeew.groups.application.dto.GroupStatePressRankingResult;
import com.pheeeew.groups.domain.GroupStamp;
import com.pheeeew.groups.domain.StampFrame;
import com.pheeeew.groups.domain.repository.GroupMemberRepository;
import com.pheeeew.groups.domain.repository.GroupRepository;
import com.pheeeew.groups.domain.repository.GroupStampRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GroupRankingIntegrationTest {

    @Autowired
    private GroupRankingService groupRankingService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupStampRepository groupStampRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private EmotionRepository emotionRepository;

    @Autowired
    private EmotionEmojiRepository emotionEmojiRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() {
        검증용_지역_계층을_저장한다(jdbcClient);
    }

    @AfterEach
    void tearDown() {
        emotionEmojiRepository.deleteAllInBatch();
        emotionRepository.deleteAllInBatch();
        jdbcClient.sql("DELETE FROM device_daily_presses").update();
        jdbcClient.sql("DELETE FROM group_daily_presses").update();
        jdbcClient.sql("DELETE FROM regions").update();
        groupMemberRepository.deleteAllInBatch();
        groupStampRepository.deleteAllInBatch();
        groupRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
        jdbcClient.sql("DELETE FROM regions").update();
    }

    @Test
    void 그룹별로_이번주_감정_수를_센다() {
        // given
        GroupResult 많은_그룹 = 그룹을_만든다("많은모임");
        GroupResult 적은_그룹 = 그룹을_만든다("적은모임");
        이번주_감정을_남긴다(많은_그룹, 3);
        이번주_감정을_남긴다(적은_그룹, 1);

        // when
        GroupRankingResult 결과 = groupRankingService.findGroupRanking(0);

        // then
        assertThat(결과.items()).hasSize(2);
        assertThat(결과.items().getFirst().name()).isEqualTo("많은모임");
        assertThat(결과.items().getFirst().score()).isEqualTo(3);
        assertThat(결과.items().getFirst().rank()).isOne();
        assertThat(결과.items().getLast().score()).isOne();
        assertThat(결과.items().getLast().rank()).isEqualTo(2);
    }

    @Test
    void 그룹을_고르지_않은_개인_감정은_어느_그룹_점수에도_들어가지_않는다() {
        // given
        GroupResult 그룹 = 그룹을_만든다("한숨모임");
        이번주_감정을_남긴다(그룹, 1);
        개인_감정을_남긴다();

        // when
        GroupRankingResult 결과 = groupRankingService.findGroupRanking(0);

        // then
        assertThat(결과.items()).hasSize(1);
        assertThat(결과.items().getFirst().score()).isOne();
    }

    @Test
    void 삭제한_감정은_점수에서_빠진다() {
        // given
        GroupResult 그룹 = 그룹을_만든다("한숨모임");
        List<Emotion> 감정들 = 이번주_감정을_남긴다(그룹, 3);
        삭제한다(감정들.getFirst().getId());

        // when
        GroupRankingResult 결과 = groupRankingService.findGroupRanking(0);

        // then
        assertThat(결과.items().getFirst().score()).isEqualTo(2);
    }

    @Test
    void 점수가_같으면_공동_순위를_준다() {
        // given
        이번주_감정을_남긴다(그룹을_만든다("가모임"), 2);
        이번주_감정을_남긴다(그룹을_만든다("나모임"), 2);
        이번주_감정을_남긴다(그룹을_만든다("다모임"), 1);

        // when
        List<GroupRankingItem> 순위표 = groupRankingService.findGroupRanking(0).items();

        // then
        assertThat(순위표).extracting(GroupRankingItem::rank).containsExactly(1, 1, 3);
    }

    @Test
    void 이번주에_감정이_없는_그룹은_순위표에_나오지_않는다() {
        // given
        GroupResult 활동한_그룹 = 그룹을_만든다("활동모임");
        그룹을_만든다("조용한모임");
        이번주_감정을_남긴다(활동한_그룹, 1);

        // when
        GroupRankingResult 결과 = groupRankingService.findGroupRanking(0);

        // then
        assertThat(결과.items()).hasSize(1);
        assertThat(결과.items().getFirst().name()).isEqualTo("활동모임");
    }

    @Test
    void 지난주_감정은_이번주_점수에_들어가지_않고_지난주로_조회된다() {
        // given
        GroupResult 그룹 = 그룹을_만든다("한숨모임");
        지난주_감정을_남긴다(그룹, 2);

        // when
        GroupRankingResult 이번주 = groupRankingService.findGroupRanking(0);
        GroupRankingResult 지난주 = groupRankingService.findGroupRanking(1);

        // then
        assertThat(이번주.items()).isEmpty();
        assertThat(지난주.items()).hasSize(1);
        assertThat(지난주.items().getFirst().score()).isEqualTo(2);
    }

    @Test
    void 더_이전에_기록이_있으면_뒤로_더_갈_수_있다고_알린다() {
        // given
        GroupResult 그룹 = 그룹을_만든다("한숨모임");
        감정을_남긴다(그룹, 1, 2);

        // when
        GroupRankingResult 이번주 = groupRankingService.findGroupRanking(0);
        GroupRankingResult 이주_전 = groupRankingService.findGroupRanking(2);

        // then
        assertThat(이번주.hasPrevious()).isTrue();
        assertThat(이번주.items()).isEmpty();
        assertThat(이주_전.hasPrevious()).isFalse();
        assertThat(이주_전.items()).hasSize(1);
    }

    @Test
    void 기록이_하나도_없으면_뒤로_갈_곳이_없다() {
        // given
        그룹을_만든다("조용한모임");

        // when
        GroupRankingResult 결과 = groupRankingService.findGroupRanking(0);

        // then
        assertThat(결과.hasPrevious()).isFalse();
        assertThat(결과.items()).isEmpty();
    }

    @Test
    void 몇_주_전인지를_응답에_그대로_담는다() {
        // when
        GroupRankingResult 결과 = groupRankingService.findGroupRanking(7);

        // then
        assertThat(결과.weeksAgo()).isEqualTo(7);
        assertThat(결과.startAt()).isEqualTo(RankingWeek.of(Instant.now(), 7).startAt());
    }

    @Test
    void 주의_끝은_다음_주의_시작과_맞닿는다() {
        // when
        GroupRankingResult 이번주 = groupRankingService.findGroupRanking(0);
        GroupRankingResult 지난주 = groupRankingService.findGroupRanking(1);

        // then
        assertThat(지난주.endAt()).isEqualTo(이번주.startAt());
    }

    @Test
    void 프레스_랭킹은_그룹별로_이번주_누른_수를_합친다() {
        // given
        GroupResult 많은_그룹 = 그룹을_만든다("많은모임");
        GroupResult 적은_그룹 = 그룹을_만든다("적은모임");
        눌린_것으로_둔다(많은_그룹, 이번_주_월요일(), EmotionState.ANGRY, 3);
        눌린_것으로_둔다(많은_그룹, 이번_주_월요일().plusDays(2), EmotionState.EXHAUSTED, 2);
        눌린_것으로_둔다(적은_그룹, 이번_주_월요일(), EmotionState.ANGRY, 1);

        // when
        GroupPressRankingResult 결과 = groupRankingService.findPressRanking(남의_기기(), 0);

        // then
        assertThat(결과.items()).extracting(GroupPressRankingItem::name, GroupPressRankingItem::score)
                .containsExactly(tuple("많은모임", 5L), tuple("적은모임", 1L));
    }

    @Test
    void 프레스_랭킹도_동점이면_공동_순위를_준다() {
        // given
        눌린_것으로_둔다(그룹을_만든다("가모임"), 이번_주_월요일(), EmotionState.ANGRY, 2);
        눌린_것으로_둔다(그룹을_만든다("나모임"), 이번_주_월요일(), EmotionState.ANGRY, 2);
        눌린_것으로_둔다(그룹을_만든다("다모임"), 이번_주_월요일(), EmotionState.ANGRY, 1);

        // when
        List<GroupPressRankingItem> 순위표 = groupRankingService.findPressRanking(남의_기기(), 0).items();

        // then
        assertThat(순위표).extracting(GroupPressRankingItem::rank).containsExactly(1, 1, 3);
    }

    @Test
    void 프레스_랭킹의_중간_동점도_공동_순위를_받고_같은_점수는_이름_순으로_나온다() {
        // given
        눌린_것으로_둔다(그룹을_만든다("일등모임"), 이번_주_월요일(), EmotionState.ANGRY, 9);
        눌린_것으로_둔다(그룹을_만든다("나동점모임"), 이번_주_월요일(), EmotionState.ANGRY, 4);
        눌린_것으로_둔다(그룹을_만든다("가동점모임"), 이번_주_월요일(), EmotionState.ANGRY, 4);
        눌린_것으로_둔다(그룹을_만든다("꼴찌모임"), 이번_주_월요일(), EmotionState.ANGRY, 1);

        // when
        List<GroupPressRankingItem> 순위표 = groupRankingService.findPressRanking(남의_기기(), 0).items();

        // then
        assertThat(순위표).extracting(GroupPressRankingItem::name, GroupPressRankingItem::rank)
                .containsExactly(
                        tuple("일등모임", 1),
                        tuple("가동점모임", 2),
                        tuple("나동점모임", 2),
                        tuple("꼴찌모임", 4)
                );
    }

    @Test
    void 이전_주에_그룹_프레스가_있으면_뒤로_더_갈_수_있다고_알린다() {
        // given
        GroupResult 그룹 = 그룹을_만든다("한숨모임");
        눌린_것으로_둔다(그룹, 이번_주_월요일().minusWeeks(2), EmotionState.ANGRY, 3);

        // when
        GroupPressRankingResult 이번주 = groupRankingService.findPressRanking(남의_기기(), 0);
        GroupPressRankingResult 이주_전 = groupRankingService.findPressRanking(남의_기기(), 2);

        // then
        assertThat(이번주.hasPrevious()).isTrue();
        assertThat(이번주.items()).isEmpty();
        assertThat(이주_전.hasPrevious()).isFalse();
        assertThat(이주_전.items()).hasSize(1);
    }

    @Test
    void 프레스_랭킹은_누르지_않은_그룹과_지난주_기록을_빼놓는다() {
        // given
        GroupResult 활동한_그룹 = 그룹을_만든다("활동모임");
        GroupResult 지난주만_그룹 = 그룹을_만든다("지난주모임");
        그룹을_만든다("조용한모임");
        눌린_것으로_둔다(활동한_그룹, 이번_주_월요일(), EmotionState.ANGRY, 1);
        눌린_것으로_둔다(지난주만_그룹, 이번_주_월요일().minusDays(1), EmotionState.ANGRY, 9);

        // when
        GroupPressRankingResult 결과 = groupRankingService.findPressRanking(남의_기기(), 0);

        // then
        assertThat(결과.items()).hasSize(1);
        assertThat(결과.items().getFirst().name()).isEqualTo("활동모임");
    }

    @Test
    void 감정별_프레스_랭킹은_그_감정만_센다() {
        // given
        GroupResult 그룹 = 그룹을_만든다("한모임");
        눌린_것으로_둔다(그룹, 이번_주_월요일(), EmotionState.ANGRY, 4);
        눌린_것으로_둔다(그룹, 이번_주_월요일(), EmotionState.EXHAUSTED, 9);

        // when
        GroupStatePressRankingResult 결과 =
                groupRankingService.findPressRankingByState(남의_기기(), EmotionState.ANGRY, 0);

        // then
        assertThat(결과.state()).isEqualTo(EmotionState.ANGRY);
        assertThat(결과.items()).extracting(GroupPressRankingItem::name, GroupPressRankingItem::score)
                .containsExactly(tuple("한모임", 4L));
    }

    @Test
    void 아무도_누르지_않은_감정은_빈_순위표를_돌려준다() {
        // given
        눌린_것으로_둔다(그룹을_만든다("한모임"), 이번_주_월요일(), EmotionState.ANGRY, 1);

        // when
        GroupStatePressRankingResult 결과 =
                groupRankingService.findPressRankingByState(남의_기기(), EmotionState.DISCOURAGED, 0);

        // then
        assertThat(결과.state()).isEqualTo(EmotionState.DISCOURAGED);
        assertThat(결과.items()).isEmpty();
    }

    @Test
    void 감정별_버튼_랭킹은_감정마다_순위를_따로_매긴다() {
        // given
        GroupResult 가모임 = 그룹을_만든다("가모임");
        GroupResult 나모임 = 그룹을_만든다("나모임");
        눌린_것으로_둔다(가모임, 이번_주_월요일(), EmotionState.ANGRY, 5);
        눌린_것으로_둔다(나모임, 이번_주_월요일(), EmotionState.ANGRY, 1);
        눌린_것으로_둔다(가모임, 이번_주_월요일(), EmotionState.EXHAUSTED, 1);
        눌린_것으로_둔다(나모임, 이번_주_월요일(), EmotionState.EXHAUSTED, 7);

        // when
        List<GroupPressRankingItem> 분노_순위표 =
                groupRankingService.findPressRankingByState(남의_기기(), EmotionState.ANGRY, 0).items();
        List<GroupPressRankingItem> 지침_순위표 =
                groupRankingService.findPressRankingByState(남의_기기(), EmotionState.EXHAUSTED, 0).items();

        // then
        assertThat(분노_순위표).extracting(GroupPressRankingItem::name, GroupPressRankingItem::score)
                .containsExactly(tuple("가모임", 5L), tuple("나모임", 1L));
        assertThat(지침_순위표).extracting(GroupPressRankingItem::name, GroupPressRankingItem::score)
                .containsExactly(tuple("나모임", 7L), tuple("가모임", 1L));
    }

    @Test
    void 스탬프_랭킹과_프레스_랭킹은_서로_섞이지_않는다() {
        // given
        GroupResult 지도만_그룹 = 그룹을_만든다("지도모임");
        GroupResult 버튼만_그룹 = 그룹을_만든다("버튼모임");
        이번주_감정을_남긴다(지도만_그룹, 4);
        눌린_것으로_둔다(버튼만_그룹, 이번_주_월요일(), EmotionState.ANGRY, 6);

        // when
        GroupRankingResult 스탬프_랭킹 = groupRankingService.findGroupRanking(0);
        GroupPressRankingResult 프레스_랭킹 = groupRankingService.findPressRanking(남의_기기(), 0);

        // then
        assertThat(스탬프_랭킹.items()).extracting(GroupRankingItem::name, GroupRankingItem::score)
                .containsExactly(tuple("지도모임", 4L));
        assertThat(프레스_랭킹.items()).extracting(GroupPressRankingItem::name, GroupPressRankingItem::score)
                .containsExactly(tuple("버튼모임", 6L));
    }

    @Test
    void 프레스_랭킹은_내가_속한_그룹에_표시를_남긴다() {
        // given
        UUID 내_기기 = 기기_식별자를_만든다();
        GroupResult 내_그룹 = 그룹을_만든다("내모임", 내_기기);
        GroupResult 남의_그룹 = 그룹을_만든다("남의모임");
        눌린_것으로_둔다(내_그룹, 이번_주_월요일(), EmotionState.ANGRY, 1);
        눌린_것으로_둔다(남의_그룹, 이번_주_월요일(), EmotionState.ANGRY, 9);

        // when
        GroupPressRankingResult 결과 = groupRankingService.findPressRanking(내_기기, 0);

        // then
        assertThat(결과.items()).extracting(GroupPressRankingItem::name, GroupPressRankingItem::mine)
                .containsExactly(tuple("남의모임", false), tuple("내모임", true));
    }

    @Test
    void 감정별_프레스_랭킹도_내가_속한_그룹에_표시를_남긴다() {
        // given
        UUID 내_기기 = 기기_식별자를_만든다();
        눌린_것으로_둔다(그룹을_만든다("내모임", 내_기기), 이번_주_월요일(), EmotionState.ANGRY, 1);
        눌린_것으로_둔다(그룹을_만든다("남의모임"), 이번_주_월요일(), EmotionState.ANGRY, 9);

        // when
        GroupStatePressRankingResult 결과 =
                groupRankingService.findPressRankingByState(내_기기, EmotionState.ANGRY, 0);

        // then
        assertThat(결과.items()).extracting(GroupPressRankingItem::name, GroupPressRankingItem::mine)
                .containsExactly(tuple("남의모임", false), tuple("내모임", true));
    }

    @Test
    void 떠난_그룹은_내_그룹으로_표시하지_않는다() {
        // given
        GroupResult 그룹 = 그룹을_만든다("한모임");
        UUID 떠날_기기 = 기기_식별자를_만든다();
        groupService.join(떠날_기기, 그룹.inviteCode());
        groupService.leave(그룹.publicId(), 떠날_기기);
        눌린_것으로_둔다(그룹, 이번_주_월요일(), EmotionState.ANGRY, 1);

        // when
        GroupPressRankingResult 결과 = groupRankingService.findPressRanking(떠날_기기, 0);

        // then
        assertThat(결과.items()).extracting(GroupPressRankingItem::mine).containsExactly(false);
    }

    @Test
    void 멤버_합산_프레스_랭킹은_개인_프레스를_합쳐_순위를_매긴다() {
        // given
        GroupResult 많은_그룹 = 그룹을_만든다("많은모임");
        GroupResult 적은_그룹 = 그룹을_만든다("적은모임");
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(많은_그룹), 이번_주_월요일(), EmotionState.ANGRY, 5);
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(많은_그룹), 이번_주_월요일(), EmotionState.EXHAUSTED, 2);
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(적은_그룹), 이번_주_월요일(), EmotionState.ANGRY, 1);

        // when
        GroupPressRankingResult 결과 = groupRankingService.findEmotionPressRanking(남의_기기(), 0);

        // then
        assertThat(결과.items())
                .extracting(GroupPressRankingItem::rank, GroupPressRankingItem::name, GroupPressRankingItem::score)
                .containsExactly(tuple(1, "많은모임", 7L), tuple(2, "적은모임", 1L));
    }

    @Test
    void 멤버_합산_프레스_랭킹도_동점이면_공동_순위를_준다() {
        // given
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(그룹을_만든다("가모임")), 이번_주_월요일(), EmotionState.ANGRY, 2);
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(그룹을_만든다("나모임")), 이번_주_월요일(), EmotionState.ANGRY, 2);
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(그룹을_만든다("다모임")), 이번_주_월요일(), EmotionState.ANGRY, 1);

        // when
        List<GroupPressRankingItem> 순위표 = groupRankingService.findEmotionPressRanking(남의_기기(), 0).items();

        // then
        assertThat(순위표).extracting(GroupPressRankingItem::rank).containsExactly(1, 1, 3);
    }

    @Test
    void 멤버_합산_프레스_랭킹은_누르지_않은_그룹을_목록에서_빼놓는다() {
        // given
        GroupResult 활동한_그룹 = 그룹을_만든다("활동모임");
        그룹을_만든다("조용한모임");
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(활동한_그룹), 이번_주_월요일(), EmotionState.ANGRY, 1);

        // when
        GroupPressRankingResult 결과 = groupRankingService.findEmotionPressRanking(남의_기기(), 0);

        // then
        assertThat(결과.items()).extracting(GroupPressRankingItem::name).containsExactly("활동모임");
    }

    @Test
    void 멤버_합산_프레스_랭킹은_지난주를_따로_조회한다() {
        // given
        GroupResult 그룹 = 그룹을_만든다("한숨모임");
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(그룹), 이번_주_월요일().minusDays(3), EmotionState.ANGRY, 9);
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(그룹), 이번_주_월요일(), EmotionState.ANGRY, 2);

        // when
        GroupPressRankingResult 이번주 = groupRankingService.findEmotionPressRanking(남의_기기(), 0);
        GroupPressRankingResult 지난주 = groupRankingService.findEmotionPressRanking(남의_기기(), 1);

        // then
        assertThat(이번주.items()).extracting(GroupPressRankingItem::score).containsExactly(2L);
        assertThat(지난주.items()).extracting(GroupPressRankingItem::score).containsExactly(9L);
    }

    @Test
    void 멤버_합산_프레스_랭킹의_이전_주_기록_유무를_알려준다() {
        // given
        GroupResult 그룹 = 그룹을_만든다("한숨모임");
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(그룹), 이번_주_월요일(), EmotionState.ANGRY, 1);

        // when
        GroupPressRankingResult 이전_기록_없음 = groupRankingService.findEmotionPressRanking(남의_기기(), 0);
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(그룹), 이번_주_월요일().minusDays(1), EmotionState.ANGRY, 1);
        GroupPressRankingResult 이전_기록_있음 = groupRankingService.findEmotionPressRanking(남의_기기(), 0);

        // then
        assertThat(이전_기록_없음.hasPrevious()).isFalse();
        assertThat(이전_기록_있음.hasPrevious()).isTrue();
    }

    @Test
    void 멤버_합산_프레스_랭킹은_내가_속한_그룹에_표시를_남긴다() {
        // given
        UUID 내_기기 = 기기_식별자를_만든다();
        GroupResult 내_그룹 = 그룹을_만든다("내모임", 내_기기);
        GroupResult 남의_그룹 = 그룹을_만든다("남의모임");
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(내_그룹), 이번_주_월요일(), EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(남의_그룹), 이번_주_월요일(), EmotionState.ANGRY, 9);

        // when
        GroupPressRankingResult 결과 = groupRankingService.findEmotionPressRanking(내_기기, 0);

        // then
        assertThat(결과.items())
                .extracting(GroupPressRankingItem::name, GroupPressRankingItem::mine)
                .containsExactly(tuple("남의모임", false), tuple("내모임", true));
    }

    @Test
    void 멤버_합산_프레스_랭킹과_구_프레스_랭킹은_서로_섞이지_않는다() {
        // given
        GroupResult 개인_프레스_그룹 = 그룹을_만든다("개인모임");
        GroupResult 구_프레스_그룹 = 그룹을_만든다("구모임");
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(개인_프레스_그룹), 이번_주_월요일(), EmotionState.ANGRY, 4);
        눌린_것으로_둔다(구_프레스_그룹, 이번_주_월요일(), EmotionState.ANGRY, 6);

        // when
        GroupPressRankingResult 신_랭킹 = groupRankingService.findEmotionPressRanking(남의_기기(), 0);
        GroupPressRankingResult 구_랭킹 = groupRankingService.findPressRanking(남의_기기(), 0);

        // then
        assertThat(신_랭킹.items())
                .extracting(GroupPressRankingItem::name, GroupPressRankingItem::score)
                .containsExactly(tuple("개인모임", 4L));
        assertThat(구_랭킹.items())
                .extracting(GroupPressRankingItem::name, GroupPressRankingItem::score)
                .containsExactly(tuple("구모임", 6L));
    }

    @Test
    void 멤버_합산_감정별_랭킹은_그_감정만_합친다() {
        // given
        GroupResult 그룹 = 그룹을_만든다("한숨모임");
        Long 기기 = 그룹장_기기_식별자(그룹);
        개인_프레스를_저장한다(jdbcClient, 기기, 이번_주_월요일(), EmotionState.ANGRY, 4);
        개인_프레스를_저장한다(jdbcClient, 기기, 이번_주_월요일(), EmotionState.EXHAUSTED, 9);

        // when
        GroupStatePressRankingResult 결과 =
                groupRankingService.findEmotionPressRankingByState(남의_기기(), EmotionState.ANGRY, 0);

        // then
        assertThat(결과.state()).isEqualTo(EmotionState.ANGRY);
        assertThat(결과.items())
                .extracting(GroupPressRankingItem::name, GroupPressRankingItem::score)
                .containsExactly(tuple("한숨모임", 4L));
    }

    @Test
    void 멤버_합산_감정별_랭킹은_감정마다_순위를_따로_매긴다() {
        // given
        GroupResult 가모임 = 그룹을_만든다("가모임");
        GroupResult 나모임 = 그룹을_만든다("나모임");
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(가모임), 이번_주_월요일(), EmotionState.ANGRY, 5);
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(나모임), 이번_주_월요일(), EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(가모임), 이번_주_월요일(), EmotionState.EXHAUSTED, 1);
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(나모임), 이번_주_월요일(), EmotionState.EXHAUSTED, 7);

        // when
        List<GroupPressRankingItem> 분노 =
                groupRankingService.findEmotionPressRankingByState(남의_기기(), EmotionState.ANGRY, 0).items();
        List<GroupPressRankingItem> 지침 =
                groupRankingService.findEmotionPressRankingByState(남의_기기(), EmotionState.EXHAUSTED, 0).items();

        // then
        assertThat(분노).extracting(GroupPressRankingItem::name, GroupPressRankingItem::score)
                .containsExactly(tuple("가모임", 5L), tuple("나모임", 1L));
        assertThat(지침).extracting(GroupPressRankingItem::name, GroupPressRankingItem::score)
                .containsExactly(tuple("나모임", 7L), tuple("가모임", 1L));
    }

    @Test
    void 멤버_합산_감정별_랭킹은_아무도_누르지_않은_감정에_빈_순위표를_돌려준다() {
        // given
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(그룹을_만든다("한숨모임")),
                이번_주_월요일(), EmotionState.ANGRY, 1);

        // when
        GroupStatePressRankingResult 결과 =
                groupRankingService.findEmotionPressRankingByState(남의_기기(), EmotionState.DISCOURAGED, 0);

        // then
        assertThat(결과.state()).isEqualTo(EmotionState.DISCOURAGED);
        assertThat(결과.items()).isEmpty();
    }

    @Test
    void 멤버_합산_감정별_랭킹과_구_감정별_랭킹은_서로_섞이지_않는다() {
        // given
        GroupResult 개인_프레스_그룹 = 그룹을_만든다("개인모임");
        GroupResult 구_프레스_그룹 = 그룹을_만든다("구모임");
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(개인_프레스_그룹), 이번_주_월요일(), EmotionState.ANGRY, 4);
        눌린_것으로_둔다(구_프레스_그룹, 이번_주_월요일(), EmotionState.ANGRY, 6);

        // when
        GroupStatePressRankingResult 신_랭킹 =
                groupRankingService.findEmotionPressRankingByState(남의_기기(), EmotionState.ANGRY, 0);
        GroupStatePressRankingResult 구_랭킹 =
                groupRankingService.findPressRankingByState(남의_기기(), EmotionState.ANGRY, 0);

        // then
        assertThat(신_랭킹.items()).extracting(GroupPressRankingItem::name, GroupPressRankingItem::score)
                .containsExactly(tuple("개인모임", 4L));
        assertThat(구_랭킹.items()).extracting(GroupPressRankingItem::name, GroupPressRankingItem::score)
                .containsExactly(tuple("구모임", 6L));
    }

    @Test
    void 멤버_합산_감정별_랭킹은_탈퇴한_멤버의_기록을_빼놓는다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        Device 떠날_기기 = 기기를_저장한다();
        GroupResult 그룹 = 그룹을_만든다("한숨모임", 그룹장.getPublicId());
        groupService.join(떠날_기기.getPublicId(), 그룹.inviteCode());
        개인_프레스를_저장한다(jdbcClient, 그룹장.getId(), 이번_주_월요일(), EmotionState.ANGRY, 2);
        개인_프레스를_저장한다(jdbcClient, 떠날_기기.getId(), 이번_주_월요일(), EmotionState.ANGRY, 4);

        // when
        long 탈퇴_전 = 감정별_점수(EmotionState.ANGRY);
        groupService.leave(그룹.publicId(), 떠날_기기.getPublicId());
        long 탈퇴_후 = 감정별_점수(EmotionState.ANGRY);

        // then
        assertThat(탈퇴_전).isEqualTo(6);
        assertThat(탈퇴_후).isEqualTo(2);
    }

    @Test
    void 멤버_합산_감정별_랭킹은_삭제된_그룹을_빼놓는다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 지울_그룹 = 그룹을_만든다("지울모임", 그룹장.getPublicId());
        개인_프레스를_저장한다(jdbcClient, 그룹장.getId(), 이번_주_월요일(), EmotionState.ANGRY, 3);

        // when
        List<GroupPressRankingItem> 삭제_전 =
                groupRankingService.findEmotionPressRankingByState(남의_기기(), EmotionState.ANGRY, 0).items();
        groupService.delete(지울_그룹.publicId(), 그룹장.getPublicId());
        List<GroupPressRankingItem> 삭제_후 =
                groupRankingService.findEmotionPressRankingByState(남의_기기(), EmotionState.ANGRY, 0).items();

        // then
        assertThat(삭제_전).extracting(GroupPressRankingItem::name).containsExactly("지울모임");
        assertThat(삭제_후).isEmpty();
    }

    @Test
    void 멤버_합산_감정별_랭킹의_이전_주_기록_유무를_알려준다() {
        // given
        GroupResult 그룹 = 그룹을_만든다("한숨모임");
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(그룹), 이번_주_월요일(), EmotionState.ANGRY, 1);

        // when
        boolean 이전_기록_없음 = groupRankingService
                .findEmotionPressRankingByState(남의_기기(), EmotionState.ANGRY, 0).hasPrevious();
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(그룹),
                이번_주_월요일().minusDays(1), EmotionState.EXHAUSTED, 1);
        boolean 이전_기록_있음 = groupRankingService
                .findEmotionPressRankingByState(남의_기기(), EmotionState.ANGRY, 0).hasPrevious();

        // then
        assertThat(이전_기록_없음).isFalse();
        assertThat(이전_기록_있음).isTrue();
    }

    @Test
    void 멤버_합산_감정별_랭킹은_내가_속한_그룹에_표시를_남긴다() {
        // given
        UUID 내_기기 = 기기_식별자를_만든다();
        GroupResult 내_그룹 = 그룹을_만든다("내모임", 내_기기);
        GroupResult 남의_그룹 = 그룹을_만든다("남의모임");
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(내_그룹), 이번_주_월요일(), EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(남의_그룹), 이번_주_월요일(), EmotionState.ANGRY, 9);

        // when
        GroupStatePressRankingResult 결과 =
                groupRankingService.findEmotionPressRankingByState(내_기기, EmotionState.ANGRY, 0);

        // then
        assertThat(결과.items())
                .extracting(GroupPressRankingItem::name, GroupPressRankingItem::mine)
                .containsExactly(tuple("남의모임", false), tuple("내모임", true));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 20})
    void 그룹_수와_관계없이_세_번의_SQL로_멤버_합산_랭킹을_조회한다(int 그룹_수) {
        // given
        for (int index = 0; index < 그룹_수; index++) {
            GroupResult 그룹 = 그룹을_만든다("모임" + index);
            개인_프레스를_저장한다(jdbcClient, 그룹장_기기_식별자(그룹), 이번_주_월요일(), EmotionState.ANGRY, index + 1L);
        }
        Statistics 통계 = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean 기존_통계_설정 = 통계.isStatisticsEnabled();
        통계.setStatisticsEnabled(true);

        try {
            entityManager.clear();
            통계.clear();

            // when
            GroupPressRankingResult 합계 = groupRankingService.findEmotionPressRanking(남의_기기(), 0);
            long 합계_SQL_횟수 = 통계.getPrepareStatementCount();
            entityManager.clear();
            통계.clear();
            GroupStatePressRankingResult 감정별 =
                    groupRankingService.findEmotionPressRankingByState(남의_기기(), EmotionState.ANGRY, 0);
            long 감정별_SQL_횟수 = 통계.getPrepareStatementCount();

            // then
            assertThat(합계.items()).hasSize(그룹_수);
            assertThat(감정별.items()).hasSize(그룹_수);
            assertThat(합계_SQL_횟수).isEqualTo(3);
            assertThat(감정별_SQL_횟수).isEqualTo(3);
        } finally {
            통계.clear();
            통계.setStatisticsEnabled(기존_통계_설정);
        }
    }

    private long 감정별_점수(EmotionState 감정) {
        return groupRankingService.findEmotionPressRankingByState(남의_기기(), 감정, 0).items().stream()
                .mapToLong(GroupPressRankingItem::score)
                .sum();
    }

    private LocalDate 이번_주_월요일() {
        return RankingWeek.of(Instant.now(), 0).startDate();
    }

    private void 눌린_것으로_둔다(GroupResult 그룹, LocalDate 날짜, EmotionState 감정, int 횟수) {
        Long groupId = groupRepository.findByPublicIdAndDeletedAtIsNull(그룹.publicId()).orElseThrow().getId();
        jdbcClient.sql("""
                        INSERT INTO group_daily_presses
                            (group_id, press_date, state, press_count, created_at, updated_at)
                        VALUES (:groupId, :pressDate, :state, :pressCount, NOW(), NOW())
                        ON CONFLICT (group_id, press_date, state) DO UPDATE
                           SET press_count = group_daily_presses.press_count + :pressCount
                        """)
                .param("groupId", groupId)
                .param("pressDate", 날짜)
                .param("state", 감정.name())
                .param("pressCount", (long) 횟수)
                .update();
    }

    private Long 그룹장_기기_식별자(GroupResult 그룹) {
        return jdbcClient.sql("""
                        SELECT m.device_id FROM group_members m
                          JOIN groups g ON g.id = m.group_id
                         WHERE g.public_id = ? AND m.role = 'OWNER' AND m.left_at IS NULL
                        """)
                .param(그룹.publicId())
                .query(Long.class)
                .single();
    }

    private UUID 남의_기기() {
        return UUID.randomUUID();
    }

    private UUID 기기_식별자를_만든다() {
        return 기기를_저장한다().getPublicId();
    }

    private GroupResult 그룹을_만든다(String name, UUID devicePublicId) {
        return groupService.save(
                devicePublicId,
                name,
                null,
                GroupStampCommand.of("기본", "#FFFFFF", "#4A90D9", StampFrame.CIRCLE)
        );
    }

    private GroupResult 그룹을_만든다(String name) {
        return groupService.save(
                기기를_저장한다().getPublicId(),
                name,
                null,
                GroupStampCommand.of("기본", "#FFFFFF", "#4A90D9", StampFrame.CIRCLE)
        );
    }

    private Device 기기를_저장한다() {
        return deviceRepository.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
    }

    private List<Emotion> 이번주_감정을_남긴다(GroupResult 그룹, int 개수) {
        return 감정을_남긴다(그룹, 개수, 0);
    }

    private List<Emotion> 지난주_감정을_남긴다(GroupResult 그룹, int 개수) {
        return 감정을_남긴다(그룹, 개수, 1);
    }

    private List<Emotion> 감정을_남긴다(GroupResult 그룹, int 개수, int 몇_주_전) {
        Long groupId = groupRepository.findByPublicIdAndDeletedAtIsNull(그룹.publicId()).orElseThrow().getId();
        GroupStamp stamp = groupStampRepository.findByGroupId(groupId).orElseThrow();
        Instant 그_주_시작 = RankingWeek.of(Instant.now(), 몇_주_전).startAt();

        List<Emotion> 감정들 = new java.util.ArrayList<>();
        for (int index = 0; index < 개수; index++) {
            Emotion emotion = emotionRepository.saveAndFlush(기본_한숨_빌더()
                    .requestId(UUID.randomUUID())
                    .state(EmotionState.ANGRY)
                    .groupStamp(stamp)
                    .build());
            생성_시각을_바꾼다(emotion.getId(), 그_주_시작.plusSeconds(3600L * (index + 1)));
            감정들.add(emotion);
        }

        return 감정들;
    }

    private void 개인_감정을_남긴다() {
        Emotion emotion = emotionRepository.saveAndFlush(기본_한숨_빌더()
                .requestId(UUID.randomUUID())
                .state(EmotionState.ANGRY)
                .build());
        생성_시각을_바꾼다(emotion.getId(), RankingWeek.of(Instant.now(), 0).startAt().plusSeconds(3600));
    }

    private void 생성_시각을_바꾼다(Long emotionId, Instant createdAt) {
        jdbcClient.sql("UPDATE emotions SET created_at = ? WHERE id = ?")
                .param(java.sql.Timestamp.from(createdAt))
                .param(emotionId)
                .update();
    }

    private void 삭제한다(Long emotionId) {
        jdbcClient.sql("UPDATE emotions SET deleted_at = NOW() WHERE id = ?")
                .param(emotionId)
                .update();
    }
}
