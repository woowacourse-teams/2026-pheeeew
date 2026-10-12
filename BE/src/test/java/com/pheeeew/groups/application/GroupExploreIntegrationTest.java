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
import com.pheeeew.groups.application.dto.GroupExploreResult;
import com.pheeeew.groups.application.dto.GroupExploreListResult;
import com.pheeeew.groups.application.dto.GroupStampResult;
import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupJoinType;
import com.pheeeew.groups.domain.GroupMember;
import com.pheeeew.groups.domain.StampFrame;
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
class GroupExploreIntegrationTest {

    private static final int 기본_페이지_크기 = 20;

    @Autowired
    private GroupExploreService groupExploreService;

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
    void 최근_생성된_그룹부터_조회한다() {
        // given
        Device 나 = 기기를_저장한다();
        Group 가장_오래된_그룹 = 그룹을_저장한다("오래된모임", 나);
        Group 중간_그룹 = 그룹을_저장한다("중간모임", 나);
        Group 가장_최근_그룹 = 그룹을_저장한다("최근모임", 나);
        생성_시각을_고정한다(가장_오래된_그룹, "2026-10-01 00:00:00+00");
        생성_시각을_고정한다(중간_그룹, "2026-10-02 00:00:00+00");
        생성_시각을_고정한다(가장_최근_그룹, "2026-10-03 00:00:00+00");
        영속성_컨텍스트를_비운다();

        // when
        GroupExploreListResult 결과 = 조회한다(나, null, null, 기본_페이지_크기);

        // then
        assertThat(결과.items()).extracting(GroupExploreResult::publicId).containsExactly(
                가장_최근_그룹.getPublicId(), 중간_그룹.getPublicId(), 가장_오래된_그룹.getPublicId()
        );
        assertThat(결과.hasNext()).isFalse();
        assertThat(결과.nextCursor()).isNull();
    }

    @Test
    void 커서로_이어_조회하면_경계에서_중복도_누락도_없다() {
        // given
        Device 나 = 기기를_저장한다();
        List<Group> 오래된_순서 = 생성_시각이_다른_그룹들을_저장한다(나, 5);
        영속성_컨텍스트를_비운다();

        // when
        GroupExploreListResult 첫_페이지 = 조회한다(나, null, null, 2);
        List<GroupExploreResult> 전체 = 끝까지_순회한다(나, null, 2);

        // then
        assertThat(첫_페이지.items()).hasSize(2);
        assertThat(첫_페이지.hasNext()).isTrue();
        assertThat(첫_페이지.nextCursor()).isNotNull();
        assertThat(전체).extracting(GroupExploreResult::publicId)
                .containsExactlyElementsOf(최근_순서의_공개_식별자(오래된_순서));
    }

    @Test
    void 생성_시각이_같은_그룹도_각각_정확히_한_번_조회된다() {
        // given
        Device 나 = 기기를_저장한다();
        List<Group> 저장_순서 = new ArrayList<>();
        for (int 순번 = 0; 순번 < 5; 순번++) {
            Group 그룹 = 그룹을_저장한다("동시모임" + 순번, 나);
            생성_시각을_고정한다(그룹, "2026-10-05 00:00:00+00");
            저장_순서.add(그룹);
        }
        영속성_컨텍스트를_비운다();

        // when
        List<GroupExploreResult> 전체 = 끝까지_순회한다(나, null, 2);

        // then
        assertThat(전체).extracting(GroupExploreResult::publicId)
                .containsExactlyElementsOf(최근_순서의_공개_식별자(저장_순서));
    }

    @Test
    void 삭제된_그룹은_조회되지_않는다() {
        // given
        Device 나 = 기기를_저장한다();
        Group 남은_그룹 = 그룹을_저장한다("남은모임", 나);
        Group 삭제한_그룹 = 그룹을_저장한다("삭제모임", 나);
        삭제한_그룹.delete(Instant.now());
        영속성_컨텍스트를_비운다();

        // when
        GroupExploreListResult 결과 = 조회한다(나, null, null, 기본_페이지_크기);

        // then
        assertThat(결과.items()).extracting(GroupExploreResult::publicId)
                .containsExactly(남은_그룹.getPublicId());
    }

