package com.pheeeew.groups.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.일반_멤버_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.groups.application.dto.GroupResult;
import com.pheeeew.groups.application.dto.GroupStampCommand;
import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupRole;
import com.pheeeew.groups.domain.StampFrame;
import com.pheeeew.groups.domain.repository.GroupMemberRepository;
import com.pheeeew.groups.domain.repository.GroupRepository;
import com.pheeeew.groups.domain.repository.GroupStampRepository;
import com.pheeeew.groups.exception.GroupErrorCode;
import com.pheeeew.groups.exception.GroupException;
import com.pheeeew.support.PostgisDataJpaTest;
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
class GroupServiceIntegrationTest {

    private static final String 혼동되는_글자 = "ILOU";

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
    void 그룹을_만들면_스탬프와_그룹장이_함께_생긴다() {
        // given
        Device 기기 = 기기를_저장한다();

        // when
        GroupResult 결과 = groupService.save(기기.getPublicId(), "한숨모임", "설명입니다", 스탬프("기본"));

        // then
        assertThat(결과.name()).isEqualTo("한숨모임");
        assertThat(결과.description()).isEqualTo("설명입니다");
        assertThat(결과.role()).isEqualTo(GroupRole.OWNER);
        assertThat(결과.memberCount()).isOne();
        assertThat(결과.stamp().text()).isEqualTo("기본");
        assertThat(결과.stamp().frame()).isEqualTo(StampFrame.RIBBON);
    }

    @Test
    void 초대_코드는_혼동되는_글자를_뺀_여섯_자리다() {
        // given
        Device 기기 = 기기를_저장한다();

        // when
        String 초대_코드 = groupService.save(기기.getPublicId(), "한숨모임", null, 스탬프("기본")).inviteCode();

        // then
        assertThat(초대_코드).hasSize(6);
        assertThat(초대_코드).matches("[0-9A-Z]{6}");
        assertThat(초대_코드).doesNotContainAnyWhitespaces();
        for (char 글자 : 혼동되는_글자.toCharArray()) {
            assertThat(초대_코드).doesNotContain(String.valueOf(글자));
        }
    }

    @Test
    void 그룹_이름의_앞뒤_공백을_잘라낸다() {
        // given
        Device 기기 = 기기를_저장한다();

        // when
        GroupResult 결과 = groupService.save(기기.getPublicId(), "  한숨모임  ", null, 스탬프("기본"));

        // then
        assertThat(결과.name()).isEqualTo("한숨모임");
    }

    @Test
    void 같은_이름의_그룹은_두_번_만들_수_없다() {
        // given
        groupService.save(기기를_저장한다().getPublicId(), "한숨모임", null, 스탬프("기본"));

        // when
        Throwable throwable = catchThrowable(
                () -> groupService.save(기기를_저장한다().getPublicId(), "한숨모임", null, 스탬프("기본"))
        );

        // then
        그룹_오류다(throwable, GroupErrorCode.GROUP_NAME_DUPLICATED);
    }

    @Test
    void 삭제한_그룹의_이름은_다시_쓸_수_있다() {
        // given
        Device 기기 = 기기를_저장한다();
        GroupResult 먼저_만든_그룹 = groupService.save(기기.getPublicId(), "한숨모임", null, 스탬프("기본"));
        groupService.delete(먼저_만든_그룹.publicId(), 기기.getPublicId());

        // when
        GroupResult 다시_만든_그룹 = groupService.save(기기를_저장한다().getPublicId(), "한숨모임", null, 스탬프("기본"));

        // then
        assertThat(다시_만든_그룹.name()).isEqualTo("한숨모임");
        assertThat(다시_만든_그룹.publicId()).isNotEqualTo(먼저_만든_그룹.publicId());
    }

    @Test
    void 내_그룹_목록에는_속한_그룹만_나온다() {
        // given
        Device 나 = 기기를_저장한다();
        groupService.save(나.getPublicId(), "내모임", null, 스탬프("기본"));
        groupService.save(기기를_저장한다().getPublicId(), "남의모임", null, 스탬프("기본"));

        // when
        List<GroupResult> 목록 = groupService.findMine(나.getPublicId());

        // then
        assertThat(목록).hasSize(1);
        assertThat(목록.getFirst().name()).isEqualTo("내모임");
    }

    @Test
    void 나간_그룹과_삭제한_그룹은_목록에서_빠진다() {
        // given
        Device 나 = 기기를_저장한다();
        GroupResult 나갈_그룹 = groupService.save(나.getPublicId(), "나갈모임", null, 스탬프("기본"));
        GroupResult 삭제할_그룹 = groupService.save(나.getPublicId(), "삭제할모임", null, 스탬프("기본"));
        나간다(나갈_그룹.publicId(), 나.getId());
        groupService.delete(삭제할_그룹.publicId(), 나.getPublicId());

        // then
        assertThat(groupService.findMine(나.getPublicId())).isEmpty();
    }

