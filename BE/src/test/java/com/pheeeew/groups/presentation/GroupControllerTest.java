package com.pheeeew.groups.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        controllers = GroupController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AppVersionMetricsFilter.class)
)
class GroupControllerTest {

    private static final String GROUPS_URI = "/api/v2/groups";
    private static final UUID 기기_공개_식별자 = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");
    private static final UUID 그룹_공개_식별자 = UUID.fromString("5f2b1c84-9d0e-4a13-b6c7-2e8f0a4d7b19");

    private final RestTestClient client;

    @MockitoBean
    private GroupService groupService;

    @Autowired
    GroupControllerTest(RestTestClient client) {
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
    void 그룹을_만들면_201과_위치_헤더를_반환한다() {
        // given
        GroupResult 만든_그룹 = 기본_결과();
        when(groupService.save(eq(기기_공개_식별자), eq("한숨모임"), eq("설명입니다"), any()))
                .thenReturn(만든_그룹);

        // when
        RestTestClient.ResponseSpec result = 생성한다("""
                {"name": "한숨모임", "description": "설명입니다", %s}
                """.formatted(스탬프_본문()));

        // then
        result.expectStatus().isCreated()
                .expectHeader().location(GROUPS_URI + "/" + 만든_그룹.publicId());
        verify(groupService).save(eq(기기_공개_식별자), eq("한숨모임"), eq("설명입니다"), any());
    }

    @Test
    void 그룹_이름의_앞뒤_공백은_잘려서_전달된다() {
        // given
        when(groupService.save(eq(기기_공개_식별자), eq("한숨모임"), eq(null), any()))
                .thenReturn(기본_결과());

        // when
        생성한다("""
                {"name": "  한숨모임  ", "description": "   ", %s}
                """.formatted(스탬프_본문()));

        // then
        verify(groupService).save(eq(기기_공개_식별자), eq("한숨모임"), eq(null), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"한", "열한글자짜리이름입니다", "  ", ""})
    void 이름_길이가_맞지_않으면_400이고_서비스를_부르지_않는다(String 이름) {
        // when
        RestTestClient.ResponseSpec result = 생성한다("""
                {"name": "%s", %s}
                """.formatted(이름, 스탬프_본문()));

        // then
        result.expectStatus().isBadRequest();
        verifyNoInteractions(groupService);
    }

    @Test
    void 이름을_아예_보내지_않으면_400이다() {
        // when
        RestTestClient.ResponseSpec result = 생성한다("""
                {"description": "설명입니다", %s}
                """.formatted(스탬프_본문()));

        // then
        result.expectStatus().isBadRequest();
        verifyNoInteractions(groupService);
    }

    @Test
    void 스탬프_글자를_아예_보내지_않으면_400이다() {
        // when
        RestTestClient.ResponseSpec result = 생성한다("""
                {"name": "한숨모임", "stamp": {"textColor": "#FFFFFF",
                 "backgroundColor": "#000000", "frame": "CIRCLE"}}
                """);

        // then
        result.expectStatus().isBadRequest();
        verifyNoInteractions(groupService);
    }

    @Test
    void 스탬프_없이_그룹을_만들_수_없다() {
        // when
        RestTestClient.ResponseSpec result = 생성한다("""
                {"name": "한숨모임", "description": "설명입니다"}
                """);

        // then
        result.expectStatus().isBadRequest();
        verifyNoInteractions(groupService);
    }

    @Test
    void 설명이_백_자를_넘으면_400이다() {
        // when
        RestTestClient.ResponseSpec result = 생성한다("""
                {"name": "한숨모임", "description": "%s", %s}
                """.formatted("가".repeat(101), 스탬프_본문()));

        // then
        result.expectStatus().isBadRequest();
        verifyNoInteractions(groupService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"#FFF", "FFFFFF", "#GGGGGG", "#FFFFFFF", "빨강"})
    void 색_형식이_맞지_않으면_400이다(String 색) {
        // when
        RestTestClient.ResponseSpec result = 변경한다("""
                {"name": "한숨모임", "stamp": {"text": "기본", "textColor": "%s",
                 "backgroundColor": "#FFFFFF", "frame": "CIRCLE"}}
                """.formatted(색));

        // then
        result.expectStatus().isBadRequest();
        verifyNoInteractions(groupService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"#FFFFFF", "#FFFFFFAA", "#ffffff"})
    void 여섯_자리와_여덟_자리_색을_모두_받는다(String 색) {
        // given
        when(groupService.update(eq(그룹_공개_식별자), eq(기기_공개_식별자), any(), any(), any()))
                .thenReturn(기본_결과());

        // when
        RestTestClient.ResponseSpec result = 변경한다("""
                {"name": "한숨모임", "stamp": {"text": "기본", "textColor": "%s",
                 "backgroundColor": "#000000", "frame": "CIRCLE"}}
                """.formatted(색));

        // then
        result.expectStatus().isOk();
    }

    @ParameterizedTest
    @ValueSource(strings = {"한", "다섯글자임"})
    void 스탬프_글자가_두_자에서_네_자가_아니면_400이다(String 글자) {
        // when
        RestTestClient.ResponseSpec result = 변경한다("""
                {"name": "한숨모임", "stamp": {"text": "%s", "textColor": "#FFFFFF",
                 "backgroundColor": "#000000", "frame": "CIRCLE"}}
                """.formatted(글자));

        // then
        result.expectStatus().isBadRequest();
        verifyNoInteractions(groupService);
    }

    @Test
    void 없는_스탬프_틀을_보내면_400이다() {
        // when
        RestTestClient.ResponseSpec result = 변경한다("""
                {"name": "한숨모임", "stamp": {"text": "기본", "textColor": "#FFFFFF",
                 "backgroundColor": "#000000", "frame": "NEON"}}
                """);

        // then
        result.expectStatus().isBadRequest();
        verifyNoInteractions(groupService);
    }

    @Test
    void 속하지_않은_그룹을_조회하면_404를_반환한다() {
        // given
        when(groupService.findOne(그룹_공개_식별자, 기기_공개_식별자))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_NOT_FOUND));

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri(GROUPS_URI + "/" + 그룹_공개_식별자)
                .exchange();