    @Test
    void 비공개_그룹도_가입_방식과_함께_조회된다() {
        // given
        Device 나 = 기기를_저장한다();
        Group 비공개_그룹 = 그룹을_저장한다("비공개모임", GroupJoinType.PRIVATE, 나);
        Group 공개_그룹 = 그룹을_저장한다("공개모임", GroupJoinType.PUBLIC, 나);
        영속성_컨텍스트를_비운다();

        // when
        GroupExploreListResult 결과 = 조회한다(나, null, null, 기본_페이지_크기);

        // then
        assertThat(결과.items())
                .extracting(GroupExploreResult::publicId, GroupExploreResult::joinType)
                .containsExactlyInAnyOrder(
                        tuple(비공개_그룹.getPublicId(), GroupJoinType.PRIVATE),
                        tuple(공개_그룹.getPublicId(), GroupJoinType.PUBLIC)
                );
    }

    @Test
    void 나간_멤버는_인원수에_세지_않는다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        Device 남은_멤버 = 기기를_저장한다();
        Device 나간_멤버 = 기기를_저장한다();
        Group 그룹 = 그룹을_저장한다("인원모임", 그룹장);
        groupMemberRepository.save(일반_멤버_빌더(그룹, 남은_멤버).build());
        GroupMember 떠난_멤버 = groupMemberRepository.save(일반_멤버_빌더(그룹, 나간_멤버).build());
        떠난_멤버.leave(Instant.now());
        영속성_컨텍스트를_비운다();

        // when
        GroupExploreListResult 결과 = 조회한다(그룹장, null, null, 기본_페이지_크기);

