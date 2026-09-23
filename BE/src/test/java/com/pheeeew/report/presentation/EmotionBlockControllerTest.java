package com.pheeeew.report.presentation;

import static com.pheeeew.report.fixture.BlockFixture.저장된_한숨_차단;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.appversion.infra.metrics.AppVersionMetricsFilter;
import com.pheeeew.auth.fixture.AccessTokenFixture;
import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.report.application.EmotionBlockService;
import com.pheeeew.report.application.dto.BlockListResult;
import com.pheeeew.report.application.dto.BlockResult;
import com.pheeeew.report.application.dto.BlockSaveResult;
import com.pheeeew.report.exception.BlockErrorCode;
import com.pheeeew.report.exception.BlockException;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.exception.EmotionErrorCode;
import com.pheeeew.emotion.exception.EmotionException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
        controllers = EmotionBlockController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AppVersionMetricsFilter.class)
)
class EmotionBlockControllerTest {

    private static final String BLOCKS_URI = "/api/v2/blocks/sighs";
    private static final Long EMOTION_ID = 42L;
    private static final Long BLOCK_ID = 7L;
    private static final Instant CREATED_AT = Instant.parse("2026-09-14T02:44:00Z");
    private static final UUID 기기_공개_식별자 = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");

    private final RestTestClient client;

    @MockitoBean
    private EmotionBlockService emotionBlockService;

    @Autowired
    EmotionBlockControllerTest(RestTestClient client) {
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
        when(emotionBlockService.save(EMOTION_ID, 기기_공개_식별자))
                .thenReturn(BlockSaveResult.of(기본_차단_결과(), true));

        // when
        RestTestClient.ResponseSpec result = 차단한다(기본_차단_요청());

        // then
        result.expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .json(기본_차단_응답(), JsonCompareMode.STRICT);
        verify(emotionBlockService).save(EMOTION_ID, 기기_공개_식별자);
    }

    @Test
    void 이미_차단한_한숨을_다시_차단하면_200과_최초_차단을_반환한다() {
        // given
        when(emotionBlockService.save(EMOTION_ID, 기기_공개_식별자))
                .thenReturn(BlockSaveResult.of(기본_차단_결과(), false));

        // when
        RestTestClient.ResponseSpec result = 차단한다(기본_차단_요청());

        // then
        result.expectStatus().isOk()
                .expectBody()
                .json(기본_차단_응답(), JsonCompareMode.STRICT);
        verify(emotionBlockService).save(EMOTION_ID, 기기_공개_식별자);
    }

    @ParameterizedTest
    @MethodSource("올바르지_않은_차단_요청들")
    void 차단_대상_한숨_식별자가_올바르지_않으면_400을_반환한다(String body) {
        // given / when
        RestTestClient.ResponseSpec result = 차단한다(body);

        // then
        오류를_검증한다(result, 400, "COMMON-001", "요청 값이 올바르지 않습니다.");
        verifyNoInteractions(emotionBlockService);
    }

    @Test
    void 차단할_한숨이_없으면_404를_반환한다() {
        // given
        when(emotionBlockService.save(EMOTION_ID, 기기_공개_식별자))
                .thenThrow(new EmotionException(EmotionErrorCode.EMOTION_NOT_FOUND));

        // when
        RestTestClient.ResponseSpec result = 차단한다(기본_차단_요청());

        // then
        오류를_검증한다(result, 404, "SIGH-002", "한숨을 찾을 수 없습니다.");
    }

    @Test
    void 인증_컨텍스트가_없으면_차단자를_비운_채_차단하지_않고_500을_반환한다() {
        // given
        SecurityContextHolder.clearContext();

        // when
        RestTestClient.ResponseSpec result = 차단한다(기본_차단_요청());

        // then
        오류를_검증한다(result, 500, "COMMON-002", "서버 내부 오류가 발생했습니다.");
        verifyNoInteractions(emotionBlockService);
    }

    @Test
    void 차단_목록을_커서와_함께_반환한다() {
        // given
        when(emotionBlockService.findAll(기기_공개_식별자, "opaque-cursor"))
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
        verify(emotionBlockService).findAll(기기_공개_식별자, "opaque-cursor");
    }

    @Test
    void 커서를_생략하면_첫_페이지를_조회한다() {
        // given
        when(emotionBlockService.findAll(기기_공개_식별자, null))
                .thenReturn(BlockListResult.of(List.of(), false, null));

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri(BLOCKS_URI)
                .exchange();

        // then
        result.expectStatus().isOk()
                .expectBody()
                .json("""
                        {"items": [], "hasNext": false, "nextCursor": null}
                        """, JsonCompareMode.STRICT);
        verify(emotionBlockService).findAll(기기_공개_식별자, null);
    }

    @Test
    void 사용할_수_없는_커서는_400을_반환한다() {
        // given
        when(emotionBlockService.findAll(기기_공개_식별자, "invalid-cursor"))
                .thenThrow(new BlockException(BlockErrorCode.BLOCK_INVALID_CURSOR));

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri(BLOCKS_URI + "?cursor=invalid-cursor")
                .exchange();

        // then
        오류를_검증한다(result, 400, "BLOCK-004", "차단 목록 커서를 사용할 수 없습니다.");
    }

    @Test
    void 차단을_해제하면_204와_빈_본문을_반환한다() {
        // given / when
        RestTestClient.ResponseSpec result = client.delete()
                .uri(BLOCKS_URI + "/{sighId}", EMOTION_ID)
                .exchange();

        // then
        result.expectStatus().isNoContent()
                .expectBody().isEmpty();
        verify(emotionBlockService).delete(EMOTION_ID, 기기_공개_식별자);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "4.2"})
    void 해제할_한숨_식별자_형식이_올바르지_않으면_400을_반환한다(String emotionId) {
        // given / when
        RestTestClient.ResponseSpec result = client.delete()
                .uri(BLOCKS_URI + "/{sighId}", emotionId)
                .exchange();

        // then
        오류를_검증한다(result, 400, "COMMON-001", "요청 값이 올바르지 않습니다.");
        verifyNoInteractions(emotionBlockService);
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
        Emotion emotion = 기본_한숨_빌더()
                .nickname("날아가는 고라니")
                .memo("오늘은 조금 지쳤다")
                .build();

        return BlockResult.of(저장된_한숨_차단(BLOCK_ID, CREATED_AT), emotion);
    }

    private String 기본_차단_요청() {
        return """
                {"sighId": 42}
                """;
    }

    private String 기본_차단_응답() {
        return """
                {
                  "sighId": 42,
                  "nickname": "날아가는 고라니",
                  "memo": "오늘은 조금 지쳤다",
                  "createdAt": "2026-09-14T02:44:00Z"
                }
                """;
    }

    private static Stream<String> 올바르지_않은_차단_요청들() {
        return Stream.of(
                "{}",
                """
                        {"sighId": null}
                        """,
                """
                        {"sighId": 0}
                        """,
                """
                        {"sighId": -1}
                        """,
                """
                        {"sighId": "마흔둘"}
                        """
        );
    }

}
