package com.pheeeew.groups.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.groups.application.dto.GroupPreviewResult;
import com.pheeeew.groups.application.dto.GroupResult;
import com.pheeeew.groups.application.dto.GroupStampCommand;
import com.pheeeew.groups.domain.GroupRole;
import com.pheeeew.groups.domain.StampFrame;
import com.pheeeew.groups.domain.repository.GroupMemberRepository;
import com.pheeeew.groups.domain.repository.GroupRepository;
import com.pheeeew.groups.domain.repository.GroupStampRepository;
import com.pheeeew.groups.exception.GroupErrorCode;
import com.pheeeew.groups.exception.GroupException;
import com.pheeeew.support.PostgisDataJpaTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GroupMembershipIntegrationTest {

    private static final int 동시_요청_수 = 6;

    @Autowired
    private GroupService groupService;

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
    void 초대_코드로_들어가기_전에_그룹을_미리_본다() {
        // given
        GroupResult 그룹 = 그룹을_만든다("한숨모임");
        groupService.join(기기를_저장한다().getPublicId(), 그룹.inviteCode());

        // when
        GroupPreviewResult 미리보기 = groupService.findByInviteCode(그룹.inviteCode());

        // then
        assertThat(미리보기.publicId()).isEqualTo(그룹.publicId());
        assertThat(미리보기.name()).isEqualTo("한숨모임");
        assertThat(미리보기.memberCount()).isEqualTo(2);
        assertThat(미리보기.stamp().text()).isEqualTo("기본");
    }

    @Test
    void 없는_초대_코드로는_미리_볼_수_없다() {
        // when
        Throwable throwable = catchThrowable(() -> groupService.findByInviteCode("ZZZZZZ"));

        // then
        그룹_오류다(throwable, GroupErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void 삭제된_그룹은_미리_볼_수_없다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프());
        groupService.delete(그룹.publicId(), 그룹장.getPublicId());

        // when
        Throwable throwable = catchThrowable(() -> groupService.findByInviteCode(그룹.inviteCode()));

        // then
        그룹_오류다(throwable, GroupErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void 초대_코드로_그룹에_들어간다() {
        // given
        GroupResult 그룹 = 그룹을_만든다("한숨모임");
        Device 들어올_기기 = 기기를_저장한다();

        // when
        GroupResult 결과 = groupService.join(들어올_기기.getPublicId(), 그룹.inviteCode());

        // then
        assertThat(결과.name()).isEqualTo("한숨모임");
        assertThat(결과.role()).isEqualTo(GroupRole.MEMBER);
        assertThat(결과.memberCount()).isEqualTo(2);
    }

    @Test
    void 이미_속한_그룹에_다시_요청하면_거절한다() {
        // given
        GroupResult 그룹 = 그룹을_만든다("한숨모임");
        Device 들어올_기기 = 기기를_저장한다();
        groupService.join(들어올_기기.getPublicId(), 그룹.inviteCode());

        // when
        Throwable throwable = catchThrowable(
                () -> groupService.join(들어올_기기.getPublicId(), 그룹.inviteCode())
        );

        // then
        그룹_오류다(throwable, GroupErrorCode.GROUP_ALREADY_JOINED);
        assertThat(멤버_행_수(그룹.publicId())).isEqualTo(2);
    }

    @Test
    void 그룹장이_자기_그룹_코드로_요청하면_거절한다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프());

        // when
        Throwable throwable = catchThrowable(() -> groupService.join(그룹장.getPublicId(), 그룹.inviteCode()));

        // then
        그룹_오류다(throwable, GroupErrorCode.GROUP_ALREADY_JOINED);
        assertThat(멤버_행_수(그룹.publicId())).isOne();
    }

    @Test
    void 없는_초대_코드로는_들어갈_수_없다() {
        // given
        그룹을_만든다("한숨모임");
        Device 들어올_기기 = 기기를_저장한다();

        // when
        Throwable throwable = catchThrowable(
                () -> groupService.join(들어올_기기.getPublicId(), "ZZZZZZ")
        );

        // then
        그룹_오류다(throwable, GroupErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void 삭제된_그룹의_초대_코드로는_들어갈_수_없다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프());
        groupService.delete(그룹.publicId(), 그룹장.getPublicId());

        // when
        Throwable throwable = catchThrowable(
                () -> groupService.join(기기를_저장한다().getPublicId(), 그룹.inviteCode())
        );

        // then
        그룹_오류다(throwable, GroupErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void 재발급하면_이전_초대_코드로는_들어갈_수_없다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프());
        String 이전_코드 = 그룹.inviteCode();
        String 새_코드 = groupService.reissueInviteCode(그룹.publicId(), 그룹장.getPublicId()).inviteCode();

        // when
        Throwable throwable = catchThrowable(
                () -> groupService.join(기기를_저장한다().getPublicId(), 이전_코드)
        );

        // then
        그룹_오류다(throwable, GroupErrorCode.GROUP_NOT_FOUND);
        assertThat(groupService.join(기기를_저장한다().getPublicId(), 새_코드).memberCount()).isEqualTo(2);
    }

    @Test
    void 그룹을_나가면_목록과_인원수에서_빠진다() {
        // given
        GroupResult 그룹 = 그룹을_만든다("한숨모임");
        Device 들어온_기기 = 기기를_저장한다();
        groupService.join(들어온_기기.getPublicId(), 그룹.inviteCode());

        // when
        groupService.leave(그룹.publicId(), 들어온_기기.getPublicId());

        // then
        assertThat(groupService.findMine(들어온_기기.getPublicId())).isEmpty();
        assertThat(활동_멤버_수(그룹.publicId())).isOne();
    }

    @Test
    void 그룹장은_그룹을_나갈_수_없다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프());

        // when
        Throwable throwable = catchThrowable(() -> groupService.leave(그룹.publicId(), 그룹장.getPublicId()));

        // then
        그룹_오류다(throwable, GroupErrorCode.GROUP_OWNER_CANNOT_LEAVE);
        assertThat(활동_멤버_수(그룹.publicId())).isOne();
    }

    @Test
    void 나갔다_다시_들어오면_새_멤버십이_생긴다() {
        // given
        GroupResult 그룹 = 그룹을_만든다("한숨모임");
        Device 기기 = 기기를_저장한다();
        groupService.join(기기.getPublicId(), 그룹.inviteCode());
        groupService.leave(그룹.publicId(), 기기.getPublicId());

        // when
        GroupResult 결과 = groupService.join(기기.getPublicId(), 그룹.inviteCode());

        // then
        assertThat(결과.memberCount()).isEqualTo(2);
        assertThat(멤버_행_수(그룹.publicId())).isEqualTo(3);
        assertThat(활동_멤버_수(그룹.publicId())).isEqualTo(2);
    }

    @Test
    void 같은_기기가_동시에_여러_번_참여해도_멤버는_하나만_생긴다() throws Exception {
        // given
        GroupResult 그룹 = 그룹을_만든다("한숨모임");
        Device 기기 = 기기를_저장한다();

        // when
        List<Boolean> 성공_여부들 = 동시에_참여한다(기기.getPublicId(), 그룹.inviteCode());

        // then
        assertThat(성공_여부들).hasSize(동시_요청_수);
        assertThat(성공_여부들).filteredOn(성공 -> 성공).hasSize(1);
        assertThat(활동_멤버_수(그룹.publicId())).isEqualTo(2);
    }

    @Test
    void 속하지_않은_그룹에서는_나갈_수_없다() {
        // given
        GroupResult 그룹 = 그룹을_만든다("한숨모임");
        Device 남 = 기기를_저장한다();

        // when
        Throwable throwable = catchThrowable(() -> groupService.leave(그룹.publicId(), 남.getPublicId()));

        // then
        그룹_오류다(throwable, GroupErrorCode.GROUP_MEMBER_ONLY);
    }

    @Test
    void 나가도_이미_쓴_기록은_그룹에_남는다() {
        // given
        GroupResult 그룹 = 그룹을_만든다("한숨모임");
        Device 기기 = 기기를_저장한다();
        groupService.join(기기.getPublicId(), 그룹.inviteCode());

        // when
        groupService.leave(그룹.publicId(), 기기.getPublicId());

        // then
        assertThat(멤버_행_수(그룹.publicId())).isEqualTo(2);
        assertThat(나간_시각이_있는_멤버_수(그룹.publicId())).isOne();
    }

    private List<Boolean> 동시에_참여한다(UUID devicePublicId, String inviteCode) throws Exception {
        CountDownLatch 준비 = new CountDownLatch(동시_요청_수);
        CountDownLatch 출발 = new CountDownLatch(1);
        ExecutorService 실행기 = Executors.newFixedThreadPool(동시_요청_수);
        try {
            List<Callable<Boolean>> 작업들 = new ArrayList<>();
            for (int index = 0; index < 동시_요청_수; index++) {
                작업들.add(() -> {
                    준비.countDown();
                    출발.await(5, TimeUnit.SECONDS);
                    try {
                        groupService.join(devicePublicId, inviteCode);

                        return true;
                    } catch (GroupException exception) {
                        return false;
                    }
                });
            }
            List<Future<Boolean>> 미래들 = 작업들.stream().map(실행기::submit).toList();
            준비.await(5, TimeUnit.SECONDS);
            출발.countDown();

            List<Boolean> 성공_여부들 = new ArrayList<>();
            for (Future<Boolean> 미래 : 미래들) {
                성공_여부들.add(미래.get(10, TimeUnit.SECONDS));
            }

            return 성공_여부들;
        } finally {
            실행기.shutdownNow();
        }
    }

    private GroupResult 그룹을_만든다(String name) {
        return groupService.save(기기를_저장한다().getPublicId(), name, null, 스탬프());
    }

    private Device 기기를_저장한다() {
        return deviceRepository.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
    }

    private GroupStampCommand 스탬프() {
        return GroupStampCommand.of("기본", "#FFFFFF", "#4A90D9", StampFrame.CIRCLE);
    }

    private long 멤버_행_수(UUID groupPublicId) {
        return 센다(groupPublicId, "SELECT COUNT(*) FROM group_members WHERE group_id = ?");
    }

    private long 활동_멤버_수(UUID groupPublicId) {
        return 센다(groupPublicId, "SELECT COUNT(*) FROM group_members WHERE group_id = ? AND left_at IS NULL");
    }

    private long 나간_시각이_있는_멤버_수(UUID groupPublicId) {
        return 센다(groupPublicId, "SELECT COUNT(*) FROM group_members WHERE group_id = ? AND left_at IS NOT NULL");
    }

    private long 센다(UUID groupPublicId, String sql) {
        Long groupId = groupRepository.findByPublicIdAndDeletedAtIsNull(groupPublicId)
                .orElseThrow()
                .getId();

        return jdbcClient.sql(sql).param(groupId).query(Long.class).single();
    }

    private void 그룹_오류다(Throwable throwable, GroupErrorCode errorCode) {
        assertThat(throwable).isInstanceOf(GroupException.class);
        assertThat(((GroupException) throwable).getErrorCode()).isEqualTo(errorCode);
    }
}