        // then
        result.expectStatus().isNotFound();
    }

    @Test
    void 그룹장이_아니면_403을_반환한다() {
        // given
        when(groupService.reissueInviteCode(그룹_공개_식별자, 기기_공개_식별자))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_OWNER_ONLY));

        // when
        RestTestClient.ResponseSpec result = client.post()
                .uri(GROUPS_URI + "/" + 그룹_공개_식별자 + "/invite-code")
                .exchange();

        // then
        result.expectStatus().isForbidden();
    }

    @Test
    void 그룹을_삭제하면_204를_반환한다() {
        // when
        RestTestClient.ResponseSpec result = client.delete()
                .uri(GROUPS_URI + "/" + 그룹_공개_식별자)
                .exchange();

        // then
        result.expectStatus().isNoContent();
        verify(groupService).delete(그룹_공개_식별자, 기기_공개_식별자);
    }

    @Test
    void 다른_멤버가_남아_있으면_409를_반환한다() {
        // given
        doThrow(new GroupException(GroupErrorCode.GROUP_MEMBER_REMAINS))
                .when(groupService).delete(그룹_공개_식별자, 기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = client.delete()
                .uri(GROUPS_URI + "/" + 그룹_공개_식별자)
                .exchange();

        // then
        result.expectStatus().isEqualTo(409);
    }

    private String 스탬프_본문() {
        return """
                "stamp": {"text": "기본", "textColor": "#FFFFFF",
                 "backgroundColor": "#000000", "frame": "CIRCLE"}""";
    }

    private RestTestClient.ResponseSpec 생성한다(String body) {
        return client.post()
                .uri(GROUPS_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private RestTestClient.ResponseSpec 변경한다(String body) {
        return client.put()
                .uri(GROUPS_URI + "/" + 그룹_공개_식별자)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private GroupResult 기본_결과() {
        Group group = Group.builder()
                .name("한숨모임")
                .description("설명입니다")
                .inviteCode("A1B2C3")
                .build();

        return GroupResult.of(
                group,
                GroupRole.OWNER,
                1,
                new GroupStampResult("기본", "#FFFFFF", "#4A90D9", StampFrame.CIRCLE)
        );
    }
}
