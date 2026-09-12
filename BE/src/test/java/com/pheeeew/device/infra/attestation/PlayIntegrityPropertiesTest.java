package com.pheeeew.device.infra.attestation;

import static com.pheeeew.device.fixture.PlayIntegrityFixture.우리_패키지명;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.클라우드_프로젝트_번호;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PlayIntegrityPropertiesTest {

    private static final String 서비스_계정 = "eyJjbGllbnRfZW1haWwiOiAiYUBiLmNvbSJ9";

    @Test
    void 자격증명이_비어_있어도_설정을_만들_수_있다() {
        // given / when
        PlayIntegrityProperties properties = new PlayIntegrityProperties(우리_패키지명, null, null, false, false, null);

        // then
        assertThat(properties.androidPackageName()).isEqualTo(우리_패키지명);
        assertThat(properties.isConfigured()).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {"pheeeew", "com..pheeeew", "1com.pheeeew", "com.pheeeew.", "com pheeeew", "com.phee-eew"})
    void 패키지명_형식이_어긋나면_설정을_만들_수_없다(String androidPackageName) {
        // given / when / then
        assertThatThrownBy(() -> new PlayIntegrityProperties(androidPackageName, null, null, false, false, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "12-34", "1.2", "123456789012345678901"})
    void Cloud_프로젝트_번호_형식이_어긋나면_설정을_만들_수_없다(String cloudProjectNumber) {
        // given / when / then
        assertThatThrownBy(() -> new PlayIntegrityProperties(우리_패키지명, 서비스_계정, cloudProjectNumber, false, false, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL,NULL",
            "eyJ9,NULL",
            "NULL,123456789012",
            "'   ',123456789012",
            "eyJ9,'   '"
    }, nullValues = "NULL")
    void 자격증명이_반쪽이면_강제를_켤_수_없다(String serviceAccountBase64, String cloudProjectNumber) {
        // given / when / then
        assertThatThrownBy(
                () -> new PlayIntegrityProperties(우리_패키지명, serviceAccountBase64, cloudProjectNumber, true, false, null)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 자격증명이_모두_있으면_강제를_켤_수_있다() {
        // given / when
        PlayIntegrityProperties properties =
                new PlayIntegrityProperties(우리_패키지명, 서비스_계정, 클라우드_프로젝트_번호, true, false, null);

        // then
        assertThat(properties.requireAttestation()).isTrue();
        assertThat(properties.isConfigured()).isTrue();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL,123456789012",
            "eyJ9,NULL",
            "NULL,NULL"
    }, nullValues = "NULL")
    void 자격증명이_반쪽이면_검증을_켜지_않는다(String serviceAccountBase64, String cloudProjectNumber) {
        // given / when
        PlayIntegrityProperties properties =
                new PlayIntegrityProperties(우리_패키지명, serviceAccountBase64, cloudProjectNumber, false, false, null);

        // then
        assertThat(properties.isConfigured()).isFalse();
    }

    @Test
    void 무결성_증명을_강제하면서_검증을_건너뛸_수는_없다() {
        // given / when / then
        assertThatThrownBy(
                () -> new PlayIntegrityProperties(우리_패키지명, 서비스_계정, 클라우드_프로젝트_번호, true, true, null)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 일일_호출_예산을_설정하지_않으면_3000_을_쓴다() {
        // given / when
        PlayIntegrityProperties properties = new PlayIntegrityProperties(우리_패키지명, null, null, false, false, null);

        // then
        assertThat(properties.dailyCallBudget()).isEqualTo(3000);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3000, 5000})
    void 일일_호출_예산은_1_이상_5000_이하를_받는다(int dailyCallBudget) {
        // given / when
        PlayIntegrityProperties properties =
                new PlayIntegrityProperties(우리_패키지명, null, null, false, false, dailyCallBudget);

        // then
        assertThat(properties.dailyCallBudget()).isEqualTo(dailyCallBudget);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 5001, 10000})
    void 일일_호출_예산이_범위를_벗어나면_설정을_만들_수_없다(int dailyCallBudget) {
        // given / when / then
        assertThatThrownBy(
                () -> new PlayIntegrityProperties(우리_패키지명, null, null, false, false, dailyCallBudget)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 설정_파일의_기본값은_검증을_건너뛰지_않고_강제하지도_않으며_일일_예산은_3000_이다() {
        // given / when / then
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(PlayIntegrityConfig.class)
                .run(context -> {
                    PlayIntegrityProperties properties = context.getBean(PlayIntegrityProperties.class);
                    assertThat(properties.skipVerification()).isFalse();
                    assertThat(properties.requireAttestation()).isFalse();
                    assertThat(properties.androidPackageName()).isEqualTo(우리_패키지명);
                    assertThat(properties.dailyCallBudget()).isEqualTo(3000);
                });
    }

    @Test
    void toString_은_서비스_계정과_프로젝트_번호를_드러내지_않는다() {
        // given
        PlayIntegrityProperties properties =
                new PlayIntegrityProperties(우리_패키지명, 서비스_계정, 클라우드_프로젝트_번호, false, false, null);

        // when
        String 표현 = properties.toString();

        // then
        assertThat(표현)
                .doesNotContain(서비스_계정)
                .doesNotContain(클라우드_프로젝트_번호)
                .contains(우리_패키지명);
    }
}
