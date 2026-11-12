package com.pheeeew.climb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import com.pheeeew.climb.application.ClimbGuestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

class ClimbProfileConfigurationTest {

    @ParameterizedTest
    @CsvSource({"dev,https://climb-dev.pheeeew.com", "prod,https://climb.pheeeew.com"})
    void 배포_프로필은_등반_빈과_환경별_출처를_설정한다(String profile, String origin) {
        // given
        var runner = contextRunner(profile);

        // when / then
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ClimbGuestService.class);
            var properties = context.getBean(ClimbProperties.class);
            assertThat(properties.enabled()).isTrue();
            assertThat(properties.allowedOrigin()).isEqualTo(origin);
            assertThat(properties.adminKey()).isEmpty();
            assertThat(properties.maps()).containsKey("v1");
            assertThat(properties.maps().get("v1").top()).isEqualTo(663);
        });
    }

    @Test
    void 별도_프로필_설정이_없으면_등반은_꺼져_있다() {
        // given / when / then
        contextRunner("climb-default-test").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(ClimbGuestService.class);
            assertThat(context.getEnvironment().getProperty("pheeeew.climb.enabled", Boolean.class)).isFalse();
        });
    }

    private WebApplicationContextRunner contextRunner(String profile) {
        return new WebApplicationContextRunner()
                .withInitializer(context -> {
                    // 실제 환경 변수·시스템 속성 없이 저장소의 프로필 설정만 확인한다.
                    var sources = context.getEnvironment().getPropertySources();
                    sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                })
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profile)
                .withUserConfiguration(TestSecurityConfiguration.class, ClimbConfiguration.class)
                .withBean(JdbcClient.class, () -> mock(JdbcClient.class, RETURNS_DEEP_STUBS));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestSecurityConfiguration {
    }
}
