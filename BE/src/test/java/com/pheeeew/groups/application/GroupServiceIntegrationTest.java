package com.pheeeew.groups.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.일반_멤버_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.groups.application.dto.GroupDetailResult;
import com.pheeeew.groups.application.dto.GroupPressCommand;
import com.pheeeew.groups.application.dto.GroupPressCountResult;
import com.pheeeew.groups.application.dto.GroupResult;
import com.pheeeew.groups.application.dto.GroupStampCommand;
import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupRole;
import com.pheeeew.groups.domain.GroupViewerRole;
import com.pheeeew.groups.domain.StampFrame;
import com.pheeeew.groups.domain.repository.GroupDailyPressRepository;
import com.pheeeew.groups.domain.repository.GroupMemberRepository;
import com.pheeeew.groups.domain.repository.GroupRepository;
import com.pheeeew.groups.domain.repository.GroupStampRepository;
import com.pheeeew.groups.exception.GroupErrorCode;
import com.pheeeew.groups.exception.GroupException;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GroupServiceIntegrationTest {

    private static final String 혼동되는_글자 = "ILOU";
    private static final int 동시_요청_수 = 6;
    private static final int 데드락_회전_수 = 30;
    private static final int 정합성_회전_수 = 10;
    private static final String 자른_양_지표 = "pheeeew.group.press.clamped";

    @MockitoBean
    private Clock clock;

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
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(Instant.parse("2026-10-07T03:00:00Z"));
    }

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
    void 목록에_그룹별_전체_정보와_내_역할_및_활성_회원_수를_반환한다() {
        // given
        Device 나 = 기기를_저장한다();
        Device 다른_기기 = 기기를_저장한다();
        Device 세번째_기기 = 기기를_저장한다();
        GroupResult 내_그룹 = groupService.save(나.getPublicId(), "내모임", "내 설명", 스탬프("내것"));
        멤버로_넣는다(내_그룹.publicId(), 다른_기기);
        멤버로_넣는다(내_그룹.publicId(), 세번째_기기);
        나간다(내_그룹.publicId(), 세번째_기기.getId());
        GroupResult 참여한_그룹 = groupService.save(다른_기기.getPublicId(), "참여모임", null, 스탬프("참여"));
        멤버로_넣는다(참여한_그룹.publicId(), 나);
        멤버로_넣는다(참여한_그룹.publicId(), 세번째_기기);

        // when
        List<GroupResult> 목록 = groupService.findMine(나.getPublicId());

        // then
        assertThat(목록).extracting(GroupResult::publicId, GroupResult::name, GroupResult::description,
                        GroupResult::inviteCode, GroupResult::role, GroupResult::memberCount, GroupResult::stamp)
                .containsExactlyInAnyOrder(
                        tuple(내_그룹.publicId(), "내모임", "내 설명", 내_그룹.inviteCode(),
                                GroupRole.OWNER, 2L, 내_그룹.stamp()),
                        tuple(참여한_그룹.publicId(), "참여모임", null, 참여한_그룹.inviteCode(),
                                GroupRole.MEMBER, 3L, 참여한_그룹.stamp())
                );
    }

    @Test
    void 그룹_생성_순서가_아닌_가입_시각_순서로_목록을_반환한다() {
        // given
        Device 나 = 기기를_저장한다();
        GroupResult 늦게_가입한_그룹 = groupService.save(나.getPublicId(), "늦은가입", null, 스탬프("기본"));
        GroupResult 먼저_가입한_그룹 = groupService.save(나.getPublicId(), "먼저가입", null, 스탬프("기본"));
        jdbcClient.sql("""
                        UPDATE group_members
                        SET created_at = CASE WHEN group_id = (SELECT id FROM groups WHERE public_id = ?)
                            THEN TIMESTAMPTZ '2026-09-28 01:00:00+00'
                            ELSE TIMESTAMPTZ '2026-09-28 00:00:00+00' END
                        WHERE device_id = ?
                        """)
                .param(늦게_가입한_그룹.publicId())
                .param(나.getId())
                .update();

        // when
        List<GroupResult> 목록 = groupService.findMine(나.getPublicId());

        // then
        assertThat(목록).extracting(GroupResult::publicId)
                .containsExactly(먼저_가입한_그룹.publicId(), 늦게_가입한_그룹.publicId());
    }

    @Test
    void 등록되지_않은_기기는_내_그룹_목록을_조회할_수_없다() {
        // given
        UUID 등록되지_않은_기기 = UUID.randomUUID();

        // when
        Throwable 예외 = catchThrowable(() -> groupService.findMine(등록되지_않은_기기));

        // then
        assertThat(예외).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) 예외).getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND);
    }

    @Test
    void 스탬프가_누락된_그룹은_목록에서_제외하지_않고_기존_오류를_반환한다() {
        // given
        Device 나 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(나.getPublicId(), "내모임", null, 스탬프("기본"));
        jdbcClient.sql("DELETE FROM group_stamps WHERE group_id = (SELECT id FROM groups WHERE public_id = ?)")
                .param(그룹.publicId())
                .update();

        // when
        Throwable 예외 = catchThrowable(() -> groupService.findMine(나.getPublicId()));

        // then
        그룹_오류다(예외, GroupErrorCode.GROUP_NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5, 25})
    void 그룹_수와_관계없이_두_번의_SQL로_전체_목록을_조회한다(int 그룹_수) {
        // given
        Device 나 = 기기를_저장한다();
        for (int index = 0; index < 그룹_수; index++) {
            groupService.save(나.getPublicId(), "모임" + index, null, 스탬프("기본"));
        }
        Statistics 통계 = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean 기존_통계_설정 = 통계.isStatisticsEnabled();
        통계.setStatisticsEnabled(true);
        통계.clear();
        try {
            // when
            List<GroupResult> 목록 = groupService.findMine(나.getPublicId());
            long SQL_횟수 = 통계.getPrepareStatementCount();

            // then
            assertThat(목록).hasSize(그룹_수);
            assertThat(SQL_횟수).isEqualTo(2);
        } finally {
            통계.clear();
            통계.setStatisticsEnabled(기존_통계_설정);
        }
    }

    @Test
    void 버튼을_누르면_오늘_집계가_올라간다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));

        // when
        groupService.press(그룹.publicId(), 그룹장.getPublicId(), 한_번(EmotionState.ANGRY));
        GroupPressCountResult 결과 = groupService.press(그룹.publicId(), 그룹장.getPublicId(), 한_번(EmotionState.ANGRY));

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
            groupService.press(그룹.publicId(), 그룹장.getPublicId(), 한_번(EmotionState.ANGRY));
        }
        GroupPressCountResult 결과 =
                groupService.press(그룹.publicId(), 그룹장.getPublicId(), 한_번(EmotionState.IRRITATED));

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
        groupService.press(그룹.publicId(), 그룹장.getPublicId(), 한_번(EmotionState.ANGRY));
        GroupPressCountResult 결과 =
                groupService.press(그룹.publicId(), 멤버.getPublicId(), 한_번(EmotionState.ANGRY));

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
        groupService.press(옆_그룹.publicId(), 그룹장.getPublicId(), 한_번(EmotionState.ANGRY));

        // when
        GroupPressCountResult 결과 =
                groupService.press(내_그룹.publicId(), 그룹장.getPublicId(), 한_번(EmotionState.ANGRY));

        // then
        assertThat(결과.total()).isOne();
    }

    @Test
    void 주간_집계는_그_주의_모든_날을_합산한다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));
        LocalDate 월요일 = 이번_주_월요일();
        눌린_것으로_둔다(그룹.publicId(), 월요일, EmotionState.ANGRY, 3);
        눌린_것으로_둔다(그룹.publicId(), 월요일.plusDays(3), EmotionState.ANGRY, 2);
        눌린_것으로_둔다(그룹.publicId(), 월요일.plusDays(6), EmotionState.EXHAUSTED, 4);

        // when
        GroupPressCountResult 결과 = groupService.findWeeklyPresses(그룹.publicId(), 그룹장.getPublicId(), 0);

        // then
        assertThat(결과.counts().get(EmotionState.ANGRY)).isEqualTo(5);
        assertThat(결과.counts().get(EmotionState.EXHAUSTED)).isEqualTo(4);
        assertThat(결과.total()).isEqualTo(9);
    }

    @Test
    void 주간_집계는_주_경계_밖의_기록을_세지_않는다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));
        LocalDate 월요일 = 이번_주_월요일();
        눌린_것으로_둔다(그룹.publicId(), 월요일.minusDays(1), EmotionState.ANGRY, 7);
        눌린_것으로_둔다(그룹.publicId(), 월요일.plusDays(7), EmotionState.ANGRY, 9);
        눌린_것으로_둔다(그룹.publicId(), 월요일, EmotionState.ANGRY, 1);

        // when
        GroupPressCountResult 결과 = groupService.findWeeklyPresses(그룹.publicId(), 그룹장.getPublicId(), 0);

        // then
        assertThat(결과.counts().get(EmotionState.ANGRY)).isEqualTo(1);
        assertThat(결과.total()).isEqualTo(1);
    }

    @Test
    void 주간_집계는_지난주도_조회할_수_있다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));
        LocalDate 지난주_월요일 = 이번_주_월요일().minusWeeks(1);
        눌린_것으로_둔다(그룹.publicId(), 지난주_월요일.plusDays(2), EmotionState.IRRITATED, 6);

        // when
        GroupPressCountResult 이번주 = groupService.findWeeklyPresses(그룹.publicId(), 그룹장.getPublicId(), 0);
        GroupPressCountResult 지난주 = groupService.findWeeklyPresses(그룹.publicId(), 그룹장.getPublicId(), 1);

        // then
        assertThat(이번주.total()).isZero();
        assertThat(지난주.counts().get(EmotionState.IRRITATED)).isEqualTo(6);
        assertThat(지난주.total()).isEqualTo(6);
    }

    @Test
    void 주간_집계도_누르지_않은_감정을_영으로_내려준다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));

        // when
        GroupPressCountResult 결과 = groupService.findWeeklyPresses(그룹.publicId(), 그룹장.getPublicId(), 0);

        // then
        assertThat(결과.counts()).hasSize(EmotionState.values().length);
        assertThat(결과.counts().values()).allMatch(count -> count == 0L);
        assertThat(결과.total()).isZero();
    }

    @Test
    void 주간_집계는_멤버가_아니면_볼_수_없다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        Device 남 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));

        // when
        Throwable 예외 = catchThrowable(() -> groupService.findWeeklyPresses(그룹.publicId(), 남.getPublicId(), 0));

        // then
        assertThat(예외).isInstanceOf(GroupException.class);
    }

    @Test
    void 누르지_않은_감정도_영으로_내려준다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));

        // when
        GroupPressCountResult 결과 =
                groupService.press(그룹.publicId(), 그룹장.getPublicId(), 한_번(EmotionState.ANGRY));

        // then
        assertThat(결과.counts()).hasSize(EmotionState.values().length);
        assertThat(결과.counts().get(EmotionState.EXHAUSTED)).isZero();
    }

    @Test
    void 날이_바뀌면_어제_집계는_안_보이고_영부터_시작한다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));
        groupService.press(그룹.publicId(), 그룹장.getPublicId(), 한_번(EmotionState.ANGRY));
        어제로_넘긴다(그룹.publicId());

        // when
        GroupPressCountResult 집계 = 오늘_집계(그룹, 그룹장);

        // then
        assertThat(집계.total()).isZero();
        assertThat(집계.counts()).hasSize(EmotionState.values().length);
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
                () -> groupService.press(그룹.publicId(), 남.getPublicId(), 한_번(EmotionState.ANGRY))
        );

        // then
        그룹_오류다(throwable, GroupErrorCode.GROUP_MEMBER_ONLY);
        assertThat(groupDailyPressRepository.count()).isZero();
    }

    @Test
    void 멤버가_아닌_요청도_형식_지표에는_먼저_집계된다() {
        // given
        GroupResult 그룹 = groupService.save(
                기기를_저장한다().getPublicId(), "한숨모임", null, 스탬프("기본")
        );
        Device 남 = 기기를_저장한다();
        double 기존_묶음_요청_수 = 형식_요청_수("counts");

        // when
        catchThrowable(() -> groupService.press(
                그룹.publicId(), 남.getPublicId(), 묶음(Map.of(EmotionState.ANGRY, 1))
        ));

        // then
        assertThat(형식_요청_수("counts") - 기존_묶음_요청_수).isEqualTo(1);
    }

    @Test
    void 묶음으로_보내면_감정마다_행이_하나씩_생기고_각각_그만큼_오른다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));

        // when
        GroupPressCountResult 결과 = groupService.press(
                그룹.publicId(),
                그룹장.getPublicId(),
                묶음(순서대로(Map.entry(EmotionState.ANGRY, 9), Map.entry(EmotionState.EXHAUSTED, 3)))
        );

        // then
        assertThat(결과.counts().get(EmotionState.ANGRY)).isEqualTo(9);
        assertThat(결과.counts().get(EmotionState.EXHAUSTED)).isEqualTo(3);
        assertThat(결과.total()).isEqualTo(12);
        assertThat(groupDailyPressRepository.count()).isEqualTo(2);
    }

    @Test
    void 구버전_단일_감정_형식과_묶음_형식은_같은_행에_함께_쌓인다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));

        // when
        GroupPressCountResult 구버전_뒤 =
                groupService.press(그룹.publicId(), 그룹장.getPublicId(), 한_번(EmotionState.ANGRY));
        GroupPressCountResult 묶음_뒤 = groupService.press(
                그룹.publicId(), 그룹장.getPublicId(), 묶음(Map.of(EmotionState.ANGRY, 4))
        );

        // then
        assertThat(구버전_뒤.counts().get(EmotionState.ANGRY)).isOne();
        assertThat(묶음_뒤.counts().get(EmotionState.ANGRY)).isEqualTo(5);
        assertThat(groupDailyPressRepository.count()).isOne();
    }

    @Test
    void 감정별_상한을_넘기면_서른만_반영되고_자른_양이_지표에_오른다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));
        double 기존_자른_양 = 자른_양("per_state");
        double 기존_적용_합 = 적용된_합();

        // when
        GroupPressCountResult 결과 = groupService.press(
                그룹.publicId(), 그룹장.getPublicId(), 묶음(Map.of(EmotionState.ANGRY, 45))
        );

        // then
        assertThat(결과.counts().get(EmotionState.ANGRY)).isEqualTo(30);
        assertThat(결과.total()).isEqualTo(30);
        assertThat(자른_양("per_state") - 기존_자른_양).isEqualTo(15);
        assertThat(적용된_합() - 기존_적용_합).isEqualTo(30);
    }

    @Test
    void 전체_합_상한을_넘기면_백만_반영되고_이름이_뒤인_감정은_행이_생기지_않는다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));
        double 기존_자른_양 = 자른_양("total");

        // when
        GroupPressCountResult 결과 = groupService.press(
                그룹.publicId(),
                그룹장.getPublicId(),
                묶음(순서대로(
                        Map.entry(EmotionState.IRRITATED, 30),
                        Map.entry(EmotionState.FRUSTRATED, 30),
                        Map.entry(EmotionState.EXHAUSTED, 30),
                        Map.entry(EmotionState.DISCOURAGED, 30),
                        Map.entry(EmotionState.ANGRY, 30)
                ))
        );

        // then
        assertThat(결과.total()).isEqualTo(100);
        assertThat(결과.counts().get(EmotionState.FRUSTRATED)).isEqualTo(10);
        assertThat(결과.counts().get(EmotionState.IRRITATED)).isZero();
        assertThat(groupDailyPressRepository.count()).isEqualTo(4);
        assertThat(자른_양("total") - 기존_자른_양).isEqualTo(50);
    }

    @Test
    void 값이_영인_감정만_보내면_집계가_그대로다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));
        groupService.press(그룹.publicId(), 그룹장.getPublicId(), 묶음(Map.of(EmotionState.ANGRY, 2)));

        // when
        GroupPressCountResult 결과 = groupService.press(
                그룹.publicId(), 그룹장.getPublicId(), 묶음(Map.of(EmotionState.ANGRY, 0))
        );

        // then
        assertThat(결과.counts().get(EmotionState.ANGRY)).isEqualTo(2);
        assertThat(결과.total()).isEqualTo(2);
        assertThat(groupDailyPressRepository.count()).isOne();
    }

    @Test
    void 빈_묶음을_보내면_행을_만들지_않고_집계를_그대로_돌려준다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));

        // when
        GroupPressCountResult 결과 =
                groupService.press(그룹.publicId(), 그룹장.getPublicId(), 묶음(Map.of()));

        // then
        assertThat(결과.total()).isZero();
        assertThat(결과.counts()).hasSize(EmotionState.values().length);
        assertThat(groupDailyPressRepository.count()).isZero();
    }

    @Test
    void 서로_반대_순서의_묶음을_동시에_보내도_데드락_없이_정확히_합산된다() throws Exception {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));
        List<Callable<Boolean>> 작업들 = new ArrayList<>();
        for (int 번호 = 0; 번호 < 동시_요청_수; 번호++) {
            Map<EmotionState, Integer> 반대_순서 = 번호 % 2 == 0
                    ? 순서대로(Map.entry(EmotionState.ANGRY, 1), Map.entry(EmotionState.EXHAUSTED, 1))
                    : 순서대로(Map.entry(EmotionState.EXHAUSTED, 1), Map.entry(EmotionState.ANGRY, 1));
            작업들.add(() -> {
                for (int 회전 = 0; 회전 < 데드락_회전_수; 회전++) {
                    groupService.press(그룹.publicId(), 그룹장.getPublicId(), 묶음(반대_순서));
                }

                return true;
            });
        }

        // when
        List<Boolean> 성공_여부들 = 실행한다(작업들);

        // then
        assertThat(성공_여부들).containsOnly(true);
        GroupPressCountResult 집계 = 오늘_집계(그룹, 그룹장);
        assertThat(집계.counts().get(EmotionState.ANGRY)).isEqualTo((long) 동시_요청_수 * 데드락_회전_수);
        assertThat(집계.counts().get(EmotionState.EXHAUSTED)).isEqualTo((long) 동시_요청_수 * 데드락_회전_수);
        assertThat(집계.total()).isEqualTo(2L * 동시_요청_수 * 데드락_회전_수);
    }

    @Test
    void 동시에_눌러도_성공한_요청의_합만_정확히_집계된다() throws Exception {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));
        List<Device> 기기들 = new ArrayList<>();
        for (int 번호 = 0; 번호 < 동시_요청_수; 번호++) {
            Device 기기 = 기기를_저장한다();
            if (번호 % 2 == 0) {
                groupService.join(기기.getPublicId(), 그룹.inviteCode());
            }
            기기들.add(기기);
        }
        List<Callable<Boolean>> 작업들 = 기기들.stream()
                .map(기기 -> (Callable<Boolean>) () -> {
                    for (int 회전 = 0; 회전 < 정합성_회전_수; 회전++) {
                        groupService.press(
                                그룹.publicId(), 기기.getPublicId(), 묶음(Map.of(EmotionState.ANGRY, 3))
                        );
                    }

                    return true;
                })
                .toList();

        // when
        List<Boolean> 성공_여부들 = 실행한다(작업들);

        // then
        long 성공한_기기_수 = 성공_여부들.stream().filter(Boolean::booleanValue).count();
        assertThat(성공한_기기_수).isEqualTo(동시_요청_수 / 2);
        assertThat(오늘_집계(그룹, 그룹장).total()).isEqualTo(성공한_기기_수 * 정합성_회전_수 * 3);
        assertThat(groupDailyPressRepository.count()).isOne();
    }

    @Test
    void 누른_횟수는_이번_주_점수와_순위에_영향을_주지_않는다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));
        groupService.press(그룹.publicId(), 그룹장.getPublicId(), 한_번(EmotionState.ANGRY));
        GroupPressCountResult 누른_결과 =
                groupService.press(그룹.publicId(), 그룹장.getPublicId(), 한_번(EmotionState.IRRITATED));

        // when
        GroupDetailResult 상세 = groupService.findOne(그룹.publicId(), 그룹장.getPublicId());

        // then
        assertThat(누른_결과.total()).isEqualTo(2);
        assertThat(상세.weeklyScore()).isZero();
        assertThat(상세.weeklyRank()).isNull();
    }

    @Test
    void 상세_조회는_그룹_정보와_인원수를_함께_준다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", "설명", 스탬프("기본"));
        멤버로_넣는다(그룹.publicId(), 기기를_저장한다());

        // when
        GroupDetailResult 상세 = groupService.findOne(그룹.publicId(), 그룹장.getPublicId());

        // then
        assertThat(상세.publicId()).isEqualTo(그룹.publicId());
        assertThat(상세.name()).isEqualTo("한숨모임");
        assertThat(상세.description()).isEqualTo("설명");
        assertThat(상세.memberCount()).isEqualTo(2);
        assertThat(상세.stamp().text()).isEqualTo("기본");
    }

    @Test
    void 역할은_그룹장과_멤버와_비가입자와_탈퇴자를_구분한다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        Device 멤버 = 기기를_저장한다();
        Device 남 = 기기를_저장한다();
        Device 나간_기기 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));
        멤버로_넣는다(그룹.publicId(), 멤버);
        멤버로_넣는다(그룹.publicId(), 나간_기기);
        나간다(그룹.publicId(), 나간_기기.getId());

        // when
        GroupViewerRole 그룹장_역할 = 역할(그룹, 그룹장);
        GroupViewerRole 멤버_역할 = 역할(그룹, 멤버);
        GroupViewerRole 남의_역할 = 역할(그룹, 남);
        GroupViewerRole 나간_기기_역할 = 역할(그룹, 나간_기기);

        // then
        assertThat(그룹장_역할).isEqualTo(GroupViewerRole.OWNER);
        assertThat(멤버_역할).isEqualTo(GroupViewerRole.MEMBER);
        assertThat(남의_역할).isEqualTo(GroupViewerRole.NONE);
        assertThat(나간_기기_역할).isEqualTo(GroupViewerRole.NONE);
    }

    @Test
    void 비가입자도_상세를_조회할_수_있고_초대_코드를_받는다() {
        // given
        GroupResult 남의_그룹 = groupService.save(기기를_저장한다().getPublicId(), "남의모임", null, 스탬프("기본"));
        Device 남 = 기기를_저장한다();

        // when
        GroupDetailResult 상세 = groupService.findOne(남의_그룹.publicId(), 남.getPublicId());

        // then
        assertThat(상세.role()).isEqualTo(GroupViewerRole.NONE);
        assertThat(상세.inviteCode()).isEqualTo(남의_그룹.inviteCode());
        assertThat(상세.name()).isEqualTo("남의모임");
    }

    @Test
    void 삭제한_그룹은_가입하지_않은_기기에게도_상세를_내주지_않는다() {
        // given
        Device 그룹장 = 기기를_저장한다();
        GroupResult 그룹 = groupService.save(그룹장.getPublicId(), "한숨모임", null, 스탬프("기본"));
        Device 남 = 기기를_저장한다();
        groupService.delete(그룹.publicId(), 그룹장.getPublicId());

        // when
        Throwable 남의_예외 = catchThrowable(() -> groupService.findOne(그룹.publicId(), 남.getPublicId()));
        Throwable 그룹장의_예외 = catchThrowable(() -> groupService.findOne(그룹.publicId(), 그룹장.getPublicId()));

        // then
        그룹_오류다(남의_예외, GroupErrorCode.GROUP_NOT_FOUND);
        그룹_오류다(그룹장의_예외, GroupErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void 상세를_조회할_수_있어도_비가입자는_그룹을_고치거나_지우거나_코드를_재발급하지_못한다() {
        // given
        GroupResult 그룹 = groupService.save(기기를_저장한다().getPublicId(), "한숨모임", null, 스탬프("기본"));
        Device 남 = 기기를_저장한다();
        groupService.findOne(그룹.publicId(), 남.getPublicId());

        // when
        Throwable 수정_예외 = catchThrowable(() -> groupService.update(
                그룹.publicId(), 남.getPublicId(), "바꾼이름", null, 스탬프("변경")
        ));
        Throwable 삭제_예외 = catchThrowable(() -> groupService.delete(그룹.publicId(), 남.getPublicId()));
        Throwable 재발급_예외 =
                catchThrowable(() -> groupService.reissueInviteCode(그룹.publicId(), 남.getPublicId()));

        // then
        그룹_오류다(수정_예외, GroupErrorCode.GROUP_MEMBER_ONLY);
        그룹_오류다(삭제_예외, GroupErrorCode.GROUP_MEMBER_ONLY);
        그룹_오류다(재발급_예외, GroupErrorCode.GROUP_MEMBER_ONLY);
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

    private GroupPressCommand 한_번(EmotionState 감정) {
        return GroupPressCommand.of(Map.of(감정, 1), false);
    }

    private GroupPressCommand 묶음(Map<EmotionState, Integer> counts) {
        return GroupPressCommand.of(counts, true);
    }

    @SafeVarargs
    private Map<EmotionState, Integer> 순서대로(Map.Entry<EmotionState, Integer>... 항목들) {
        Map<EmotionState, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<EmotionState, Integer> 항목 : 항목들) {
            counts.put(항목.getKey(), 항목.getValue());
        }

        return counts;
    }

    private GroupPressCountResult 오늘_집계(GroupResult 그룹, Device 멤버) {
        return groupService.press(그룹.publicId(), 멤버.getPublicId(), 묶음(Map.of()));
    }

    private GroupViewerRole 역할(GroupResult 그룹, Device 기기) {
        return groupService.findOne(그룹.publicId(), 기기.getPublicId()).role();
    }

    private double 자른_양(String 상한) {
        return meterRegistry.get(자른_양_지표).tag("limit", 상한).summary().totalAmount();
    }

    private double 적용된_합() {
        return meterRegistry.get("pheeeew.group.press.applied").summary().totalAmount();
    }

    private double 형식_요청_수(String 형식) {
        return meterRegistry.get("pheeeew.group.press.requests").tag("format", 형식).counter().count();
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

    private LocalDate 이번_주_월요일() {
        return RankingWeek.of(clock.instant(), 0).startDate();
    }

    private void 눌린_것으로_둔다(UUID groupPublicId, LocalDate 날짜, EmotionState 감정, int 횟수) {
        Long groupId = groupRepository.findByPublicIdAndDeletedAtIsNull(groupPublicId).orElseThrow().getId();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (int i = 0; i < 횟수; i++) {
                groupDailyPressRepository.increase(groupId, 날짜, 감정.name(), 1, clock.instant());
            }
        });
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
