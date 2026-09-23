package com.pheeeew.report.presentation;

import static com.pheeeew.report.fixture.BlockFixture.저장된_사용자_차단;
import static com.pheeeew.sigh.fixture.EmotionFixture.기본_한숨_빌더;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.appversion.infra.metrics.AppVersionMetricsFilter;
import com.pheeeew.auth.fixture.AccessTokenFixture;
import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.report.application.DeviceBlockService;
import com.pheeeew.report.application.dto.BlockListResult;
import com.pheeeew.report.application.dto.BlockResult;
import com.pheeeew.report.application.dto.BlockSaveResult;
import com.pheeeew.report.exception.BlockErrorCode;
import com.pheeeew.report.exception.BlockException;
import com.pheeeew.sigh.domain.Emotion;
import com.pheeeew.sigh.exception.EmotionErrorCode;
import com.pheeeew.sigh.exception.EmotionException;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
@Import(GlobalExceptionHandler.class)
@WebMvcTest(
        controllers = DeviceBlockController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AppVersionMetricsFilter.class)
)
class DeviceBlockControllerTest {

    private static final String BLOCKS_URI = "/api/v2/blocks/devices";
    private static final Long SIGH_ID = 42L;
    private static final Long BLOCK_ID = 7L;
    private static final Instant CREATED_AT = Instant.parse("2026-09-14T02:44:00Z");
    private static final UUID 기기_공개_식별자 = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");

    private final RestTestClient client;

    @MockitoBean
    private DeviceBlockService deviceBlockService;

