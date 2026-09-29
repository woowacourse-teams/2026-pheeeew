package com.pheeeew.groups.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionEmojiRepository;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.groups.application.dto.GroupRankingItem;
import com.pheeeew.groups.application.dto.GroupRankingResult;
import com.pheeeew.groups.application.dto.GroupStampCommand;
import com.pheeeew.groups.application.dto.GroupResult;
import com.pheeeew.groups.domain.GroupStamp;
import com.pheeeew.groups.domain.StampFrame;
import com.pheeeew.groups.domain.repository.GroupMemberRepository;
import com.pheeeew.groups.domain.repository.GroupRepository;
import com.pheeeew.groups.domain.repository.GroupStampRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
    private JdbcClient jdbcClient;

    @AfterEach
    void tearDown() {
        emotionEmojiRepository.deleteAllInBatch();
        emotionRepository.deleteAllInBatch();
        groupMemberRepository.deleteAllInBatch();
        groupStampRepository.deleteAllInBatch();
        groupRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
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
