package com.pheeeew.groups.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_그룹_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_스탬프_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.그룹장_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.일반_멤버_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.groups.application.dto.GroupMemberResult;
import com.pheeeew.groups.application.dto.GroupMemberListResult;
import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupJoinType;
import com.pheeeew.groups.domain.GroupMember;
import com.pheeeew.groups.domain.GroupRole;
import com.pheeeew.groups.domain.repository.GroupMemberRepository;
import com.pheeeew.groups.domain.repository.GroupRepository;
import com.pheeeew.groups.domain.repository.GroupStampRepository;
import com.pheeeew.groups.exception.GroupErrorCode;
import com.pheeeew.groups.exception.GroupException;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@PostgisDataJpaTest
class GroupMemberListIntegrationTest {

    private static final int 기본_페이지_크기 = 20;
    private static final List<String> 멤버_닉네임 = List.of("멤버가", "멤버나", "멤버다", "멤버라");

    @Autowired
    private GroupService groupService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupStampRepository groupStampRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void 가입_순서대로_멤버를_조회한다() {
        // given
        Device 그룹장 = 기기를_저장한다("방장");
        Device 먼저_가입한_기기 = 기기를_저장한다("먼저");
        Device 나중에_가입한_기기 = 기기를_저장한다("나중");
        Group 그룹 = 그룹을_저장한다("가입모임", 그룹장);
        groupMemberRepository.save(일반_멤버_빌더(그룹, 먼저_가입한_기기).build());
        groupMemberRepository.save(일반_멤버_빌더(그룹, 나중에_가입한_기기).build());
        영속성_컨텍스트를_비운다();

        // when
        GroupMemberListResult 결과 = 조회한다(그룹, 그룹장, null, 기본_페이지_크기);

        // then
        assertThat(결과.items())
                .extracting(GroupMemberResult::nickname, GroupMemberResult::role)
                .containsExactly(
                        tuple("방장", GroupRole.OWNER),
                        tuple("먼저", GroupRole.MEMBER),
                        tuple("나중", GroupRole.MEMBER)
                );
        assertThat(결과.hasNext()).isFalse();
        assertThat(결과.nextCursor()).isNull();
    }

    @Test
    void 가입_시각이_같은_멤버도_각각_정확히_한_번_조회된다() {
        // given
        Device 그룹장 = 기기를_저장한다("방장");
        Group 그룹 = 그룹을_저장한다("동시모임", 그룹장);
        for (int 순번 = 0; 순번 < 4; 순번++) {
            groupMemberRepository.save(일반_멤버_빌더(그룹, 기기를_저장한다(멤버_닉네임.get(순번))).build());
        }
        가입_시각을_고정한다(그룹, "2026-10-05 00:00:00+00");
        영속성_컨텍스트를_비운다();

        // when
        List<GroupMemberResult> 전체 = 끝까지_순회한다(그룹, 그룹장, 2);

        // then
        assertThat(전체).extracting(GroupMemberResult::nickname)
                .containsExactly("방장", "멤버가", "멤버나", "멤버다", "멤버라");
    }

    @Test
    void 나간_멤버는_목록에_나오지_않는다() {
        // given
        Device 그룹장 = 기기를_저장한다("방장");
        Device 남은_기기 = 기기를_저장한다("남은이");
        Device 나간_기기 = 기기를_저장한다("나간이");
        Group 그룹 = 그룹을_저장한다("탈퇴모임", 그룹장);
        groupMemberRepository.save(일반_멤버_빌더(그룹, 남은_기기).build());
        GroupMember 떠난_멤버 = groupMemberRepository.save(일반_멤버_빌더(그룹, 나간_기기).build());
        떠난_멤버.leave(Instant.now());
        영속성_컨텍스트를_비운다();

        // when
        GroupMemberListResult 결과 = 조회한다(그룹, 그룹장, null, 기본_페이지_크기);

        // then
        assertThat(결과.items()).extracting(GroupMemberResult::nickname)
                .containsExactly("방장", "남은이");
    }

