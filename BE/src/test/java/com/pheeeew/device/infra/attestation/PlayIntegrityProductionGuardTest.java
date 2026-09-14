package com.pheeeew.device.infra.attestation;

import static com.pheeeew.device.fixture.PlayIntegrityFixture.우리_패키지명;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PlayIntegrityProductionGuardTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PlayIntegrityConfig.class, PlayIntegrityProductionGuard.class)
            .withPropertyValues("pheeeew.play-integrity.android-package-name=" + 우리_패키지명);

    @Test
    void 운영_프로필에서_검증을_건너뛰도록_켜면_부팅에_실패한다() {
        // given / when / then
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "pheeeew.play-integrity.skip-verification=true"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessage("운영 프로필에서는 무결성 증명 검증을 건너뛸 수 없습니다.");
                });
    }

    @Test
    void 운영_프로필에서_검증을_켜두면_가드가_부팅을_막지_않는다() {
        // given / when / then
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "pheeeew.play-integrity.skip-verification=false"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PlayIntegrityProductionGuard.class);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "dev", "test"})
    void 운영이_아닌_프로필에서는_검증을_건너뛰어도_부팅한다(String profile) {
        // given / when / then
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=" + profile,
                        "pheeeew.play-integrity.skip-verification=true"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(PlayIntegrityProperties.class).skipVerification()).isTrue();
                    assertThat(context).doesNotHaveBean(PlayIntegrityProductionGuard.class);
                });
    }
}
