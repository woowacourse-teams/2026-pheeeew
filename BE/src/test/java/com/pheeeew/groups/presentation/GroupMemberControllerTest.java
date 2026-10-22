package com.pheeeew.groups.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.auth.fixture.AccessTokenFixture;
import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.groups.application.GroupService;
import com.pheeeew.groups.application.dto.GroupMemberResult;
import com.pheeeew.groups.application.dto.GroupMemberListResult;
import com.pheeeew.groups.application.dto.GroupPreviewResult;
import com.pheeeew.groups.application.dto.GroupResult;

import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.application.dto.GroupStampResult;
import com.pheeeew.groups.domain.GroupJoinType;
import com.pheeeew.groups.domain.GroupRole;
import com.pheeeew.groups.domain.StampFrame;
import com.pheeeew.groups.exception.GroupErrorCode;
import com.pheeeew.groups.exception.GroupException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
@Import(GlobalExceptionHandler.class)
@WebMvcTest(
        controllers = GroupMemberController.class
)
class GroupMemberControllerTest {

    private static final String GROUPS_URI = "/api/v2/groups";
    private static final String JOIN_URI = GROUPS_URI + "/join";
    private static final String 초대_코드 = "A1B2C3";
    private static final UUID 기기_공개_식별자 = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");
    private static final UUID 그룹_공개_식별자 = UUID.fromString("5f2b1c84-9d0e-4a13-b6c7-2e8f0a4d7b19");
    private static final String MEMBERSHIP_URI = GROUPS_URI + "/" + 그룹_공개_식별자 + "/members/me";

    private final RestTestClient client;

    @MockitoBean
    private GroupService groupService;

    @Autowired
    GroupMemberControllerTest(RestTestClient client) {
        this.client = client;
    }

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext()
                .setAuthentication(AccessTokenFixture.인증된_기기(기기_공개_식별자));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 초대_코드로_그룹을_찾는다() {
        // given
        when(groupService.findByInviteCode(초대_코드)).thenReturn(기본_미리보기());

        // when
        RestTestClient.ResponseSpec result = 찾는다(초대_코드);

        // then
        result.expectStatus().isOk();
        verify(groupService).findByInviteCode(초대_코드);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a1b2c3", "AIBLCO"})
    void 검색도_참여와_똑같이_코드를_맞춰_읽는다(String 보낸_코드) {
        // given
        when(groupService.findByInviteCode(any())).thenReturn(기본_미리보기());

        // when
        찾는다(보낸_코드);

        // then
        verify(groupService).findByInviteCode(보낸_코드.toUpperCase()
                .replace('I', '1').replace('L', '1').replace('O', '0'));
    }

    @Test
    void 없는_코드로_찾으면_404다() {
        // given
        when(groupService.findByInviteCode(초대_코드))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_NOT_FOUND));

        // when
        RestTestClient.ResponseSpec result = 찾는다(초대_코드);