    @Test
    void 전체_페이지를_이은_길이가_상세의_인원수와_같다() {
        // given
        Device 그룹장 = 기기를_저장한다("방장");
        Group 그룹 = 그룹을_저장한다("인원모임", 그룹장);
        for (int 순번 = 0; 순번 < 4; 순번++) {
            groupMemberRepository.save(일반_멤버_빌더(그룹, 기기를_저장한다(멤버_닉네임.get(순번))).build());
        }
        GroupMember 떠난_멤버 = groupMemberRepository.save(일반_멤버_빌더(그룹, 기기를_저장한다("나간이")).build());
        떠난_멤버.leave(Instant.now());
        영속성_컨텍스트를_비운다();

        // when
        List<GroupMemberResult> 전체 = 끝까지_순회한다(그룹, 그룹장, 2);
        long 인원수 = groupService.findDetailV3(그룹.getPublicId(), 그룹장.getPublicId()).memberCount();

        // then
        assertThat(전체).hasSize((int) 인원수);
        assertThat(인원수).isEqualTo(5L);
    }

    @Test
    void 닉네임을_정하지_않은_기기는_익명으로_조회된다() {
        // given
        Device 그룹장 = 기기를_저장한다("방장");
        Device 닉네임_없는_기기 = 기기를_저장한다(null);
        Group 그룹 = 그룹을_저장한다("익명모임", 그룹장);
        groupMemberRepository.save(일반_멤버_빌더(그룹, 닉네임_없는_기기).build());
        영속성_컨텍스트를_비운다();

        // when
        GroupMemberListResult 결과 = 조회한다(그룹, 그룹장, null, 기본_페이지_크기);

        // then
        assertThat(결과.items()).extracting(GroupMemberResult::nickname)
                .containsExactly("방장", "익명");
    }

    @Test
    void 비멤버는_멤버_목록을_볼_수_없다() {
        // given
        Device 그룹장 = 기기를_저장한다("방장");
        Device 비멤버 = 기기를_저장한다("남이");
        Group 그룹 = 그룹을_저장한다("권한모임", 그룹장);
        영속성_컨텍스트를_비운다();

        // when / then
        그룹_예외를_검증한다(
                () -> 조회한다(그룹, 비멤버, null, 기본_페이지_크기),
                GroupErrorCode.GROUP_MEMBER_ONLY
        );
    }

    @Test
    void 나간_멤버도_멤버_목록을_볼_수_없다() {
        // given
        Device 그룹장 = 기기를_저장한다("방장");
        Device 나간_기기 = 기기를_저장한다("나간이");
        Group 그룹 = 그룹을_저장한다("탈퇴권한", 그룹장);
        GroupMember 떠난_멤버 = groupMemberRepository.save(일반_멤버_빌더(그룹, 나간_기기).build());
        떠난_멤버.leave(Instant.now());
        영속성_컨텍스트를_비운다();

        // when / then
        그룹_예외를_검증한다(
                () -> 조회한다(그룹, 나간_기기, null, 기본_페이지_크기),
                GroupErrorCode.GROUP_MEMBER_ONLY
        );
    }

    @Test
    void 없는_그룹의_멤버_목록은_찾을_수_없다() {
        // given
        Device 나 = 기기를_저장한다("방장");
        영속성_컨텍스트를_비운다();

        // when / then
        그룹_예외를_검증한다(
                () -> groupService.findMembers(UUID.randomUUID(), 나.getPublicId(), null, 기본_페이지_크기),
                GroupErrorCode.GROUP_NOT_FOUND
        );
    }

    @Test
    void 삭제된_그룹은_비멤버_판정보다_먼저_찾을_수_없다() {
        // given
        Device 그룹장 = 기기를_저장한다("방장");
        Device 비멤버 = 기기를_저장한다("남이");
        Group 그룹 = 그룹을_저장한다("삭제모임", 그룹장);
        그룹.delete(Instant.now());
        영속성_컨텍스트를_비운다();

        // when / then
        그룹_예외를_검증한다(
                () -> 조회한다(그룹, 비멤버, null, 기본_페이지_크기),
                GroupErrorCode.GROUP_NOT_FOUND
        );
    }