    @Autowired
    DeviceBlockControllerTest(RestTestClient client) {
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
    void 처음_차단하면_201과_작성자_정보가_없는_차단_응답을_반환한다() {
        // given
        when(deviceBlockService.save(SIGH_ID, 기기_공개_식별자))
                .thenReturn(BlockSaveResult.of(기본_차단_결과(), true));

        // when
        RestTestClient.ResponseSpec result = 차단한다(기본_차단_요청());

        // then
        result.expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .json(기본_차단_응답(), JsonCompareMode.STRICT);
        verify(deviceBlockService).save(SIGH_ID, 기기_공개_식별자);
    }

    @Test
    void 이미_차단한_사용자를_다시_차단하면_200과_최초_차단을_반환한다() {
        // given
        when(deviceBlockService.save(SIGH_ID, 기기_공개_식별자))
                .thenReturn(BlockSaveResult.of(기본_차단_결과(), false));

        // when
        RestTestClient.ResponseSpec result = 차단한다(기본_차단_요청());

        // then
        result.expectStatus().isOk()
                .expectBody()
                .json(기본_차단_응답(), JsonCompareMode.STRICT);
        verify(deviceBlockService).save(SIGH_ID, 기기_공개_식별자);
    }

    @Test
    void 자기_자신을_차단하면_409를_반환한다() {
        // given
        when(deviceBlockService.save(SIGH_ID, 기기_공개_식별자))
                .thenThrow(new BlockException(BlockErrorCode.BLOCK_SELF_NOT_ALLOWED));

        // when
        RestTestClient.ResponseSpec result = 차단한다(기본_차단_요청());

        // then
        오류를_검증한다(result, 409, "BLOCK-002", "자기 자신은 차단할 수 없습니다.");
    }

    @Test
    void 작성자를_알_수_없는_한숨으로_차단하면_409를_반환한다() {
        // given
        when(deviceBlockService.save(SIGH_ID, 기기_공개_식별자))
                .thenThrow(new BlockException(BlockErrorCode.BLOCK_AUTHOR_UNKNOWN));

        // when
        RestTestClient.ResponseSpec result = 차단한다(기본_차단_요청());

        // then
        오류를_검증한다(
                result,
                409,
                "BLOCK-003",
                "작성자를 알 수 없는 한숨은 사용자 차단을 할 수 없습니다."
        );
    }

    @Test
    void 차단할_한숨이_없으면_404를_반환한다() {
        // given
        when(deviceBlockService.save(SIGH_ID, 기기_공개_식별자))
                .thenThrow(new EmotionException(EmotionErrorCode.EMOTION_NOT_FOUND));

        // when
        RestTestClient.ResponseSpec result = 차단한다(기본_차단_요청());

        // then
        오류를_검증한다(result, 404, "SIGH-002", "한숨을 찾을 수 없습니다.");
    }

    @Test
    void 차단_대상_한숨_식별자가_없으면_400을_반환한다() {
        // given / when
        RestTestClient.ResponseSpec result = 차단한다("{}");

        // then
        오류를_검증한다(result, 400, "COMMON-001", "요청 값이 올바르지 않습니다.");
        verifyNoInteractions(deviceBlockService);
    }

    @Test
    void 인증_컨텍스트가_없으면_차단자를_비운_채_차단하지_않고_500을_반환한다() {
        // given
        SecurityContextHolder.clearContext();

        // when
        RestTestClient.ResponseSpec result = 차단한다(기본_차단_요청());

        // then
        오류를_검증한다(result, 500, "COMMON-002", "서버 내부 오류가 발생했습니다.");
        verifyNoInteractions(deviceBlockService);
    }

    @Test
    void 차단_목록은_차단_식별자와_근거_한숨을_함께_반환한다() {
        // given
        when(deviceBlockService.findAll(기기_공개_식별자, "opaque-cursor"))
                .thenReturn(BlockListResult.of(List.of(기본_차단_결과()), true, "next-cursor"));

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri(BLOCKS_URI + "?cursor=opaque-cursor")
                .exchange();

        // then
        result.expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .json("""
                        {
                          "items": [%s],
                          "hasNext": true,
                          "nextCursor": "next-cursor"
                        }
                        """.formatted(기본_차단_응답()), JsonCompareMode.STRICT);
        verify(deviceBlockService).findAll(기기_공개_식별자, "opaque-cursor");
    }

    @Test
    void 사용할_수_없는_커서는_400을_반환한다() {
        // given
        when(deviceBlockService.findAll(기기_공개_식별자, "invalid-cursor"))
                .thenThrow(new BlockException(BlockErrorCode.BLOCK_INVALID_CURSOR));

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri(BLOCKS_URI + "?cursor=invalid-cursor")
                .exchange();

        // then
        오류를_검증한다(result, 400, "BLOCK-004", "차단 목록 커서를 사용할 수 없습니다.");
    }

    @Test
    void 차단을_해제할_때는_한숨_식별자가_아니라_차단_식별자를_서비스에_넘긴다() {
        // given / when
        RestTestClient.ResponseSpec result = client.delete()
                .uri(BLOCKS_URI + "/{blockId}", BLOCK_ID)
                .exchange();

        // then
        result.expectStatus().isNoContent()
                .expectBody().isEmpty();
        verify(deviceBlockService).delete(BLOCK_ID, 기기_공개_식별자);
    }

    private RestTestClient.ResponseSpec 차단한다(String body) {
        return client.post()
                .uri(BLOCKS_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private void 오류를_검증한다(
            RestTestClient.ResponseSpec result,
            int status,
            String code,
            String message
    ) {
        result.expectStatus().isEqualTo(status)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .json("""
                        {"code":"%s","message":"%s"}
                        """.formatted(code, message), JsonCompareMode.STRICT);
    }

    private BlockResult 기본_차단_결과() {
        Emotion sigh = 기본_한숨_빌더()
                .nickname("날아가는 고라니")
                .memo("오늘은 조금 지쳤다")
                .build();

        return BlockResult.of(저장된_사용자_차단(BLOCK_ID, CREATED_AT), sigh);
    }

    private String 기본_차단_요청() {
        return """
                {"sighId": 42}
                """;
    }

    private String 기본_차단_응답() {
        return """
                {
                  "blockId": 7,
                  "sighId": 42,
                  "nickname": "날아가는 고라니",
                  "memo": "오늘은 조금 지쳤다",
                  "createdAt": "2026-09-14T02:44:00Z"
                }
                """;
    }

}
