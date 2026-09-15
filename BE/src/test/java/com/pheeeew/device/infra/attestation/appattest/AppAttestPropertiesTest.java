package com.pheeeew.device.infra.attestation.appattest;

import static com.pheeeew.device.fixture.AppAttestFixture.번들_ID;
import static com.pheeeew.device.fixture.AppAttestFixture.팀_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AppAttestPropertiesTest {

    @Test
    void 설정값이_비어_있어도_설정을_만들_수_있고_검증은_꺼진_것으로_본다() {
        // given / when
        AppAttestProperties properties = new AppAttestProperties(null, null, null, false);

        // then
        assertThat(properties.isConfigured()).isFalse();
        assertThat(properties.environment()).isEqualTo(AppAttestEnvironment.PRODUCTION);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL,com.pheeeew.app",
            "1234567890,NULL",
            "'   ',com.pheeeew.app",
            "1234567890,'   '"
    }, nullValues = "NULL")
    void 설정값이_반쪽이면_검증을_켜지_않는다(String teamId, String bundleId) {
        // given / when
        AppAttestProperties properties = new AppAttestProperties(teamId, bundleId, null, false);

        // then
        assertThat(properties.isConfigured()).isFalse();
    }

    @Test
    void 설정값이_모두_있으면_App_ID_를_만든다() {
        // given / when
        AppAttestProperties properties = new AppAttestProperties(팀_ID, 번들_ID, null, false);

        // then
        assertThat(properties.isConfigured()).isTrue();
        assertThat(properties.appId()).isEqualTo(팀_ID + "." + 번들_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456789", "12345678901", "abcdefghij", "1234-56789", "1234 56789"})
    void Team_ID_형식이_어긋나면_설정을_만들_수_없다(String teamId) {
        // given / when / then
        assertThatThrownBy(() -> new AppAttestProperties(teamId, 번들_ID, null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pheeeew", "com..pheeeew", "com.pheeeew.", "com pheeeew", "com.pheeeew!"})
    void Bundle_ID_형식이_어긋나면_설정을_만들_수_없다(String bundleId) {
        // given / when / then
        assertThatThrownBy(() -> new AppAttestProperties(팀_ID, bundleId, null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL,NULL",
            "1234567890,NULL",
            "NULL,com.pheeeew.app"
    }, nullValues = "NULL")
    void App_ID_가_없으면_강제를_켤_수_없다(String teamId, String bundleId) {
        // given / when / then
        assertThatThrownBy(() -> new AppAttestProperties(teamId, bundleId, null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 설정_파일이_실제_App_ID_를_담고_있다() {
        // given / when / then
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(AppAttestConfig.class)
                .run(context -> {
                    AppAttestProperties properties = context.getBean(AppAttestProperties.class);
                    assertThat(properties.isConfigured()).isTrue();
                    assertThat(properties.appId()).isEqualTo("Z6V4SXKRL6.com.itda.pheeeew");
                    assertThat(properties.requireAttestation()).isFalse();
                });
    }

    @Test
    void 기본_환경은_운영이다() {
        // given / when / then
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(AppAttestConfig.class)
                .run(context -> assertThat(context.getBean(AppAttestProperties.class).environment())
                        .isEqualTo(AppAttestEnvironment.PRODUCTION));
    }

    @Test
    void 개발_프로필은_개발_환경으로_재정의한다() {
        // given / when / then
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(AppAttestConfig.class)
                .withPropertyValues("spring.profiles.active=dev", "DB_URL=jdbc:postgresql://localhost/x",
                        "DB_USERNAME=x", "DB_PASSWORD=x")
                .run(context -> assertThat(context.getBean(AppAttestProperties.class).environment())
                        .isEqualTo(AppAttestEnvironment.DEVELOPMENT));
    }

    @Test
    void 환경_값이_잘못되면_부팅이_실패한다() {
        // given / when / then
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(AppAttestConfig.class)
                .withPropertyValues("pheeeew.app-attest.environment=STAGING")
                .run(context -> assertThat(context).hasFailed());
    }
}