    @Test
    void 사용할_수_없는_커서로_조회하면_거부한다() {
        // given
        Device 그룹장 = 기기를_저장한다("방장");
        Group 그룹 = 그룹을_저장한다("커서모임", 그룹장);
        영속성_컨텍스트를_비운다();

        // when / then
        그룹_예외를_검증한다(
                () -> 조회한다(그룹, 그룹장, "not-base64!!", 기본_페이지_크기),
                GroupErrorCode.GROUP_INVALID_CURSOR
        );
    }

    @Test
    void 탐색_커서로_멤버_목록을_조회하면_거부한다() {
        // given
        Device 그룹장 = 기기를_저장한다("방장");
        Group 그룹 = 그룹을_저장한다("교차모임", 그룹장);
        영속성_컨텍스트를_비운다();
        String 탐색_커서 = GroupCursorCodec.encodeExplore(Instant.parse("2026-10-11T09:00:00Z"), 1L);

        // when / then
        그룹_예외를_검증한다(
                () -> 조회한다(그룹, 그룹장, 탐색_커서, 기본_페이지_크기),
                GroupErrorCode.GROUP_INVALID_CURSOR
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 30})
    void 멤버_수와_관계없이_네_번의_SQL로_조회한다(int 멤버_수) {
        // given
        Device 그룹장 = 기기를_저장한다("방장");
        Group 그룹 = 그룹을_저장한다("성능모임", 그룹장);
        for (int 순번 = 1; 순번 < 멤버_수; 순번++) {
            groupMemberRepository.save(일반_멤버_빌더(그룹, 기기를_저장한다(null)).build());
        }
        영속성_컨텍스트를_비운다();
        Statistics 통계 = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean 기존_통계_설정 = 통계.isStatisticsEnabled();
        통계.setStatisticsEnabled(true);
        try {
            통계.clear();

            // when
            GroupMemberListResult 결과 = 조회한다(그룹, 그룹장, null, 멤버_수);
            long 조회_SQL_횟수 = 통계.getPrepareStatementCount();

            // then
            assertThat(결과.items()).hasSize(멤버_수);
            assertThat(조회_SQL_횟수).isEqualTo(4);
        } finally {
            통계.clear();
            통계.setStatisticsEnabled(기존_통계_설정);
        }
    }

    private GroupMemberListResult 조회한다(Group 그룹, Device 기기, String 커서, int 페이지_크기) {
        return groupService.findMembers(그룹.getPublicId(), 기기.getPublicId(), 커서, 페이지_크기);
    }

    private List<GroupMemberResult> 끝까지_순회한다(Group 그룹, Device 기기, int 페이지_크기) {
        List<GroupMemberResult> 전체 = new ArrayList<>();
        String 커서 = null;
        do {
            GroupMemberListResult 페이지 = 조회한다(그룹, 기기, 커서, 페이지_크기);
            전체.addAll(페이지.items());
            커서 = 페이지.nextCursor();
        } while (커서 != null);

        return 전체;
    }

    private void 그룹_예외를_검증한다(Runnable 호출, GroupErrorCode 기대_코드) {
        assertThatThrownBy(호출::run)
                .isInstanceOf(GroupException.class)
                .extracting(throwable -> ((GroupException) throwable).getErrorCode())
                .isEqualTo(기대_코드);
    }

    private Device 기기를_저장한다(String 닉네임) {
        return deviceRepository.save(
                기본_기기_빌더().requestId(UUID.randomUUID()).nickname(닉네임).build()
        );
    }

    private Group 그룹을_저장한다(String 이름, Device 그룹장) {
        Group 그룹 = groupRepository.save(기본_그룹_빌더().name(이름).joinType(GroupJoinType.PRIVATE).build());
        groupStampRepository.save(기본_스탬프_빌더(그룹).build());
        groupMemberRepository.save(그룹장_빌더(그룹, 그룹장).build());

        return 그룹;
    }

    private void 가입_시각을_고정한다(Group 그룹, String 시각) {
        entityManager.flush();
        jdbcClient.sql("UPDATE group_members SET created_at = CAST(? AS TIMESTAMPTZ) WHERE group_id = ?")
                .param(시각)
                .param(그룹.getId())
                .update();
    }

    private void 영속성_컨텍스트를_비운다() {
        entityManager.flush();
        entityManager.clear();
    }
}
