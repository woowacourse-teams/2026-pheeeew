package com.pheeeew.groups.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_그룹_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_스탬프_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.그룹장_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.일반_멤버_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.groups.application.dto.GroupStampItemResult;
import com.pheeeew.groups.application.dto.GroupStampResult;
import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupMember;
import com.pheeeew.groups.domain.StampFrame;
import com.pheeeew.groups.domain.repository.GroupMemberRepository;
import com.pheeeew.groups.domain.repository.GroupRepository;
import com.pheeeew.groups.domain.repository.GroupStampRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
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
class GroupStampQueryIntegrationTest {

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
    void 현재_소속된_그룹의_스탬프만_조회한다() {
        // given
        Device 나 = 기기를_저장한다();
        Device 다른_기기 = 기기를_저장한다();
        Group 내_그룹 = 그룹을_저장한다("내그룹", 나);
        Group 참여한_그룹 = 그룹을_저장한다("참여그룹", 다른_기기);
        groupMemberRepository.save(일반_멤버_빌더(참여한_그룹, 나).build());
        Group 탈퇴한_그룹 = 그룹을_저장한다("탈퇴그룹", 다른_기기);
        GroupMember 탈퇴한_멤버 = groupMemberRepository.save(일반_멤버_빌더(탈퇴한_그룹, 나).build());
        탈퇴한_멤버.leave(Instant.now());
        Group 삭제한_그룹 = 그룹을_저장한다("삭제그룹", 나);
        삭제한_그룹.delete(Instant.now());
        그룹을_저장한다("남의그룹", 다른_기기);
        영속성_컨텍스트를_비운다();

        // when
        List<GroupStampItemResult> 결과 = groupService.findMyStamps(나.getPublicId());

        // then
        assertThat(결과).extracting(GroupStampItemResult::publicId)
                .containsExactly(내_그룹.getPublicId(), 참여한_그룹.getPublicId());
        assertThat(결과).extracting(GroupStampItemResult::name).containsExactly("내그룹", "참여그룹");
        assertThat(결과).extracting(GroupStampItemResult::stamp)
                .containsOnly(GroupStampResult.of("기본", "#FFFFFF", "#4A90D9", StampFrame.CIRCLE));
    }

    @Test
    void 가입_시각이_빠른_순서로_조회하고_같은_시각에는_가입_식별자로_정렬한다() {
        // given
        Device 나 = 기기를_저장한다();
        Group 늦게_가입한_그룹 = 그룹을_저장한다("늦은가입", 나);
        Group 먼저_가입한_그룹 = 그룹을_저장한다("먼저가입", 나);
        Group 같은_시각의_그룹 = 그룹을_저장한다("같은시각", 나);
        entityManager.flush();
        jdbcClient.sql("UPDATE group_members SET created_at = TIMESTAMPTZ '2026-09-28 00:00:00+00' WHERE device_id = ?")
                .param(나.getId())
                .update();
        jdbcClient.sql("UPDATE group_members SET created_at = created_at + INTERVAL '1 hour' WHERE group_id = ?")
                .param(늦게_가입한_그룹.getId())
                .update();
        entityManager.clear();

        // when
        List<GroupStampItemResult> 결과 = groupService.findMyStamps(나.getPublicId());

        // then
        assertThat(결과).extracting(GroupStampItemResult::publicId).containsExactly(
                먼저_가입한_그룹.getPublicId(), 같은_시각의_그룹.getPublicId(), 늦게_가입한_그룹.getPublicId()
        );
    }

    @Test
    void 소속_그룹이_없으면_빈_목록을_반환한다() {
        // given
        Device 나 = 기기를_저장한다();
        영속성_컨텍스트를_비운다();

        // when
        List<GroupStampItemResult> 결과 = groupService.findMyStamps(나.getPublicId());

        // then
        assertThat(결과).isEmpty();
    }

    @Test
    void 등록되지_않은_기기는_조회할_수_없다() {
        // given
        UUID 등록되지_않은_기기 = UUID.randomUUID();

        // when
        Throwable 예외 = catchThrowable(() -> groupService.findMyStamps(등록되지_않은_기기));

        // then
        assertThat(예외).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) 예외).getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void 그룹_수와_관계없이_두_번의_SQL로_스탬프_목록을_조회한다(int 그룹_수) {
        // given
        Device 나 = 기기를_저장한다();
        for (int index = 0; index < 그룹_수; index++) {
            그룹을_저장한다("모임" + index, 나);
        }
        영속성_컨텍스트를_비운다();
        Statistics 통계 = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean 기존_통계_설정 = 통계.isStatisticsEnabled();
        통계.setStatisticsEnabled(true);
        try {
            통계.clear();
            groupService.findMine(나.getPublicId());
            long 기존_SQL_횟수 = 통계.getPrepareStatementCount();
            entityManager.clear();
            통계.clear();

            // when
            List<GroupStampItemResult> 결과 = groupService.findMyStamps(나.getPublicId());
            long 스탬프_SQL_횟수 = 통계.getPrepareStatementCount();

            // then
            assertThat(결과).hasSize(그룹_수);
            assertThat(스탬프_SQL_횟수).isEqualTo(2);
            System.out.printf("그룹 %d개: 기존 목록 SQL %d회, 스탬프 목록 SQL %d회%n", 그룹_수, 기존_SQL_횟수, 스탬프_SQL_횟수);
        } finally {
            통계.clear();
            통계.setStatisticsEnabled(기존_통계_설정);
        }
    }

    private Device 기기를_저장한다() {
        return deviceRepository.save(기본_기기_빌더().requestId(UUID.randomUUID()).build());
    }

    private Group 그룹을_저장한다(String 이름, Device 그룹장) {
        Group 그룹 = groupRepository.save(기본_그룹_빌더().name(이름).build());
        groupStampRepository.save(기본_스탬프_빌더(그룹).build());
        groupMemberRepository.save(그룹장_빌더(그룹, 그룹장).build());
        return 그룹;
    }

    private void 영속성_컨텍스트를_비운다() {
        entityManager.flush();
        entityManager.clear();
    }
}
