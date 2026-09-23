package com.pheeeew.groups.domain;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.그룹장_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_그룹_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_스탬프_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.무작위_초대_코드;
import static com.pheeeew.groups.fixture.GroupFixture.일반_멤버_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.groups.domain.repository.GroupMemberRepository;
import com.pheeeew.groups.domain.repository.GroupRepository;
import com.pheeeew.groups.domain.repository.GroupStampRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GroupPersistenceIntegrationTest {

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

    @AfterEach
    void tearDown() {
        groupMemberRepository.deleteAllInBatch();
        groupStampRepository.deleteAllInBatch();
        groupRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
    }

    @Test
    void 그룹을_저장하고_모든_필드를_그대로_읽는다() {
        // given
        Group 저장한_그룹 = groupRepository.saveAndFlush(기본_그룹_빌더().build());

        // when
        Group 읽은_그룹 = groupRepository.findById(저장한_그룹.getId()).orElseThrow();

        // then
        assertThat(읽은_그룹.getPublicId()).isNotNull();
        assertThat(읽은_그룹.getName()).isEqualTo("한숨모임");
        assertThat(읽은_그룹.getDescription()).isEqualTo("테스트 그룹입니다.");
        assertThat(읽은_그룹.getInviteCode()).hasSize(6);
        assertThat(읽은_그룹.getDeletedAt()).isNull();
        assertThat(읽은_그룹.getCreatedAt()).isNotNull();
        assertThat(읽은_그룹.getUpdatedAt()).isNotNull();
    }

    @Test
    void 설명_없이도_그룹을_저장한다() {
        // given
        Group 설명이_없는_그룹 = Group.builder()
                .name("설명없음")
                .inviteCode(무작위_초대_코드())
                .build();

        // when
        Group 저장한_그룹 = groupRepository.saveAndFlush(설명이_없는_그룹);

        // then
        assertThat(저장한_그룹.getDescription()).isNull();
    }

    @Test
    void 같은_이름의_그룹은_두_번_만들_수_없다() {
        // given
        groupRepository.saveAndFlush(기본_그룹_빌더().build());

        // when
        Throwable throwable = catchThrowable(
                () -> groupRepository.saveAndFlush(기본_그룹_빌더().build())
        );

        // then
        assertThat(throwable).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_초대_코드를_가진_그룹은_두_번_만들_수_없다() {
        // given
        Group 먼저_만든_그룹 = groupRepository.saveAndFlush(기본_그룹_빌더().build());

        // when
        Throwable throwable = catchThrowable(() -> groupRepository.saveAndFlush(
                기본_그룹_빌더().name("다른이름").inviteCode(먼저_만든_그룹.getInviteCode()).build()
        ));

        // then
        assertThat(throwable).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 스탬프를_저장하고_모든_필드를_그대로_읽는다() {
        // given
        Group 그룹 = groupRepository.saveAndFlush(기본_그룹_빌더().build());
        GroupStamp 저장한_스탬프 = groupStampRepository.saveAndFlush(기본_스탬프_빌더(그룹).build());

        // when
        GroupStamp 읽은_스탬프 = groupStampRepository.findById(저장한_스탬프.getId()).orElseThrow();

        // then
        assertThat(읽은_스탬프.getGroup().getId()).isEqualTo(그룹.getId());
        assertThat(읽은_스탬프.getText()).isEqualTo("기본");
        assertThat(읽은_스탬프.getTextColor()).isEqualTo("#FFFFFF");
        assertThat(읽은_스탬프.getBackgroundColor()).isEqualTo("#4A90D9");
        assertThat(읽은_스탬프.getFrame()).isEqualTo(StampFrame.CIRCLE);
    }

    @Test
    void 스탬프_틀은_이름_그대로_저장된다() {
        // given
        Group 그룹 = groupRepository.saveAndFlush(기본_그룹_빌더().build());
        groupStampRepository.saveAndFlush(기본_스탬프_빌더(그룹).frame(StampFrame.RIBBON).build());

        // when
        String 저장된_틀 = jdbcClient.sql("SELECT frame FROM group_stamps WHERE group_id = ?")
                .param(그룹.getId())
                .query(String.class)
                .single();

        // then
        assertThat(저장된_틀).isEqualTo("RIBBON");
    }

    @Test
    void 스탬프를_읽을_때_그룹을_즉시_불러오지_않는다() {
        // given
        Group 그룹 = groupRepository.saveAndFlush(기본_그룹_빌더().build());
        GroupStamp 저장한_스탬프 = groupStampRepository.saveAndFlush(기본_스탬프_빌더(그룹).build());

        // when
        GroupStamp 읽은_스탬프 = groupStampRepository.findById(저장한_스탬프.getId()).orElseThrow();

        // then
        assertThat(Hibernate.isInitialized(읽은_스탬프.getGroup())).isFalse();
    }

    @Test
    void 한_그룹은_스탬프를_두_개_가질_수_없다() {
        // given
        Group 그룹 = groupRepository.saveAndFlush(기본_그룹_빌더().build());
        groupStampRepository.saveAndFlush(기본_스탬프_빌더(그룹).build());

        // when
        Throwable throwable = catchThrowable(
                () -> groupStampRepository.saveAndFlush(기본_스탬프_빌더(그룹).text("추가").build())
        );

        // then
        assertThat(throwable).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 멤버를_저장하고_모든_필드를_그대로_읽는다() {
        // given
        Group 그룹 = groupRepository.saveAndFlush(기본_그룹_빌더().build());
        Device 기기 = 기기를_저장한다();
        GroupMember 저장한_멤버 =
                groupMemberRepository.saveAndFlush(그룹장_빌더(그룹, 기기).build());

        // when
        GroupMember 읽은_멤버 = groupMemberRepository.findById(저장한_멤버.getId()).orElseThrow();

        // then
        assertThat(읽은_멤버.getPublicId()).isNotNull();
        assertThat(읽은_멤버.getGroup().getId()).isEqualTo(그룹.getId());
        assertThat(읽은_멤버.getDevice().getId()).isEqualTo(기기.getId());
        assertThat(읽은_멤버.getRole()).isEqualTo(GroupRole.OWNER);
        assertThat(읽은_멤버.getLeftAt()).isNull();
    }

    @Test
    void 멤버를_읽을_때_그룹과_기기를_즉시_불러오지_않는다() {
        // given
        Group 그룹 = groupRepository.saveAndFlush(기본_그룹_빌더().build());
        GroupMember 저장한_멤버 =
                groupMemberRepository.saveAndFlush(그룹장_빌더(그룹, 기기를_저장한다()).build());

        // when
        GroupMember 읽은_멤버 = groupMemberRepository.findById(저장한_멤버.getId()).orElseThrow();

        // then
        assertThat(Hibernate.isInitialized(읽은_멤버.getGroup())).isFalse();
        assertThat(Hibernate.isInitialized(읽은_멤버.getDevice())).isFalse();
    }

    @Test
    void 같은_기기가_한_그룹의_활동_멤버로_두_번_들어갈_수_없다() {
        // given
        Group 그룹 = groupRepository.saveAndFlush(기본_그룹_빌더().build());
        Device 기기 = 기기를_저장한다();
        groupMemberRepository.saveAndFlush(일반_멤버_빌더(그룹, 기기).build());

        // when
        Throwable throwable = catchThrowable(
                () -> groupMemberRepository.saveAndFlush(일반_멤버_빌더(그룹, 기기).build())
        );

        // then
        assertThat(throwable).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 나간_뒤에는_같은_기기가_같은_그룹에_다시_들어올_수_있다() {
        // given
        Group 그룹 = groupRepository.saveAndFlush(기본_그룹_빌더().build());
        Device 기기 = 기기를_저장한다();
        GroupMember 먼저_들어온_멤버 =
                groupMemberRepository.saveAndFlush(일반_멤버_빌더(그룹, 기기).build());
        내보낸다(먼저_들어온_멤버.getId());

        // when
        GroupMember 다시_들어온_멤버 =
                groupMemberRepository.saveAndFlush(일반_멤버_빌더(그룹, 기기).build());

        // then
        assertThat(다시_들어온_멤버.getId()).isNotEqualTo(먼저_들어온_멤버.getId());
        assertThat(활동_멤버_수(그룹.getId())).isOne();
    }

    @Test
    void 한_그룹에_그룹장이_둘일_수_없다() {
        // given
        Group 그룹 = groupRepository.saveAndFlush(기본_그룹_빌더().build());
        groupMemberRepository.saveAndFlush(그룹장_빌더(그룹, 기기를_저장한다()).build());

        // when
        Throwable throwable = catchThrowable(() -> groupMemberRepository.saveAndFlush(
                그룹장_빌더(그룹, 기기를_저장한다()).build()
        ));

        // then
        assertThat(throwable).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 그룹장이_나가면_다른_기기가_그룹장이_될_수_있다() {
        // given
        Group 그룹 = groupRepository.saveAndFlush(기본_그룹_빌더().build());
        GroupMember 먼저_그룹장 =
                groupMemberRepository.saveAndFlush(그룹장_빌더(그룹, 기기를_저장한다()).build());
        내보낸다(먼저_그룹장.getId());

        // when
        GroupMember 새_그룹장 =
                groupMemberRepository.saveAndFlush(그룹장_빌더(그룹, 기기를_저장한다()).build());

        // then
        assertThat(새_그룹장.getRole()).isEqualTo(GroupRole.OWNER);
        assertThat(활동_멤버_수(그룹.getId())).isOne();
    }

    @Test
    void 한_기기가_여러_그룹에_동시에_속할_수_있다() {
        // given
        Device 기기 = 기기를_저장한다();
        Group 첫_그룹 = groupRepository.saveAndFlush(기본_그룹_빌더().name("첫모임").build());
        Group 둘째_그룹 = groupRepository.saveAndFlush(기본_그룹_빌더().name("둘째모임").build());

        // when
        groupMemberRepository.saveAndFlush(그룹장_빌더(첫_그룹, 기기).build());
        groupMemberRepository.saveAndFlush(일반_멤버_빌더(둘째_그룹, 기기).build());

        // then
        assertThat(활동_멤버_수(첫_그룹.getId())).isOne();
        assertThat(활동_멤버_수(둘째_그룹.getId())).isOne();
    }

    @Test
    void 그룹을_지우지_않고_삭제_시각만_남긴다() {
        // given
        Group 그룹 = groupRepository.saveAndFlush(기본_그룹_빌더().build());

        // when
        삭제한다(그룹.getId());

        // then
        assertThat(groupRepository.findById(그룹.getId())).isPresent();
        assertThat(jdbcClient.sql("SELECT deleted_at FROM groups WHERE id = ?")
                .param(그룹.getId())
                .query(Instant.class)
                .single()).isNotNull();
    }

    private Device 기기를_저장한다() {
        return deviceRepository.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
    }

    private long 활동_멤버_수(Long groupId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM group_members WHERE group_id = ? AND left_at IS NULL")
                .param(groupId)
                .query(Long.class)
                .single();
    }

    private void 삭제한다(Long groupId) {
        jdbcClient.sql("UPDATE groups SET deleted_at = NOW() WHERE id = ?")
                .param(groupId)
                .update();
    }

    private void 내보낸다(Long memberId) {
        jdbcClient.sql("UPDATE group_members SET left_at = NOW() WHERE id = ?")
                .param(memberId)
                .update();
    }
}