    @Test
    void 속하지_않은_그룹은_상세_조회에서_404다() {
        // given
        GroupResult 남의_그룹 = groupService.save(기기를_저장한다().getPublicId(), "남의모임", null, 스탬프("기본"));
        Device 남 = 기기를_저장한다();

        // when
        Throwable throwable = catchThrowable(() -> groupService.findOne(남의_그룹.publicId(), 남.getPublicId()));

        // then
        그룹_오류다(throwable, GroupErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void 그룹장이_아니면_변경할_수_없다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        Device 멤버 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));
        멤버로_넣는다(그룹.publicId(), 멤버);

        // when
        Throwable throwable = catchThrowable(() -> groupService.update(
                그룹.publicId(), 멤버.getPublicId(), "바꾼이름", null, 스탬프("변경")
        ));

        // then
        그룹_오류다(throwable, GroupErrorCode.GROUP_OWNER_ONLY);
    }

    @Test
    void 이름과_설명과_스탬프를_함께_바꾼다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));

        // when
        GroupResult 결과 = groupService.update(
                그룹.publicId(), 그룹장.getPublicId(), "바뀐모임", "새 설명", 스탬프("변경")
        );

        // then
        assertThat(결과.name()).isEqualTo("바뀐모임");
        assertThat(결과.description()).isEqualTo("새 설명");
        assertThat(결과.stamp().text()).isEqualTo("변경");
        assertThat(결과.stamp().frame()).isEqualTo(StampFrame.RIBBON);
    }

    @Test
    void 이름을_그대로_두고_바꾸면_중복으로_걸리지_않는다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));

        // when
        GroupResult 결과 = groupService.update(
                그룹.publicId(), 그룹장.getPublicId(), "한숨모임", "설명만 바꿈", 스탬프("그대로")
        );

        // then
        assertThat(결과.name()).isEqualTo("한숨모임");
        assertThat(결과.description()).isEqualTo("설명만 바꿈");
    }

    @Test
    void 남이_쓰는_이름으로는_바꿀_수_없다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "내모임", null, 스탬프("기본"));
        groupService.save(기기를_저장한다().getPublicId(), "남의모임", null, 스탬프("기본"));

        // when
        Throwable throwable = catchThrowable(() -> groupService.update(
                그룹.publicId(), 그룹장.getPublicId(), "남의모임", null, 스탬프("변경")
        ));

        // then
        그룹_오류다(throwable, GroupErrorCode.GROUP_NAME_DUPLICATED);
    }

    @Test
    void 초대_코드를_재발급하면_이전_코드는_무효가_된다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));
        String 이전_코드 = 그룹.inviteCode();

        // when
        String 새_코드 = groupService.reissueInviteCode(그룹.publicId(), 그룹장.getPublicId()).inviteCode();

        // then
        assertThat(새_코드).isNotEqualTo(이전_코드);
        assertThat(새_코드).hasSize(6);
        assertThat(groupRepository.findAll())
                .noneMatch(저장된_그룹 -> 저장된_그룹.getInviteCode().equals(이전_코드));
    }

    @Test
    void 다른_멤버가_남아_있으면_그룹을_삭제할_수_없다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));
        멤버로_넣는다(그룹.publicId(), 기기를_저장한다());

        // when
        Throwable throwable = catchThrowable(() -> groupService.delete(그룹.publicId(), 그룹장.getPublicId()));

        // then
        그룹_오류다(throwable, GroupErrorCode.GROUP_MEMBER_REMAINS);
    }

    @Test
    void 그룹장_혼자_남으면_그룹을_삭제할_수_있다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        Device 멤버 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));
        멤버로_넣는다(그룹.publicId(), 멤버);
        나간다(그룹.publicId(), 멤버.getId());

        // when
        groupService.delete(그룹.publicId(), 그룹장.getPublicId());

        // then
        assertThat(groupRepository.findByPublicIdAndDeletedAtIsNull(그룹.publicId())).isEmpty();
        assertThat(groupRepository.findAll()).hasSize(1);
    }

    private Device 기기를_저장한다() {
        return deviceRepository.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
    }

    private GroupStampCommand 스탬프(String text) {
        return GroupStampCommand.of(text, "#000000", "#FFFFFFAA", StampFrame.RIBBON);
    }

    private void 멤버로_넣는다(UUID groupPublicId, Device device) {
        Group group = groupRepository.findByPublicIdAndDeletedAtIsNull(groupPublicId).orElseThrow();
        groupMemberRepository.saveAndFlush(일반_멤버_빌더(group, device).build());
    }

    private void 나간다(UUID groupPublicId, Long deviceId) {
        Group group = groupRepository.findByPublicIdAndDeletedAtIsNull(groupPublicId).orElseThrow();
        jdbcClient.sql("UPDATE group_members SET left_at = NOW() WHERE group_id = ? AND device_id = ?")
                .param(group.getId())
                .param(deviceId)
                .update();
    }

    private void 그룹_오류다(Throwable throwable, GroupErrorCode errorCode) {
        assertThat(throwable).isInstanceOf(GroupException.class);
        assertThat(((GroupException) throwable).getErrorCode()).isEqualTo(errorCode);
    }
}
