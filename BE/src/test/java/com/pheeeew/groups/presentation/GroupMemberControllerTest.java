package com.pheeeew.groups.presentation;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.appversion.infra.metrics.AppVersionMetricsFilter;
import com.pheeeew.auth.fixture.AccessTokenFixture;
import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.groups.application.GroupService;
import com.pheeeew.groups.application.dto.GroupResult;
import com.pheeeew.groups.application.dto.GroupStampResult;
import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupRole;
import com.pheeeew.groups.domain.StampFrame;
import com.pheeeew.groups.exception.GroupErrorCode;
import com.pheeeew.groups.exception.GroupException;
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
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
@Import(GlobalExceptionHandler.class)
@WebMvcTest(
        controllers = GroupMemberController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AppVersionMetricsFilter.class)
)
class GroupMemberControllerTest {

    private static final String GROUPS_URI = "/api/v2/groups";
    private static final String JOIN_URI = GROUPS_URI + "/join";
    private static final String 초대_코드 = "A1B2C3";
    private static final UUID 기기_공개_식별자 = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");
    private static final UUID 그룹_공개_식별자 = UUID.fromString("5f2b1c84-9d0e-4a13-b6c7-2e8f0a4d7b19");

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
    void 처음_참여하면_201과_위치_헤더를_반환한다() {
        // given
        GroupResult 참여한_그룹 = 기본_그룹_결과();
        when(groupService.join(기기_공개_식별자, 초대_코드)).thenReturn(참여한_그룹);

        // when
        RestTestClient.ResponseSpec result = 참여한다(초대_코드);

        // then
        result.expectStatus().isCreated()
                .expectHeader().location(GROUPS_URI + "/" + 참여한_그룹.publicId());
        verify(groupService).join(기기_공개_식별자, 초대_코드);
    }

    @Test
    void 이미_속해_있으면_409를_반환한다() {
        // given
        when(groupService.join(기기_공개_식별자, 초대_코드))
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
        when(groupService.join(기기_공개_식별자, 서비스가_받는_코드)).thenReturn(기본_그룹_결과());

        // when
        참여한다(보낸_코드);

        // then
        verify(groupService).join(기기_공개_식별자, 서비스가_받는_코드);
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
        when(groupService.join(기기_공개_식별자, 초대_코드))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_NOT_FOUND));

        // when
        RestTestClient.ResponseSpec result = 참여한다(초대_코드);

        // then
        result.expectStatus().isNotFound();
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

    private RestTestClient.ResponseSpec 참여한다(String 코드) {
        return client.post()
                .uri(JOIN_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"inviteCode": "%s"}
                        """.formatted(코드))
                .exchange();
    }

    private RestTestClient.ResponseSpec 나간다() {
        return client.delete()
                .uri(GROUPS_URI + "/" + 그룹_공개_식별자 + "/members/me")
                .exchange();
    }

    private GroupResult 기본_그룹_결과() {
        Group group = Group.builder()
                .name("한숨모임")
                .description(null)
                .inviteCode(초대_코드)
                .build();

        return GroupResult.of(
                group,
                GroupRole.MEMBER,
                2,
                new GroupStampResult("기본", "#FFFFFF", "#4A90D9", StampFrame.CIRCLE)
        );
    }
}
