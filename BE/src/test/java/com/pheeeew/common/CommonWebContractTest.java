package com.pheeeew.common;

import static com.pheeeew.common.exception.CommonErrorCode.INVALID_REQUEST;

import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.common.exception.PheeeewException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Import({GlobalExceptionHandler.class, CommonWebContractTest.TestController.class})
@AutoConfigureRestTestClient
@WebMvcTest(controllers = CommonWebContractTest.TestController.class)
class CommonWebContractTest {

    private final RestTestClient client;

    @Autowired
    CommonWebContractTest(RestTestClient client) {
        this.client = client;
    }

    @Test
    void 필수_요청_본문이_없으면_COMMON_001을_반환한다() {
        // given / when
        RestTestClient.ResponseSpec result = client.post()
                .uri("/test/required-body")
                .exchange();

        // then
        오류를_검증한다(result, 400, "COMMON-001", "요청 값이 올바르지 않습니다.");
    }

    @Test
    void 경로_변수_형식이_올바르지_않으면_COMMON_001을_반환한다() {
        // given / when
        RestTestClient.ResponseSpec result = client.get()
                .uri("/test/path-variable/not-a-number")
                .exchange();

        // then
        오류를_검증한다(result, 400, "COMMON-001", "요청 값이 올바르지 않습니다.");
    }

    @Test
    void 서비스_예외는_정의된_상태와_코드로_반환한다() {
        // given / when
        RestTestClient.ResponseSpec result = client.get()
                .uri("/test/pheeeew-exception")
                .exchange();

        // then
        오류를_검증한다(result, 400, "COMMON-001", "요청 값이 올바르지 않습니다.");
    }

    @Test
    void 존재하지_않는_API는_API_001을_반환한다() {
        // given / when
        RestTestClient.ResponseSpec result = client.get()
                .uri("/test/not-found")
                .exchange();

        // then
        오류를_검증한다(result, 404, "API-001", "요청한 API를 찾을 수 없습니다.");
    }

    @Test
    void 예상하지_못한_예외는_내부_메시지를_노출하지_않는다() {
        // given / when
        RestTestClient.ResponseSpec result = client.get()
                .uri("/test/unexpected")
                .exchange();

        // then
        오류를_검증한다(result, 500, "COMMON-002", "서버 내부 오류가 발생했습니다.");
    }

    private void 오류를_검증한다(
            RestTestClient.ResponseSpec result,
            int status,
            String code,
            String message
    ) {
        result.expectStatus().isEqualTo(status)
                .expectBody()
                .json("""
                        {"code":"%s","message":"%s"}
                        """.formatted(code, message), JsonCompareMode.STRICT);
    }

    @RequestMapping("/test")
    @RestController
    static class TestController {

        @PostMapping("/required-body")
        void requiredBody(
                @RequestBody TestRequest request
        ) {
        }

        @GetMapping("/path-variable/{id}")
        void pathVariable(
                @PathVariable Long id
        ) {
        }

        @GetMapping("/pheeeew-exception")
        void pheeeewException() {
            throw new PheeeewException(INVALID_REQUEST, new IllegalArgumentException());
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("외부에 노출되면 안 되는 메시지");
        }
    }

    record TestRequest(String value) {

        static TestRequest from(String value) {
            return new TestRequest(value);
        }
    }
}
