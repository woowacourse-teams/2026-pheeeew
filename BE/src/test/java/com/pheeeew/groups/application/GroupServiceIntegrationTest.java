package com.pheeeew.groups.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.일반_멤버_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.groups.application.dto.GroupDetailResult;
import com.pheeeew.groups.application.dto.GroupPressCountResult;
import com.pheeeew.groups.application.dto.GroupResult;
import com.pheeeew.groups.application.dto.GroupStampCommand;
import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupRole;
import com.pheeeew.groups.domain.StampFrame;
import com.pheeeew.groups.domain.repository.GroupDailyPressRepository;
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
class GroupServiceIntegrationTest {

    private static final String 혼동되는_글자 = "ILOU";
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
    private GroupDailyPressRepository groupDailyPressRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @AfterEach
    void tearDown() {
        groupDailyPressRepository.deleteAllInBatch();
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
        assertThat(결과.stamp().frame()).isEqualTo(StampFrame.VOUCHER);
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
    void 같은_이름으로_동시에_만들면_하나만_성공한다() throws Exception {
        // when
        List<Boolean> 성공_여부들 = 동시에_실행한다(() -> {
            groupService.save(기기를_저장한다().getPublicId(), "한숨모임", null, 스탬프("기본"));

            return true;
        });

        // then
        assertThat(성공_여부들).filteredOn(성공 -> 성공).hasSize(1);
        assertThat(groupRepository.findAll()).hasSize(1);
    }

    @Test
    void 같은_이름으로_동시에_바꾸면_하나만_성공한다() throws Exception {
        // given
        Device 첫_그룹장 = 기기를_저장한다();
        Device 둘째_그룹장 = 기기를_저장한다();
        GroupResult 첫_그룹 = groupService.save(첫_그룹장.getPublicId(), "첫모임", null, 스탬프("기본"));
        GroupResult 둘째_그룹 = groupService.save(둘째_그룹장.getPublicId(), "둘째모임", null, 스탬프("기본"));

        // when
        List<Boolean> 성공_여부들 = 동시에_바꾼다(
                () -> groupService.update(첫_그룹.publicId(), 첫_그룹장.getPublicId(), "같은이름", null, 스탬프("기본")),
                () -> groupService.update(둘째_그룹.publicId(), 둘째_그룹장.getPublicId(), "같은이름", null, 스탬프("기본"))
        );

        // then
        assertThat(성공_여부들).filteredOn(성공 -> 성공).hasSize(1);
        assertThat(groupRepository.findAll())
                .filteredOn(그룹 -> 그룹.getName().equals("같은이름"))
                .hasSize(1);
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
    void 버튼을_누르면_오늘_집계가_올라간다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));

        // when
        groupService.press(그룹.publicId(), 그룹장.getPublicId(), EmotionState.ANGRY);
        GroupPressCountResult 결과 = groupService.press(그룹.publicId(), 그룹장.getPublicId(), EmotionState.ANGRY);

