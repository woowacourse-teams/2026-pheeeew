package com.pheeeew.climb;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.support.SharedJwtTestConfiguration;
import com.pheeeew.support.SharedPostgisTestConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** 프레스 경쟁 웹소켓 통합 테스트와 같은 구성이라 컨텍스트를 함께 쓴다. 기본값(pheeeew.climb.enabled 꺼짐)이다. */
@Import({SharedPostgisTestConfiguration.class, SharedJwtTestConfiguration.class})
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "pheeeew.s3.bucket=pheeeew-test",
        "pheeeew.s3.key-prefix=pheeeew/test/", "pheeeew.s3.region=ap-northeast-2"
})
class ClimbDisabledIntegrationTest {

    @LocalServerPort
    private int port;
    @Autowired
    private ConfigurableApplicationContext context;

    @Test
    void 기능이_꺼져_있으면_등반_빈이_하나도_없다() {
        // when
        List<String> climbBeans = Arrays.stream(context.getBeanDefinitionNames())
                .filter(name -> {
                    Class<?> type = context.getBeanFactory().getType(name);
                    return type != null && type.getName().startsWith("com.pheeeew.climb.");
                })
                .toList();

        // then
        assertThat(climbBeans).isEmpty();
    }

    @Test
    void 기능이_꺼져_있으면_등반_경로는_본체_체인이_막는다() throws Exception {
        // given
        try (HttpClient client = HttpClient.newHttpClient()) {
            // when
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + port + "/api/v1/climb/ranking?board=all&map=v1")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            // then
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        }
    }
}
