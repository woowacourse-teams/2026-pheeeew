package com.pheeeew.groups.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.auth.fixture.AccessTokenFixture;
import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.groups.application.GroupExploreService;
import com.pheeeew.groups.application.dto.GroupExploreResult;
import com.pheeeew.groups.application.dto.GroupExploreListResult;
import com.pheeeew.groups.application.dto.GroupStampResult;
import com.pheeeew.groups.domain.GroupJoinType;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
@Import(GlobalExceptionHandler.class)
@WebMvcTest(
        controllers = GroupExploreController.class
)
class GroupExploreControllerTest {

    private static final String EXPLORE_URI = "/api/v2/groups/explore";
    private static final UUID 기기_공개_식별자 = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");
    private static final UUID 그룹_공개_식별자 = UUID.fromString("5f2b1c84-9d0e-4a13-b6c7-2e8f0a4d7b19");
    private static final int 기본_페이지_크기 = 20;

    private final RestTestClient client;

    @MockitoBean
    private GroupExploreService groupExploreService;

    @Autowired
    GroupExploreControllerTest(RestTestClient client) {
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
    void 목록을_조회하면_항목과_다음_커서를_반환한다() {
        // given
        when(groupExploreService.findAll(eq(기기_공개_식별자), any(), any(), anyInt()))
                .thenReturn(GroupExploreListResult.of(List.of(기본_항목()), true, "next-cursor"));

        // when
        RestTestClient.ResponseSpec result = 조회한다("");

        // then
        result.expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].groupId").isEqualTo(그룹_공개_식별자.toString())
                .jsonPath("$.items[0].name").isEqualTo("한숨모임")
                .jsonPath("$.items[0].description").isEqualTo("퇴근하고 한숨 쉬는 모임")
                .jsonPath("$.items[0].memberCount").isEqualTo(7)
                .jsonPath("$.items[0].joinType").isEqualTo("PUBLIC")
                .jsonPath("$.items[0].joined").isEqualTo(false)
                .jsonPath("$.items[0].stamp.frame").isEqualTo("CIRCLE")
                .jsonPath("$.items[0].inviteCode").doesNotExist()
                .jsonPath("$.hasNext").isEqualTo(true)
                .jsonPath("$.nextCursor").isEqualTo("next-cursor");
    }

    @Test
    void 검색어와_커서를_생략하면_서비스에_값_없이_전달한다() {
        // given
        when(groupExploreService.findAll(eq(기기_공개_식별자), any(), any(), anyInt()))
                .thenReturn(빈_목록());

        // when
        조회한다("");

        // then
        verify(groupExploreService).findAll(기기_공개_식별자, null, null, 기본_페이지_크기);
    }

    @Test
    void 검색어를_다듬지_않고_서비스에_그대로_전달한다() {
        // given
        when(groupExploreService.findAll(eq(기기_공개_식별자), any(), any(), anyInt()))
                .thenReturn(빈_목록());

        // when
        검색어로_조회한다("  모임  ");

        // then
        verify(groupExploreService).findAll(기기_공개_식별자, "  모임  ", null, 기본_페이지_크기);
    }

    @Test
    void 커서와_페이지_크기를_서비스에_그대로_전달한다() {
        // given
        when(groupExploreService.findAll(eq(기기_공개_식별자), any(), any(), anyInt()))
                .thenReturn(빈_목록());

        // when
        조회한다("?cursor=opaque-cursor&size=50");

        // then
        verify(groupExploreService).findAll(기기_공개_식별자, null, "opaque-cursor", 50);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "51", "-1"})
    void 페이지_크기가_범위를_벗어나면_400이고_서비스를_부르지_않는다(String 페이지_크기) {
        // when
        RestTestClient.ResponseSpec result = 조회한다("?size=" + 페이지_크기);

        // then
        result.expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(groupExploreService);
    }

    @Test
    void 페이지_크기가_숫자가_아니면_400이고_서비스를_부르지_않는다() {
        // when
        RestTestClient.ResponseSpec result = 조회한다("?size=abc");

        // then
        result.expectStatus().isBadRequest();
        verifyNoInteractions(groupExploreService);
    }

    @Test
    void 사용할_수_없는_커서면_400과_커서_오류_코드를_반환한다() {
        // given
        when(groupExploreService.findAll(eq(기기_공개_식별자), any(), any(), anyInt()))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_INVALID_CURSOR));

        // when
        RestTestClient.ResponseSpec result = 조회한다("?cursor=broken");

        // then
        result.expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("GROUP-010");
    }

    private RestTestClient.ResponseSpec 조회한다(String 쿼리) {
        return client.get()
                .uri(EXPLORE_URI + 쿼리)
                .exchange();
    }

    private RestTestClient.ResponseSpec 검색어로_조회한다(String 검색어) {
        return client.get()
                .uri(builder -> builder.path(EXPLORE_URI).queryParam("keyword", 검색어).build())
                .exchange();
    }

    private GroupExploreListResult 빈_목록() {
        return GroupExploreListResult.of(List.of(), false, null);
    }

    private GroupExploreResult 기본_항목() {
        return new GroupExploreResult(
                그룹_공개_식별자,
                "한숨모임",
                "퇴근하고 한숨 쉬는 모임",
                GroupJoinType.PUBLIC,
                7L,
                false,
                GroupStampResult.of("기본", "#FFFFFF", "#4A90D9", StampFrame.CIRCLE),
                Instant.parse("2026-10-11T09:00:00Z"),
                11L
        );
    }
}
