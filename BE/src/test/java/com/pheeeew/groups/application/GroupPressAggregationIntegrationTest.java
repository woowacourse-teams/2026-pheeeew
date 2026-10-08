package com.pheeeew.groups.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.DeviceDailyPressFixture.개인_프레스를_저장한다;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.groups.application.dto.GroupDetailV3Result;
import com.pheeeew.groups.application.dto.GroupPressRankingItem;
import com.pheeeew.groups.application.dto.GroupResult;
import com.pheeeew.groups.application.dto.GroupStampCommand;
import com.pheeeew.groups.domain.StampFrame;
import com.pheeeew.groups.domain.repository.GroupMemberRepository;
import com.pheeeew.groups.domain.repository.GroupRepository;
import com.pheeeew.groups.domain.repository.GroupStampRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GroupPressAggregationIntegrationTest {

    private static final Instant 수요일_정오 = Instant.parse("2026-10-07T03:00:00Z");

    @MockitoBean
    private Clock clock;

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupRankingService groupRankingService;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupStampRepository groupStampRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(수요일_정오);
        검증용_지역_계층을_저장한다(jdbcClient);
    }

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM device_daily_presses").update();
        jdbcClient.sql("DELETE FROM group_daily_presses").update();
        jdbcClient.sql("DELETE FROM regions").update();
        groupMemberRepository.deleteAllInBatch();
        groupStampRepository.deleteAllInBatch();
        groupRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
    }

    @Test
    void 두_그룹에_속한_기기가_한_번_누르면_두_그룹_모두_오른다() {
        // given
        Device 기기 = 기기를_저장한다();
        GroupResult 내_그룹 = 그룹을_만든다("내모임", 기기);
        GroupResult 옆_그룹 = 그룹을_만든다("옆모임", 기기를_저장한다());
        groupService.join(기기.getPublicId(), 옆_그룹.inviteCode());
        눌린_것으로_둔다(기기, 이번_주_월요일(), EmotionState.ANGRY, 5);

        // when
        GroupDetailV3Result 내_상세 = 상세(내_그룹, 기기);
        GroupDetailV3Result 옆_상세 = 상세(옆_그룹, 기기);

        // then
        assertThat(내_상세.weeklyEmotionPressCount()).isEqualTo(5);
        assertThat(옆_상세.weeklyEmotionPressCount()).isEqualTo(5);
        assertThat(프레스_랭킹(기기)).isEmpty();
    }

    @Test
    void 같은_횟수를_누른_멤버가_여럿이면_각자의_횟수가_모두_더해진다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        Device 멤버 = 기기를_저장한다();
        Device 다른_멤버 = 기기를_저장한다();
        GroupResult 그룹 = 그룹을_만든다("한숨모임", 그룹장);
        groupService.join(멤버.getPublicId(), 그룹.inviteCode());
        groupService.join(다른_멤버.getPublicId(), 그룹.inviteCode());
        눌린_것으로_둔다(그룹장, 이번_주_월요일(), EmotionState.ANGRY, 3);
        눌린_것으로_둔다(멤버, 이번_주_월요일(), EmotionState.ANGRY, 3);
        눌린_것으로_둔다(다른_멤버, 이번_주_월요일().plusDays(2), EmotionState.EXHAUSTED, 4);

        // when
        GroupDetailV3Result 상세 = 상세(그룹, 그룹장);

        // then
        assertThat(상세.weeklyEmotionPressCount()).isEqualTo(10);
        assertThat(상세.weeklyEmotionPressRank()).isOne();
    }

    @Test
    void 탈퇴하면_그_주에_누른_것까지_그룹_합계에서_빠진다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        Device 떠날_기기 = 기기를_저장한다();
        GroupResult 그룹 = 그룹을_만든다("한숨모임", 그룹장);
        groupService.join(떠날_기기.getPublicId(), 그룹.inviteCode());
        눌린_것으로_둔다(그룹장, 이번_주_월요일(), EmotionState.ANGRY, 2);
        눌린_것으로_둔다(떠날_기기, 이번_주_월요일(), EmotionState.ANGRY, 4);
        long 탈퇴_전 = 상세(그룹, 그룹장).weeklyEmotionPressCount();

        // when
        groupService.leave(그룹.publicId(), 떠날_기기.getPublicId());

        // then
        assertThat(탈퇴_전).isEqualTo(6);
        assertThat(상세(그룹, 그룹장).weeklyEmotionPressCount()).isEqualTo(2);
    }

    @Test
    void 나갔다_다시_들어온_멤버의_프레스도_한_번만_세어진다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        Device 재가입_기기 = 기기를_저장한다();
        GroupResult 그룹 = 그룹을_만든다("한숨모임", 그룹장);
        groupService.join(재가입_기기.getPublicId(), 그룹.inviteCode());
        groupService.leave(그룹.publicId(), 재가입_기기.getPublicId());
        groupService.join(재가입_기기.getPublicId(), 그룹.inviteCode());
        눌린_것으로_둔다(재가입_기기, 이번_주_월요일(), EmotionState.ANGRY, 5);

        // when
        GroupDetailV3Result 상세 = 상세(그룹, 그룹장);

        // then
        assertThat(멤버십_행_수(그룹, 재가입_기기)).isEqualTo(2);
        assertThat(상세.weeklyEmotionPressCount()).isEqualTo(5);
    }

    @Test
    void 주_중간에_가입해도_그_주_처음부터의_기록이_들어온다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        Device 늦게_온_기기 = 기기를_저장한다();
        GroupResult 그룹 = 그룹을_만든다("한숨모임", 그룹장);
        눌린_것으로_둔다(늦게_온_기기, 이번_주_월요일(), EmotionState.ANGRY, 8);
        long 가입_전 = 상세(그룹, 그룹장).weeklyEmotionPressCount();

        // when
        groupService.join(늦게_온_기기.getPublicId(), 그룹.inviteCode());

        // then
        assertThat(가입_전).isZero();
        assertThat(상세(그룹, 그룹장).weeklyEmotionPressCount()).isEqualTo(8);
    }

    @Test
    void 어느_그룹에도_속하지_않은_기기의_프레스는_어느_그룹에도_들어가지_않는다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = 그룹을_만든다("한숨모임", 그룹장);
        눌린_것으로_둔다(그룹장, 이번_주_월요일(), EmotionState.ANGRY, 1);
        눌린_것으로_둔다(기기를_저장한다(), 이번_주_월요일(), EmotionState.ANGRY, 9);

        // when
        GroupDetailV3Result 상세 = 상세(그룹, 그룹장);

        // then
        assertThat(상세.weeklyEmotionPressCount()).isOne();
        assertThat(프레스_랭킹(그룹장)).isEmpty();
    }

    @Test
    void 그룹_프레스_개수는_주_경계_밖의_기록을_세지_않는다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = 그룹을_만든다("한숨모임", 그룹장);
        눌린_것으로_둔다(그룹장, 이번_주_월요일().minusDays(1), EmotionState.ANGRY, 7);
        눌린_것으로_둔다(그룹장, 이번_주_월요일(), EmotionState.ANGRY, 1);
        눌린_것으로_둔다(그룹장, 이번_주_월요일().plusDays(6), EmotionState.ANGRY, 2);
        눌린_것으로_둔다(그룹장, 이번_주_월요일().plusDays(7), EmotionState.ANGRY, 9);

        // when
        GroupDetailV3Result 상세 = 상세(그룹, 그룹장);

        // then
        assertThat(상세.weeklyEmotionPressCount()).isEqualTo(3);
    }

    @Test
    void 개인_프레스는_스탬프_개수와_순위에는_들어가지_않는다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = 그룹을_만든다("한숨모임", 그룹장);
        눌린_것으로_둔다(그룹장, 이번_주_월요일(), EmotionState.ANGRY, 4);

        // when
        GroupDetailV3Result 상세 = 상세(그룹, 그룹장);

        // then
        assertThat(상세.weeklyStampCount()).isZero();
        assertThat(상세.weeklyStampRank()).isNull();
        assertThat(상세.weeklyEmotionPressCount()).isEqualTo(4);
        assertThat(상세.weeklyEmotionPressRank()).isOne();
    }

    @Test
    void 프레스_기록이_없는_그룹은_개수가_영이고_순위가_null_이다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 조용한_그룹 = 그룹을_만든다("조용한모임", 그룹장);
        눌린_것으로_둔다(기기를_저장한다(), 이번_주_월요일(), EmotionState.ANGRY, 3);

        // when
        GroupDetailV3Result 상세 = 상세(조용한_그룹, 그룹장);

        // then
        assertThat(상세.weeklyEmotionPressCount()).isZero();
        assertThat(상세.weeklyEmotionPressRank()).isNull();
    }

    @Test
    void 비가입자도_상세에서_프레스_개수와_순위를_받는다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        Device 남 = 기기를_저장한다();
        GroupResult 그룹 = 그룹을_만든다("남의모임", 그룹장);
        눌린_것으로_둔다(그룹장, 이번_주_월요일(), EmotionState.ANGRY, 6);

        // when
        GroupDetailV3Result 상세 = 상세(그룹, 남);

        // then
        assertThat(상세.weeklyEmotionPressCount()).isEqualTo(6);
        assertThat(상세.weeklyEmotionPressRank()).isOne();
    }

    @Test
    void v3_감정_프레스_순위는_공동_순위_규칙을_따르고_그룹_프레스_랭킹과_원천이_다르다() {
        // given
        Device 일등_기기 = 기기를_저장한다();
        Device 동점_기기 = 기기를_저장한다();
        Device 꼴찌_기기 = 기기를_저장한다();
        GroupResult 일등 = 그룹을_만든다("일등모임", 일등_기기);
        GroupResult 동점 = 그룹을_만든다("동점모임", 동점_기기);
        GroupResult 꼴찌 = 그룹을_만든다("꼴찌모임", 꼴찌_기기);
        눌린_것으로_둔다(일등_기기, 이번_주_월요일(), EmotionState.ANGRY, 9);
        눌린_것으로_둔다(동점_기기, 이번_주_월요일(), EmotionState.ANGRY, 9);
        눌린_것으로_둔다(꼴찌_기기, 이번_주_월요일(), EmotionState.ANGRY, 1);

        // when
        List<GroupPressRankingItem> 그룹_프레스_순위표 = 프레스_랭킹(일등_기기);

        // then
        assertThat(상세(일등, 일등_기기).weeklyEmotionPressRank()).isOne();
        assertThat(상세(동점, 일등_기기).weeklyEmotionPressRank()).isOne();
        assertThat(상세(꼴찌, 일등_기기).weeklyEmotionPressRank()).isEqualTo(3);
        assertThat(그룹_프레스_순위표).isEmpty();
    }

    @Test
    void group_daily_presses_에_남은_값은_프레스_개수와_순위에_들어가지_않는다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = 그룹을_만든다("한숨모임", 그룹장);
        옛_그룹_프레스를_남긴다(그룹, 이번_주_월요일(), EmotionState.ANGRY, 77);

        // when
        GroupDetailV3Result 상세 = 상세(그룹, 그룹장);

        // then
        assertThat(상세.weeklyEmotionPressCount()).isZero();
        assertThat(상세.weeklyEmotionPressRank()).isNull();
        assertThat(프레스_랭킹(그룹장)).extracting(GroupPressRankingItem::name, GroupPressRankingItem::score)
                .containsExactly(tuple("한숨모임", 77L));
    }

    private Device 기기를_저장한다() {
        return deviceRepository.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
    }

    private GroupResult 그룹을_만든다(String name, Device 그룹장) {
        return groupService.save(
                그룹장.getPublicId(),
                name,
                null,
                GroupStampCommand.of("기본", "#FFFFFF", "#4A90D9", StampFrame.CIRCLE)
        );
    }

    private LocalDate 이번_주_월요일() {
        return RankingWeek.of(수요일_정오, 0).startDate();
    }

    private void 눌린_것으로_둔다(Device 기기, LocalDate 날짜, EmotionState 감정, long 횟수) {
        개인_프레스를_저장한다(jdbcClient, 기기.getId(), 날짜, 감정, 횟수);
    }

    private long 멤버십_행_수(GroupResult 그룹, Device 기기) {
        return jdbcClient.sql("""
                        SELECT COUNT(*) FROM group_members m
                          JOIN groups g ON g.id = m.group_id
                         WHERE g.public_id = :groupPublicId AND m.device_id = :deviceId
                        """)
                .param("groupPublicId", 그룹.publicId())
                .param("deviceId", 기기.getId())
                .query(Long.class)
                .single();
    }

    private GroupDetailV3Result 상세(GroupResult 그룹, Device 보는_기기) {
        return groupService.findDetailV3(그룹.publicId(), 보는_기기.getPublicId());
    }

    private List<GroupPressRankingItem> 프레스_랭킹(Device 기기) {
        return groupRankingService.findPressRanking(기기.getPublicId(), 0).items();
    }

    private Integer 순위(List<GroupPressRankingItem> 순위표, String 이름) {
        return 순위표.stream()
                .filter(항목 -> 항목.name().equals(이름))
                .map(GroupPressRankingItem::rank)
                .findFirst()
                .orElseThrow();
    }

    private void 옛_그룹_프레스를_남긴다(GroupResult 그룹, LocalDate 날짜, EmotionState 감정, long 횟수) {
        Long groupId = groupRepository.findByPublicIdAndDeletedAtIsNull(그룹.publicId()).orElseThrow().getId();
        jdbcClient.sql("""
                        INSERT INTO group_daily_presses
                            (group_id, press_date, state, press_count, created_at, updated_at)
                        VALUES (:groupId, :pressDate, :state, :pressCount, NOW(), NOW())
                        """)
                .param("groupId", groupId)
                .param("pressDate", 날짜)
                .param("state", 감정.name())
                .param("pressCount", 횟수)
                .update();
    }
}
