package com.pheeeew.groups.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.appversion.infra.metrics.AppVersionMetricsFilter;
import com.pheeeew.auth.fixture.AccessTokenFixture;
import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.groups.application.GroupRankingService;
import com.pheeeew.groups.application.dto.GroupRankingItem;
import com.pheeeew.groups.application.dto.GroupRankingResult;
import com.pheeeew.groups.application.dto.GroupStampResult;
import com.pheeeew.groups.domain.StampFrame;
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
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
@Import(GlobalExceptionHandler.class)
@WebMvcTest(
        controllers = GroupRankingController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AppVersionMetricsFilter.class)
)
class GroupRankingControllerTest {

    private static final String GROUPS_URI = "/api/v2/groups";
    private static final UUID 기기_공개_식별자 = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");
    private static final UUID 그룹_공개_식별자 = UUID.fromString("5f2b1c84-9d0e-4a13-b6c7-2e8f0a4d7b19");

    private final RestTestClient client;

    @MockitoBean
    private GroupRankingService groupRankingService;

    @Autowired
    GroupRankingControllerTest(RestTestClient client) {
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
    void 그룹_간_랭킹_경로가_그룹_상세로_잘못_라우팅되지_않는다() {
        // given
        when(groupRankingService.findGroupRanking(0)).thenReturn(기본_그룹_랭킹());

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri(GROUPS_URI + "/rankings")
                .exchange();

        // then
        result.expectStatus().isOk();
        verify(groupRankingService).findGroupRanking(0);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 52, 520})
    void 몇_주_전이든_뒤로_넘겨_볼_수_있다(int 몇_주_전) {
        // given
        when(groupRankingService.findGroupRanking(몇_주_전)).thenReturn(기본_그룹_랭킹());

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri(GROUPS_URI + "/rankings?weeksAgo=" + 몇_주_전)
                .exchange();

        // then
        result.expectStatus().isOk();
        verify(groupRankingService).findGroupRanking(몇_주_전);
    }

    @Test
    void 주를_안_보내면_이번주로_본다() {
        // given
        when(groupRankingService.findGroupRanking(0)).thenReturn(기본_그룹_랭킹());

        // when
        client.get().uri(GROUPS_URI + "/rankings").exchange();

        // then
        verify(groupRankingService).findGroupRanking(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "521", "지난주"})
    void 뒤로_갈_수_없는_값을_보내면_400이다(String 몇_주_전) {
        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri(GROUPS_URI + "/rankings?weeksAgo=" + 몇_주_전)
                .exchange();

        // then
        result.expectStatus().isBadRequest();
        verifyNoInteractions(groupRankingService);
    }

    private GroupRankingResult 기본_그룹_랭킹() {
        return GroupRankingResult.of(
                0,
                Instant.parse("2026-09-20T15:00:00Z"),
                Instant.parse("2026-09-27T15:00:00Z"),
                true,
                List.of(new GroupRankingItem(
                        1,
                        그룹_공개_식별자,
                        "한숨모임",
                        new GroupStampResult("기본", "#FFFFFF", "#4A90D9", StampFrame.CIRCLE),
                        7
                ))
        );
    }
}