        // then
        assertThat(결과.items()).singleElement()
                .extracting(GroupExploreResult::memberCount)
                .isEqualTo(2L);
    }

    @Test
    void 지금_속한_그룹만_가입_여부가_참이다() {
        // given
        Device 나 = 기기를_저장한다();
        Device 남 = 기기를_저장한다();
        Group 내_그룹 = 그룹을_저장한다("내모임", 나);
        Group 남의_그룹 = 그룹을_저장한다("남의모임", 남);
        Group 나간_그룹 = 그룹을_저장한다("나간모임", 남);
        GroupMember 나간_멤버 = groupMemberRepository.save(일반_멤버_빌더(나간_그룹, 나).build());
        나간_멤버.leave(Instant.now());
        영속성_컨텍스트를_비운다();

        // when
        GroupExploreListResult 결과 = 조회한다(나, null, null, 기본_페이지_크기);

        // then
        assertThat(결과.items())
                .extracting(GroupExploreResult::publicId, GroupExploreResult::joined)
                .containsExactlyInAnyOrder(
                        tuple(내_그룹.getPublicId(), true),
                        tuple(남의_그룹.getPublicId(), false),
                        tuple(나간_그룹.getPublicId(), false)
                );
    }

    @Test
    void 스탬프의_글자와_색과_테두리를_그대로_조회한다() {
        // given
        Device 나 = 기기를_저장한다();
        그룹을_저장한다("스탬프모임", 나);
        영속성_컨텍스트를_비운다();

        // when
        GroupExploreListResult 결과 = 조회한다(나, null, null, 기본_페이지_크기);

        // then
        assertThat(결과.items()).singleElement()
                .extracting(GroupExploreResult::stamp)
                .isEqualTo(GroupStampResult.of("기본", "#FFFFFF", "#4A90D9", StampFrame.CIRCLE));
    }

    @Test
    void 사용할_수_없는_커서로_조회하면_거부한다() {
        // given
        Device 나 = 기기를_저장한다();
        영속성_컨텍스트를_비운다();

        // when / then
        assertThatThrownBy(() -> 조회한다(나, null, "not-base64!!", 기본_페이지_크기))
                .isInstanceOf(GroupException.class)
                .extracting(throwable -> ((GroupException) throwable).getErrorCode())
                .isEqualTo(GroupErrorCode.GROUP_INVALID_CURSOR);
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 30})
    void 그룹_수와_관계없이_한_번의_SQL로_조회한다(int 그룹_수) {
        // given
        Device 나 = 기기를_저장한다();
        for (int 순번 = 0; 순번 < 그룹_수; 순번++) {
            그룹을_저장한다("모임" + 순번, 나);
        }
        영속성_컨텍스트를_비운다();
        Statistics 통계 = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean 기존_통계_설정 = 통계.isStatisticsEnabled();
        통계.setStatisticsEnabled(true);
        try {
            통계.clear();

            // when
            GroupExploreListResult 결과 = 조회한다(나, null, null, 그룹_수);
            long 조회_SQL_횟수 = 통계.getPrepareStatementCount();

            // then
            assertThat(결과.items()).hasSize(그룹_수);
            assertThat(조회_SQL_횟수).isEqualTo(1);
        } finally {
            통계.clear();
            통계.setStatisticsEnabled(기존_통계_설정);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"한숨", "숨모", "모임"})
    void 그룹명_부분_일치로_검색한다(String 검색어) {
        // given
        Device 나 = 기기를_저장한다();
        Group 찾을_그룹 = 그룹을_저장한다("한숨모임", 나);
        그룹을_저장한다("딴이름", 나);
        영속성_컨텍스트를_비운다();

        // when
        GroupExploreListResult 결과 = 조회한다(나, 검색어, null, 기본_페이지_크기);

        // then
        assertThat(결과.items()).extracting(GroupExploreResult::publicId)
                .containsExactly(찾을_그룹.getPublicId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "ABC", "AbC"})
    void 대소문자를_무시하고_검색한다(String 검색어) {
        // given
        Device 나 = 기기를_저장한다();
        Group 찾을_그룹 = 그룹을_저장한다("ABCgroup", 나);
        영속성_컨텍스트를_비운다();

        // when
        GroupExploreListResult 결과 = 조회한다(나, 검색어, null, 기본_페이지_크기);

        // then
        assertThat(결과.items()).extracting(GroupExploreResult::publicId)
                .containsExactly(찾을_그룹.getPublicId());
    }

    @Test
    void 검색어의_앞뒤_공백을_뗀다() {
        // given
        Device 나 = 기기를_저장한다();
        Group 찾을_그룹 = 그룹을_저장한다("한숨모임", 나);
        그룹을_저장한다("딴이름", 나);
        영속성_컨텍스트를_비운다();

        // when
        GroupExploreListResult 결과 = 조회한다(나, "  모임  ", null, 기본_페이지_크기);

        // then
        assertThat(결과.items()).extracting(GroupExploreResult::publicId)
                .containsExactly(찾을_그룹.getPublicId());
    }

    @Test
    void 검색어가_공백뿐이면_전체_목록을_조회한다() {
        // given
        Device 나 = 기기를_저장한다();
        Group 첫_그룹 = 그룹을_저장한다("한숨모임", 나);
        Group 둘째_그룹 = 그룹을_저장한다("딴이름", 나);
        영속성_컨텍스트를_비운다();

        // when
        GroupExploreListResult 결과 = 조회한다(나, "   ", null, 기본_페이지_크기);

        // then
        assertThat(결과.items()).extracting(GroupExploreResult::publicId)
                .containsExactlyInAnyOrder(첫_그룹.getPublicId(), 둘째_그룹.getPublicId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"없는이름", "열한글자가넘는검색어요", "%", "_"})
    void 일치하는_그룹이_없으면_빈_목록을_조회한다(String 검색어) {
        // given
        Device 나 = 기기를_저장한다();
        그룹을_저장한다("한숨모임", 나);
        그룹을_저장한다("딴이름", 나);
        영속성_컨텍스트를_비운다();

        // when
        GroupExploreListResult 결과 = 조회한다(나, 검색어, null, 기본_페이지_크기);

        // then
        assertThat(결과.items()).isEmpty();
        assertThat(결과.hasNext()).isFalse();
        assertThat(결과.nextCursor()).isNull();
    }

    @Test
    void 검색_결과도_커서로_이어_조회하면_중복도_누락도_없다() {
        // given
        Device 나 = 기기를_저장한다();
        List<Group> 오래된_순서 = new ArrayList<>();
        for (int 순번 = 0; 순번 < 3; 순번++) {
            Group 그룹 = 그룹을_저장한다("찾을모임" + 순번, 나);
            생성_시각을_고정한다(그룹, "2026-10-0" + (순번 + 1) + " 00:00:00+00");
            오래된_순서.add(그룹);
        }
        그룹을_저장한다("딴이름", 나);
        영속성_컨텍스트를_비운다();

        // when
        List<GroupExploreResult> 전체 = 끝까지_순회한다(나, "찾을", 2);

        // then
        assertThat(전체).extracting(GroupExploreResult::publicId)
                .containsExactlyElementsOf(최근_순서의_공개_식별자(오래된_순서));
    }

    @Test
    void 삭제된_그룹은_검색에도_나오지_않는다() {
        // given
        Device 나 = 기기를_저장한다();
        Group 남은_그룹 = 그룹을_저장한다("남은모임", 나);
        Group 삭제한_그룹 = 그룹을_저장한다("삭제모임", 나);
        삭제한_그룹.delete(Instant.now());
        영속성_컨텍스트를_비운다();

        // when
        GroupExploreListResult 결과 = 조회한다(나, "모임", null, 기본_페이지_크기);

        // then
        assertThat(결과.items()).extracting(GroupExploreResult::publicId)
                .containsExactly(남은_그룹.getPublicId());
    }

    private GroupExploreListResult 조회한다(Device 기기, String 검색어, String 커서, int 페이지_크기) {
        return groupExploreService.findAll(기기.getPublicId(), 검색어, 커서, 페이지_크기);
    }

    private List<GroupExploreResult> 끝까지_순회한다(Device 기기, String 검색어, int 페이지_크기) {
        List<GroupExploreResult> 전체 = new ArrayList<>();
        String 커서 = null;
        do {
            GroupExploreListResult 페이지 = 조회한다(기기, 검색어, 커서, 페이지_크기);
            전체.addAll(페이지.items());
            커서 = 페이지.nextCursor();
        } while (커서 != null);

        return 전체;
    }

    private List<Group> 생성_시각이_다른_그룹들을_저장한다(Device 그룹장, int 그룹_수) {
        List<Group> 오래된_순서 = new ArrayList<>();
        for (int 순번 = 0; 순번 < 그룹_수; 순번++) {
            Group 그룹 = 그룹을_저장한다("모임" + 순번, 그룹장);
            생성_시각을_고정한다(그룹, "2026-10-0" + (순번 + 1) + " 00:00:00+00");
            오래된_순서.add(그룹);
        }

        return 오래된_순서;
    }

    private List<UUID> 최근_순서의_공개_식별자(List<Group> 오래된_순서) {
        return 오래된_순서.reversed().stream()
                .map(Group::getPublicId)
                .toList();
    }

    private Device 기기를_저장한다() {
        return deviceRepository.save(기본_기기_빌더().requestId(UUID.randomUUID()).build());
    }

    private Group 그룹을_저장한다(String 이름, Device 그룹장) {
        return 그룹을_저장한다(이름, GroupJoinType.PRIVATE, 그룹장);
    }

    private Group 그룹을_저장한다(String 이름, GroupJoinType 가입_방식, Device 그룹장) {
        Group 그룹 = groupRepository.save(기본_그룹_빌더().name(이름).joinType(가입_방식).build());
        groupStampRepository.save(기본_스탬프_빌더(그룹).build());
        groupMemberRepository.save(그룹장_빌더(그룹, 그룹장).build());

        return 그룹;
    }

    private void 생성_시각을_고정한다(Group 그룹, String 시각) {
        entityManager.flush();
        jdbcClient.sql("UPDATE groups SET created_at = CAST(? AS TIMESTAMPTZ) WHERE id = ?")
                .param(시각)
                .param(그룹.getId())
                .update();
    }

    private void 영속성_컨텍스트를_비운다() {
        entityManager.flush();
        entityManager.clear();
    }
}