        // then
        assertThat(결과.counts().get(EmotionState.ANGRY)).isEqualTo(2);
        assertThat(결과.total()).isEqualTo(2);
    }

    @Test
    void 아무리_연타해도_감정마다_행이_하나다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));

        // when
        for (int 회차 = 0; 회차 < 50; 회차++) {
            groupService.press(그룹.publicId(), 그룹장.getPublicId(), EmotionState.ANGRY);
        }
        GroupPressCountResult 결과 =
                groupService.press(그룹.publicId(), 그룹장.getPublicId(), EmotionState.IRRITATED);

        // then
        assertThat(결과.counts().get(EmotionState.ANGRY)).isEqualTo(50);
        assertThat(groupDailyPressRepository.count()).isEqualTo(2);
    }

    @Test
    void 여러_멤버가_눌러도_한_그룹의_같은_감정은_한_행에_쌓인다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));
        Device 멤버 = 기기를_저장한다();
        groupService.join(멤버.getPublicId(), 그룹.inviteCode());

        // when
        groupService.press(그룹.publicId(), 그룹장.getPublicId(), EmotionState.ANGRY);
        GroupPressCountResult 결과 =
                groupService.press(그룹.publicId(), 멤버.getPublicId(), EmotionState.ANGRY);

        // then
        assertThat(결과.counts().get(EmotionState.ANGRY)).isEqualTo(2);
        assertThat(groupDailyPressRepository.count()).isOne();
    }

    @Test
    void 그룹이_다르면_집계가_섞이지_않는다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 내_그룹 = groupService.save(그룹장.getPublicId(), "내모임", null, 스탬프("기본"));
        GroupResult 옆_그룹 = groupService.save(그룹장.getPublicId(), "옆모임", null, 스탬프("기본"));
        groupService.press(옆_그룹.publicId(), 그룹장.getPublicId(), EmotionState.ANGRY);

        // when
        GroupPressCountResult 결과 =
                groupService.press(내_그룹.publicId(), 그룹장.getPublicId(), EmotionState.ANGRY);

        // then
        assertThat(결과.total()).isOne();
    }

    @Test
    void 누르지_않은_감정도_영으로_내려준다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));

        // when
        GroupPressCountResult 결과 =
                groupService.press(그룹.publicId(), 그룹장.getPublicId(), EmotionState.ANGRY);

        // then
        assertThat(결과.counts()).hasSize(EmotionState.values().length);
        assertThat(결과.counts().get(EmotionState.EXHAUSTED)).isZero();
    }

    @Test
    void 날이_바뀌면_어제_집계는_안_보이고_영부터_시작한다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));
        groupService.press(그룹.publicId(), 그룹장.getPublicId(), EmotionState.ANGRY);
        어제로_넘긴다(그룹.publicId());

        // when
        GroupDetailResult 상세 = groupService.findOne(그룹.publicId(), 그룹장.getPublicId());

        // then
        assertThat(상세.todayPresses().total()).isZero();
        assertThat(상세.todayPresses().counts()).hasSize(EmotionState.values().length);
        assertThat(groupDailyPressRepository.count()).isOne();
    }

    @Test
    void 멤버가_아니면_버튼을_누를_수_없다() {
        // given
        GroupResult 그룹 = groupService.save(
                기기를_저장한다().getPublicId(), "한숨모임", null, 스탬프("기본")
        );
        Device 남 = 기기를_저장한다();

        // when
        Throwable throwable = catchThrowable(
                () -> groupService.press(그룹.publicId(), 남.getPublicId(), EmotionState.ANGRY)
        );

        // then
        그룹_오류다(throwable, GroupErrorCode.GROUP_MEMBER_ONLY);
    }

    @Test
    void 누른_횟수는_이번_주_점수와_순위에_영향을_주지_않는다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));
        groupService.press(그룹.publicId(), 그룹장.getPublicId(), EmotionState.ANGRY);
        groupService.press(그룹.publicId(), 그룹장.getPublicId(), EmotionState.IRRITATED);

        // when
        GroupDetailResult 상세 = groupService.findOne(그룹.publicId(), 그룹장.getPublicId());

        // then
        assertThat(상세.todayPresses().total()).isEqualTo(2);
        assertThat(상세.weeklyScore()).isZero();
        assertThat(상세.weeklyRank()).isNull();
    }

    @Test
    void 상세_조회는_그룹_정보와_오늘_집계를_함께_준다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", "설명", 스탬프("기본"));

        // when
        GroupDetailResult 상세 = groupService.findOne(그룹.publicId(), 그룹장.getPublicId());

        // then
        assertThat(상세.group().name()).isEqualTo("한숨모임");
        assertThat(상세.group().memberCount()).isOne();
        assertThat(상세.todayPresses().total()).isZero();
    }

    @Test
    void 속하지_않은_그룹은_상세_조회에서_403이다() {
        // given
        GroupResult 남의_그룹 = groupService.save(기기를_저장한다().getPublicId(), "남의모임", null, 스탬프("기본"));
        Device 남 = 기기를_저장한다();

        // when
        Throwable throwable = catchThrowable(() -> groupService.findOne(남의_그룹.publicId(), 남.getPublicId()));

        // then
        그룹_오류다(throwable, GroupErrorCode.GROUP_MEMBER_ONLY);
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
        assertThat(결과.stamp().frame()).isEqualTo(StampFrame.VOUCHER);
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

    @SafeVarargs
    private List<Boolean> 동시에_바꾼다(Callable<GroupResult>... 작업들) throws Exception {
        return 실행한다(List.of(작업들).stream()
                .map(작업 -> (Callable<Boolean>) () -> {
                    작업.call();

                    return true;
                })
                .toList());
    }

    private List<Boolean> 동시에_실행한다(Callable<Boolean> 작업) throws Exception {
        List<Callable<Boolean>> 작업들 = new ArrayList<>();
        for (int index = 0; index < 동시_요청_수; index++) {
            작업들.add(작업);
        }

        return 실행한다(작업들);
    }

    private List<Boolean> 실행한다(List<Callable<Boolean>> 작업들) throws Exception {
        CountDownLatch 준비 = new CountDownLatch(작업들.size());
        CountDownLatch 출발 = new CountDownLatch(1);
        ExecutorService 실행기 = Executors.newFixedThreadPool(작업들.size());
        try {
            List<Future<Boolean>> 미래들 = 작업들.stream()
                    .map(작업 -> 실행기.submit(() -> {
                        준비.countDown();
                        출발.await(5, TimeUnit.SECONDS);
                        try {
                            return 작업.call();
                        } catch (GroupException exception) {
                            return false;
                        }
                    }))
                    .toList();
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

    private Device 기기를_저장한다() {
        return deviceRepository.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
    }

    private GroupStampCommand 스탬프(String text) {
        return GroupStampCommand.of(text, "#000000", "#FFFFFFAA", StampFrame.VOUCHER);
    }

    private void 멤버로_넣는다(UUID groupPublicId, Device device) {
        Group group = groupRepository.findByPublicIdAndDeletedAtIsNull(groupPublicId).orElseThrow();
        groupMemberRepository.saveAndFlush(일반_멤버_빌더(group, device).build());
    }

    private void 어제로_넘긴다(UUID 그룹_공개_식별자) {
        jdbcClient.sql("""
                        UPDATE group_daily_presses SET press_date = press_date - 1
                         WHERE group_id = (SELECT id FROM groups WHERE public_id = ?)
                        """)
                .param(그룹_공개_식별자)
                .update();
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