        // then
        result.expectStatus().isNotFound();
    }

    @Test
    void 처음_참여하면_201과_위치_헤더를_반환한다() {
        // given
        GroupResult 참여한_그룹 = 기본_그룹_결과();
        when(groupService.joinByInviteCode(기기_공개_식별자, 초대_코드)).thenReturn(참여한_그룹);

        // when
        RestTestClient.ResponseSpec result = 참여한다(초대_코드);

        // then
        result.expectStatus().isCreated()
                .expectHeader().location(GROUPS_URI + "/" + 참여한_그룹.publicId());
        verify(groupService).joinByInviteCode(기기_공개_식별자, 초대_코드);
    }

    @Test
    void 이미_속해_있으면_409를_반환한다() {
        // given
        when(groupService.joinByInviteCode(기기_공개_식별자, 초대_코드))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_ALREADY_JOINED));

        // when
        RestTestClient.ResponseSpec result = 참여한다(초대_코드);

        // then
        result.expectStatus().isEqualTo(409);
    }

    @ParameterizedTest
    @CsvSource({
            "a1b2c3, A1B2C3",
            "' A1B2C3 ', A1B2C3",
            "AIBLCO, A1B1C0",
            "aibloc, A1B10C"
    })
    void 초대_코드를_대문자로_맞추고_혼동되는_글자를_바꾼다(String 보낸_코드, String 서비스가_받는_코드) {
        // given
        when(groupService.joinByInviteCode(기기_공개_식별자, 서비스가_받는_코드)).thenReturn(기본_그룹_결과());

        // when
        참여한다(보낸_코드);

        // then
        verify(groupService).joinByInviteCode(기기_공개_식별자, 서비스가_받는_코드);
    }

    @ParameterizedTest
    @ValueSource(strings = {"A1B2C", "A1B2C3D", "A1B2C!", "", "  "})
    void 초대_코드_형식이_맞지_않으면_400이고_서비스를_부르지_않는다(String 코드) {
        // when
        RestTestClient.ResponseSpec result = 참여한다(코드);

        // then
        result.expectStatus().isBadRequest();
        verifyNoInteractions(groupService);
    }

    @Test
    void 초대_코드를_아예_보내지_않으면_400이다() {
        // when
        RestTestClient.ResponseSpec result = client.post()
                .uri(JOIN_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .exchange();

        // then
        result.expectStatus().isBadRequest();
        verifyNoInteractions(groupService);
    }

    @Test
    void 없는_초대_코드면_404를_반환한다() {
        // given
        when(groupService.joinByInviteCode(기기_공개_식별자, 초대_코드))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_NOT_FOUND));

        // when
        RestTestClient.ResponseSpec result = 참여한다(초대_코드);

        // then
        result.expectStatus().isNotFound();
    }

    @Test
    void 공개_그룹에_참여하면_201과_멤버십_위치_헤더를_반환한다() {
        // given
        when(groupService.joinPublicGroup(그룹_공개_식별자, 기기_공개_식별자)).thenReturn(공개_그룹_결과());

        // when
        RestTestClient.ResponseSpec result = 코드_없이_참여한다();

        // then
        result.expectStatus().isCreated()
                .expectHeader().location(MEMBERSHIP_URI)
                .expectBody()
                .jsonPath("$.joinType").isEqualTo("PUBLIC");
        verify(groupService).joinPublicGroup(그룹_공개_식별자, 기기_공개_식별자);
    }

    @Test
    void 비공개_그룹에_코드_없이_참여하면_409와_초대_코드_필요를_반환한다() {
        // given
        when(groupService.joinPublicGroup(그룹_공개_식별자, 기기_공개_식별자))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_INVITE_CODE_REQUIRED));

        // when
        RestTestClient.ResponseSpec result = 코드_없이_참여한다();

        // then
        result.expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("GROUP-009");
    }

    @Test
    void 이미_속한_공개_그룹에_참여하면_409를_반환한다() {
        // given
        when(groupService.joinPublicGroup(그룹_공개_식별자, 기기_공개_식별자))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_ALREADY_JOINED));

        // when
        RestTestClient.ResponseSpec result = 코드_없이_참여한다();

        // then
        result.expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("GROUP-007");
    }

    @Test
    void 없는_그룹에_코드_없이_참여하면_404를_반환한다() {
        // given
        when(groupService.joinPublicGroup(그룹_공개_식별자, 기기_공개_식별자))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_NOT_FOUND));

        // when
        RestTestClient.ResponseSpec result = 코드_없이_참여한다();

        // then
        result.expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("GROUP-001");
    }

    @Test
    void 그룹_식별자가_UUID가_아니면_400이고_서비스를_부르지_않는다() {
        // when
        RestTestClient.ResponseSpec result = client.post()
                .uri(GROUPS_URI + "/not-a-uuid/members/me")
                .exchange();

        // then
        result.expectStatus().isBadRequest();
        verifyNoInteractions(groupService);
    }

    @Test
    void 그룹을_나가면_204를_반환한다() {
        // when
        RestTestClient.ResponseSpec result = 나간다();

        // then
        result.expectStatus().isNoContent();
        verify(groupService).leave(그룹_공개_식별자, 기기_공개_식별자);
    }

    @Test
    void 그룹장이_나가려_하면_409를_반환한다() {
        // given
        doThrow(new GroupException(GroupErrorCode.GROUP_OWNER_CANNOT_LEAVE))
                .when(groupService).leave(그룹_공개_식별자, 기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = 나간다();

        // then
        result.expectStatus().isEqualTo(409);
    }

    @Test
    void 속하지_않은_그룹에서_나가려_하면_403을_반환한다() {
        // given
        doThrow(new GroupException(GroupErrorCode.GROUP_MEMBER_ONLY))
                .when(groupService).leave(그룹_공개_식별자, 기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = 나간다();

        // then
        result.expectStatus().isForbidden();
    }

    @Test
    void 없는_그룹에서_나가려_하면_404를_반환한다() {
        // given
        doThrow(new GroupException(GroupErrorCode.GROUP_NOT_FOUND))
                .when(groupService).leave(그룹_공개_식별자, 기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = 나간다();

        // then
        result.expectStatus().isNotFound();
    }

    @Test
    void 멤버_목록을_조회하면_닉네임과_역할과_가입_시각을_반환한다() {
        // given
        when(groupService.findMembers(eq(그룹_공개_식별자), eq(기기_공개_식별자), any(), anyInt()))
                .thenReturn(기본_멤버_목록());

        // when
        RestTestClient.ResponseSpec result = 멤버_목록을_조회한다("");

        // then
        result.expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].nickname").isEqualTo("한숨러")
                .jsonPath("$.items[0].role").isEqualTo("OWNER")
                .jsonPath("$.items[0].joinedAt").isEqualTo("2026-10-11T09:00:00Z")
                .jsonPath("$.hasNext").isEqualTo(true)
                .jsonPath("$.nextCursor").isEqualTo("next-cursor");
        verify(groupService).findMembers(그룹_공개_식별자, 기기_공개_식별자, null, 20);
    }

    @Test
    void 멤버_목록의_커서와_페이지_크기를_서비스에_그대로_전달한다() {
        // given
        when(groupService.findMembers(eq(그룹_공개_식별자), eq(기기_공개_식별자), any(), anyInt()))
                .thenReturn(GroupMemberListResult.of(List.of(), false, null));

        // when
        멤버_목록을_조회한다("?cursor=opaque-cursor&size=50");

        // then
        verify(groupService).findMembers(그룹_공개_식별자, 기기_공개_식별자, "opaque-cursor", 50);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "51"})
    void 멤버_목록의_페이지_크기가_범위를_벗어나면_400이고_서비스를_부르지_않는다(String 페이지_크기) {
        // when
        RestTestClient.ResponseSpec result = 멤버_목록을_조회한다("?size=" + 페이지_크기);

        // then
        result.expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(groupService);
    }

    @Test
    void 멤버_목록을_사용할_수_없는_커서로_조회하면_400을_반환한다() {
        // given
        when(groupService.findMembers(eq(그룹_공개_식별자), eq(기기_공개_식별자), any(), anyInt()))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_INVALID_CURSOR));

        // when
        RestTestClient.ResponseSpec result = 멤버_목록을_조회한다("?cursor=broken");

        // then
        result.expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("GROUP-010");
    }

    @Test
    void 비멤버가_멤버_목록을_조회하면_403을_반환한다() {
        // given
        when(groupService.findMembers(eq(그룹_공개_식별자), eq(기기_공개_식별자), any(), anyInt()))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_MEMBER_ONLY));

        // when
        RestTestClient.ResponseSpec result = 멤버_목록을_조회한다("");

        // then
        result.expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("GROUP-008");
    }

    @Test
    void 없는_그룹의_멤버_목록을_조회하면_404를_반환한다() {
        // given
        when(groupService.findMembers(eq(그룹_공개_식별자), eq(기기_공개_식별자), any(), anyInt()))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_NOT_FOUND));

        // when
        RestTestClient.ResponseSpec result = 멤버_목록을_조회한다("");

        // then
        result.expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("GROUP-001");
    }

    @Test
    void 멤버_목록의_그룹_식별자가_UUID가_아니면_400이고_서비스를_부르지_않는다() {
        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri(GROUPS_URI + "/not-a-uuid/members")
                .exchange();

        // then
        result.expectStatus().isBadRequest();
        verifyNoInteractions(groupService);
    }

    private RestTestClient.ResponseSpec 멤버_목록을_조회한다(String 쿼리) {
        return client.get()
                .uri(GROUPS_URI + "/" + 그룹_공개_식별자 + "/members" + 쿼리)
                .exchange();
    }

    private GroupMemberListResult 기본_멤버_목록() {
        return GroupMemberListResult.of(
                List.of(new GroupMemberResult(
                        "한숨러", GroupRole.OWNER, Instant.parse("2026-10-11T09:00:00Z"), 1L
                )),
                true,
                "next-cursor"
        );
    }

    private RestTestClient.ResponseSpec 찾는다(String 코드) {
        return client.get()
                .uri(GROUPS_URI + "/search?inviteCode=" + 코드)
                .exchange();
    }

    private GroupPreviewResult 기본_미리보기() {
        return new GroupPreviewResult(
                그룹_공개_식별자,
                "한숨모임",
                null,
                2,
                new GroupStampResult("기본", "#FFFFFF", "#4A90D9", StampFrame.CIRCLE)
        );
    }

    private RestTestClient.ResponseSpec 참여한다(String 코드) {
        return client.post()
                .uri(JOIN_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"inviteCode": "%s"}
                        """.formatted(코드))
                .exchange();
    }

    private RestTestClient.ResponseSpec 코드_없이_참여한다() {
        return client.post()
                .uri(MEMBERSHIP_URI)
                .exchange();
    }

    private RestTestClient.ResponseSpec 나간다() {
        return client.delete()
                .uri(GROUPS_URI + "/" + 그룹_공개_식별자 + "/members/me")
                .exchange();
    }

    private GroupResult 공개_그룹_결과() {
        Group group = Group.builder()
                .name("공개모임")
                .description(null)
                .inviteCode(초대_코드)
                .joinType(GroupJoinType.PUBLIC)
                .build();

        return GroupResult.of(
                group,
                GroupRole.MEMBER,
                2,
                new GroupStampResult("기본", "#FFFFFF", "#4A90D9", StampFrame.CIRCLE)
        );
    }

    private GroupResult 기본_그룹_결과() {
        Group group = Group.builder()
                .name("한숨모임")
                .description(null)
                .inviteCode(초대_코드)
                .joinType(GroupJoinType.PRIVATE)
                .build();

        return GroupResult.of(
                group,
                GroupRole.MEMBER,
                2,
                new GroupStampResult("기본", "#FFFFFF", "#4A90D9", StampFrame.CIRCLE)
        );
    }
}
